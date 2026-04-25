package com.regu.orchestration.stage1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regu.domain.*;
import com.regu.domain.repository.AnalysisRepository;
import com.regu.domain.repository.InterviewAnswerRepository;
import com.regu.domain.repository.InterviewSessionRepository;
import com.regu.orchestration.dto.ClassificationResult;
import com.regu.orchestration.exception.ClassificationException;
import com.regu.orchestration.exception.InterviewStateException;
import com.regu.orchestration.llm.LlmClient;
import com.regu.orchestration.prompt.PromptBuilder;
import com.regu.retrieval.RetrievalOrchestrator;
import com.regu.retrieval.dto.RetrievalQuery;
import com.regu.retrieval.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Objects;

/**
 * Drives the Stage 1 guided risk-classification interview.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Start an interview session linked to an existing {@link Analysis}.</li>
 *   <li>Record each answer and use an LLM to select the next question.</li>
 *   <li>Trigger risk classification once the LLM decides enough information
 *       has been gathered (or when a terminal question is reached).</li>
 * </ul>
 */
@Service
public class Stage1InterviewService {

    private static final Logger log = LoggerFactory.getLogger(Stage1InterviewService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // System prompts -------------------------------------------------------

    private static final String NEXT_QUESTION_SYSTEM = """
            You are a legal compliance interview navigator for the EU AI Act.
            Given the questions already answered and the remaining questions available,
            select the single most important next question to ask.

            Rules:
            - Check each remaining question's preconditions against already-answered questions
            - Only select questions whose preconditions are satisfied
            - Prioritize questions that will most efficiently narrow the risk classification
            - If you have enough information to classify the system, respond with
              {"next_question": null, "ready_to_classify": true, "reason": "..."}
            - Otherwise respond with {"next_question": "<question_key>", "ready_to_classify": false, "reason": "..."}
            """;

    private static final String CLASSIFICATION_SYSTEM = """
            You are a legal expert classifying an AI system under the EU AI Act
            (Regulation 2024/1689). Based on the interview answers and retrieved
            legal context below, determine the risk classification.

            RULES:
            1. You MUST cite specific articles and annex points for your classification
            2. risk_category must be exactly one of: unacceptable, high, limited, minimal
            3. confidence must be: high (clear case), medium (probable but some ambiguity),
               low (significant uncertainty), review_recommended (edge case, needs human review)
            4. applicableArticles must list ALL articles that impose obligations on this system
            5. reasoning must reference the legal text, not just state a conclusion
            6. citedChunkIds must list the actual chunk database IDs (as strings) from the context provided
            """;

    // Dependencies ---------------------------------------------------------

    private final InterviewSessionRepository  sessionRepo;
    private final InterviewAnswerRepository   answerRepo;
    private final AnalysisRepository          analysisRepo;
    private final RetrievalOrchestrator       retrieval;
    private final LlmClient                   llm;
    private final PromptBuilder               promptBuilder;
    private final NamedParameterJdbcTemplate  jdbc;
    private final Stage1RuleEngine            ruleEngine;
    private final int                         maxRetries;
    private final int                         classificationMaxTokens;

    public Stage1InterviewService(
            InterviewSessionRepository sessionRepo,
            InterviewAnswerRepository answerRepo,
            AnalysisRepository analysisRepo,
            RetrievalOrchestrator retrieval,
            LlmClient llm,
            PromptBuilder promptBuilder,
            NamedParameterJdbcTemplate jdbc,
            Stage1RuleEngine ruleEngine,
            @Value("${regu.llm.max-retries:2}")               int maxRetries,
            @Value("${regu.llm.classification-max-tokens:2048}") int classificationMaxTokens) {
        this.sessionRepo              = sessionRepo;
        this.answerRepo               = answerRepo;
        this.analysisRepo             = analysisRepo;
        this.retrieval                = retrieval;
        this.llm                      = llm;
        this.promptBuilder            = promptBuilder;
        this.jdbc                     = jdbc;
        this.ruleEngine               = ruleEngine;
        this.maxRetries               = maxRetries;
        this.classificationMaxTokens  = classificationMaxTokens;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Creates a new {@link InterviewSession} linked to {@code analysis} and
     * returns the first question (always E1 — entity type selection).
     */
    @Transactional
    public InterviewSessionWithQuestion startInterview(Analysis analysis) {
        InterviewSession session = new InterviewSession(analysis);
        session.setCurrentQuestionKey("E1");
        sessionRepo.save(session);

        InterviewQuestionDto firstQuestion = loadQuestion("E1");
        log.info("Started interview session {} for analysis {}", session.getId(), analysis.getId());
        return new InterviewSessionWithQuestion(session, firstQuestion);
    }

    /**
     * Records an answer and routes to the next step.
     *
     * <p>Routing strategy:
     * <ul>
     *   <li>Predefined option selected → deterministic next_key routing, no LLM.</li>
     *   <li>Option is terminal (or next_key is null) → rule-based classification, no LLM.</li>
     *   <li>No option matched (free-text input) → LLM selects next question or classifies.</li>
     * </ul>
     *
     * @param sessionId   the active session
     * @param questionKey the question being answered
     * @param answerText  the user's raw answer text
     * @return response indicating next question or classification complete
     */
    @Transactional
    public NextQuestionResponse answerAndGetNext(UUID sessionId, String questionKey,
                                                  String answerText) {
        InterviewSession session = requireActiveSession(sessionId);

        // Persist the answer
        InterviewAnswer answer = new InterviewAnswer(session, questionKey, answerText);
        answerRepo.save(answer);
        log.debug("Recorded answer for session {} question {}", sessionId, questionKey);

        // Load all answers for this session (includes the one just saved)
        List<InterviewAnswer> allAnswers = answerRepo.findAllBySession_IdOrderByAnsweredAtAsc(session.getId());

        // ── Attempt deterministic routing from the matched answer option ───
        InterviewQuestionDto answeredQuestion = loadQuestion(questionKey);
        AnswerOptionRouting routing = resolveAnswerRouting(answeredQuestion, answerText);

        if (routing != null) {
            // Predefined option matched — never call LLM for routing or classification

            if (routing.isTerminal || routing.nextKey == null) {
                // Terminal option: classify via rule engine (no LLM)
                log.info("Session {}: terminal option reached — rule-based classification (flags accumulated)",
                        sessionId);
                Set<String> flags = ruleEngine.accumulateFlags(allAnswers, this::loadQuestion);
                ClassificationResult result = ruleEngine.classifyByRules(flags);
                persistClassificationResult(session, result);
                return NextQuestionResponse.classified(result);
            }

            // Non-terminal option: follow next_key deterministically
            try {
                InterviewQuestionDto nextQuestion = loadQuestion(routing.nextKey);
                session.setCurrentQuestionKey(nextQuestion.questionKey());
                sessionRepo.save(session);
                log.info("Session {}: deterministic routing {} → {}",
                        sessionId, questionKey, routing.nextKey);
                return NextQuestionResponse.ongoing(nextQuestion);
            } catch (InterviewStateException e) {
                log.warn("Session {}: next_key '{}' not found in DB, falling back to LLM",
                        sessionId, routing.nextKey);
                // fall through to LLM path
            }
        }

        // ── LLM path: free-text answer or unrecognised option value ───────
        log.info("Session {}: no option match for question {} — using LLM", sessionId, questionKey);

        // Question-level terminal check (safety net for non-option questions)
        if (answeredQuestion.isTerminal()) {
            return completeClassification(session, allAnswers);
        }

        Set<String> answeredKeys = allAnswers.stream()
                .map(InterviewAnswer::getQuestionKey)
                .collect(Collectors.toSet());
        List<InterviewQuestionDto> remaining = loadAllStage1Questions().stream()
                .filter(q -> !answeredKeys.contains(q.questionKey()))
                .toList();

        if (remaining.isEmpty()) {
            return completeClassification(session, allAnswers);
        }

        String userPrompt = promptBuilder.buildNextQuestionPrompt(allAnswers, remaining);
        NextQuestionDecision decision = llm.callWithSchema(
                NEXT_QUESTION_SYSTEM, userPrompt, NextQuestionDecision.class);

        log.info("Session {}: LLM next-question decision — readyToClassify={}, nextKey={}",
                sessionId, decision.readyToClassify(), decision.nextQuestion());

        if (decision.readyToClassify() || decision.nextQuestion() == null) {
            return completeClassification(session, allAnswers);
        }

        InterviewQuestionDto nextQuestion = loadQuestion(decision.nextQuestion());
        session.setCurrentQuestionKey(nextQuestion.questionKey());
        sessionRepo.save(session);

        return NextQuestionResponse.ongoing(nextQuestion);
    }

    // ── Answer option routing helper ────────────────────────────────────

    private record AnswerOptionRouting(String nextKey, boolean isTerminal, List<String> flags) {}

    /**
     * Handles single or comma-separated multi-select answers.
     *
     * <p>Rules:
     * <ul>
     *   <li>Comma-separated parts are each matched against option values.</li>
     *   <li>If "none_of_the_above" appears alongside other selections, it is
     *       ignored — the other selections take precedence.</li>
     *   <li>A terminal option wins over non-terminal ones.</li>
     *   <li>nextKey: first non-null key across all matched options (they should
     *       all agree for well-formed multi_select questions).</li>
     *   <li>flags: union of all matched option flags.</li>
     * </ul>
     */
    private AnswerOptionRouting resolveAnswerRouting(InterviewQuestionDto question, String answerText) {
        if (question.answersJson() == null || answerText == null) return null;
        try {
            var node = MAPPER.readTree(question.answersJson());
            var options = node.get("options");
            if (options == null || !options.isArray()) return null;

            // Split for multi-select support
            List<String> parts = Arrays.stream(answerText.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            // If multiple parts contain "none_of_the_above" alongside real answers, drop it
            List<String> effectiveParts = parts.size() > 1
                    ? parts.stream().filter(p -> !p.equalsIgnoreCase("none_of_the_above")).toList()
                    : parts;
            if (effectiveParts.isEmpty()) effectiveParts = parts;

            List<AnswerOptionRouting> matched = new ArrayList<>();
            for (String part : effectiveParts) {
                AnswerOptionRouting r = matchSingleOption(options, part);
                if (r != null) matched.add(r);
            }

            if (matched.isEmpty()) {
                log.debug("No option match for answer '{}' on question {}", answerText, question.questionKey());
                return null;
            }

            // Collect union of all flags
            List<String> allFlags = matched.stream()
                    .flatMap(r -> r.flags().stream())
                    .distinct().toList();

            // Terminal option wins
            for (AnswerOptionRouting r : matched) {
                if (r.isTerminal()) {
                    log.debug("Multi-select {}: terminal option wins, flags={}", question.questionKey(), allFlags);
                    return new AnswerOptionRouting(r.nextKey(), true, allFlags);
                }
            }

            // All non-terminal — use first non-null nextKey
            String nextKey = matched.stream()
                    .map(AnswerOptionRouting::nextKey)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);

            if (matched.size() > 1) {
                log.debug("Multi-select {}: {} options matched, nextKey={}, flags={}",
                        question.questionKey(), matched.size(), nextKey, allFlags);
            }
            return new AnswerOptionRouting(nextKey, false, allFlags);

        } catch (Exception e) {
            log.warn("Failed to parse answers JSON for question {}: {}", question.questionKey(), e.getMessage());
            return null;
        }
    }

    /** Matches a single answer string against the options array of a question. */
    private AnswerOptionRouting matchSingleOption(JsonNode options, String singleAnswer) {
        String normalised = singleAnswer.trim().toLowerCase();
        for (var option : options) {
            String value = option.has("value") ? option.get("value").asText() : "";
            String label = option.has("label") ? option.get("label").asText() : "";

            if (normalised.equals(value.toLowerCase())
                    || normalised.equals(label.toLowerCase())
                    || label.toLowerCase().startsWith(normalised)
                    || normalised.startsWith(value.toLowerCase())) {

                String nextKey = option.has("next_key") && !option.get("next_key").isNull()
                        ? option.get("next_key").asText() : null;
                boolean terminal = option.has("is_terminal") && option.get("is_terminal").asBoolean();

                List<String> flags = new ArrayList<>();
                var flagsNode = option.get("flags");
                if (flagsNode != null && flagsNode.isArray()) {
                    for (var f : flagsNode) flags.add(f.asText());
                }
                return new AnswerOptionRouting(nextKey, terminal, flags);
            }
        }
        return null;
    }

    /**
     * Performs the full risk classification for a session using retrieved context.
     * Updates both the {@link InterviewSession} and the linked {@link Analysis}.
     *
     * @param sessionId the session whose answers to use
     * @return the classification result
     * @throws ClassificationException if the LLM fails after retries
     */
    @Transactional
    public ClassificationResult classifyRisk(UUID sessionId) {
        InterviewSession session = requireSession(sessionId);
        List<InterviewAnswer> answers = answerRepo.findAllBySession_IdOrderByAnsweredAtAsc(session.getId());
        return runClassification(session, answers);
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private NextQuestionResponse completeClassification(InterviewSession session,
                                                         List<InterviewAnswer> answers) {
        ClassificationResult result = runClassification(session, answers);
        return NextQuestionResponse.classified(result);
    }

    private ClassificationResult runClassification(InterviewSession session,
                                                    List<InterviewAnswer> answers) {
        log.info("Running risk classification for session {}", session.getId());

        // Build query text from interview answers
        String queryText = answers.stream()
                .map(a -> a.getQuestionKey() + ": " + a.getRawInput())
                .collect(Collectors.joining(". "));
        if (queryText.isBlank()) queryText = "AI system compliance assessment";

        // Retrieve context (parallel for legal + use_case, deterministic for rules)
        List<RetrievedChunk> legalChunks = retrieval
                .retrieveFromTable("legal", RetrievalQuery.of(queryText, 10))
                .chunks();

        List<RetrievedChunk> ruleChunks = resolveRuleChunks(answers);

        List<RetrievedChunk> useCaseChunks = retrieval
                .retrieveFromTable("use_case", RetrievalQuery.of(queryText, 5))
                .chunks();

        // Build and execute classification prompt
        String userPrompt = promptBuilder.buildClassificationPrompt(
                answers, legalChunks, ruleChunks, useCaseChunks);

        ClassificationResult result = null;
        RuntimeException lastError = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                result = llm.callWithSchema(
                        CLASSIFICATION_SYSTEM, userPrompt,
                        ClassificationResult.class, classificationMaxTokens);

                // Cross-check: compare applicableArticles against use_case mappedArticles
                if (crossCheckFails(result, useCaseChunks) && attempt < maxRetries) {
                    log.warn("Cross-check mismatch on attempt {} for session {}. Retrying.",
                            attempt + 1, session.getId());
                    userPrompt = userPrompt + buildCrossCheckRetryAppendix(result, useCaseChunks);
                    continue;
                }
                break;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("Classification attempt {} failed for session {}: {}",
                        attempt + 1, session.getId(), e.getMessage());
            }
        }

        if (result == null) {
            throw new ClassificationException(
                    "Risk classification failed after " + maxRetries + " retries",
                    lastError);
        }

        // Persist result to session and analysis
        persistClassificationResult(session, result);
        return result;
    }

    private void persistClassificationResult(InterviewSession session, ClassificationResult result) {
        // Update InterviewSession
        try {
            session.setRiskClassification(
                    InterviewSession.RiskClassification.valueOf(result.riskCategory()));
        } catch (IllegalArgumentException e) {
            session.setRiskClassification(InterviewSession.RiskClassification.out_of_scope);
        }
        session.setStatus(InterviewSession.Status.stage1_complete);
        sessionRepo.save(session);

        // Update linked Analysis
        Analysis analysis = session.getAnalysis();
        if (analysis != null) {
            try {
                analysis.setRiskCategory(
                        Analysis.RiskCategory.valueOf(result.riskCategory()));
            } catch (IllegalArgumentException ignored) { }
            try {
                analysis.setConfidence(
                        Analysis.Confidence.valueOf(result.confidence()));
            } catch (IllegalArgumentException ignored) {
                analysis.setConfidence(Analysis.Confidence.review_recommended);
            }
            analysis.setPrimaryLegalBasis(result.primaryLegalBasis());
            analysis.setStatus(Analysis.Status.processing);
            analysisRepo.save(analysis);
        }

        log.info("Session {} classified as {} (confidence={})",
                session.getId(), result.riskCategory(), result.confidence());
    }

    // ── Cross-check helpers ────────────────────────────────────────────────

    private boolean crossCheckFails(ClassificationResult result,
                                     List<RetrievedChunk> useCaseChunks) {
        if (result.applicableArticles() == null || result.applicableArticles().isEmpty()) {
            return false;
        }
        Set<Integer> resultArticles = new HashSet<>(result.applicableArticles());
        Set<Integer> useCaseArticles = extractUseCaseMappedArticles(useCaseChunks);
        if (useCaseArticles.isEmpty()) return false;

        long matches = useCaseArticles.stream().filter(resultArticles::contains).count();
        double overlapRatio = (double) matches / useCaseArticles.size();
        return overlapRatio < 0.5;
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> extractUseCaseMappedArticles(List<RetrievedChunk> useCaseChunks) {
        Set<Integer> articles = new HashSet<>();
        for (RetrievedChunk chunk : useCaseChunks) {
            Object mapped = chunk.metadata().get("mapped_articles");
            if (mapped instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Number n) articles.add(n.intValue());
                }
            }
        }
        return articles;
    }

    private String buildCrossCheckRetryAppendix(ClassificationResult result,
                                                  List<RetrievedChunk> useCaseChunks) {
        Set<Integer> useCaseArticles = extractUseCaseMappedArticles(useCaseChunks);
        return "\n\n[CROSS-CHECK NOTE: The applicable articles you identified " +
               result.applicableArticles() +
               " have less than 50% overlap with the articles from similar use cases " +
               useCaseArticles +
               ". Please review your classification and re-check which articles truly apply.]";
    }

    // ── DB helpers ─────────────────────────────────────────────────────────

    private InterviewQuestionDto loadQuestion(String questionKey) {
        String sql = """
                SELECT question_key, display_text, hint_text, answers::text,
                       preconditions::text, stage, section,
                       linked_rule_chunk, is_terminal
                FROM interview_questions
                WHERE question_key = :key
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("key", questionKey));
        if (rows.isEmpty()) {
            throw new InterviewStateException("Question not found: " + questionKey);
        }
        return mapRow(rows.get(0));
    }

    private List<InterviewQuestionDto> loadAllStage1Questions() {
        String sql = """
                SELECT question_key, display_text, hint_text, answers::text,
                       preconditions::text, stage, section,
                       linked_rule_chunk, is_terminal
                FROM interview_questions
                WHERE stage = 1
                ORDER BY question_key
                """;
        return jdbc.queryForList(sql, Map.of()).stream()
                .map(this::mapRow)
                .toList();
    }

    private InterviewQuestionDto mapRow(Map<String, Object> row) {
        Number linkedRuleChunk = (Number) row.get("linked_rule_chunk");
        return new InterviewQuestionDto(
                (String)  row.get("question_key"),
                (String)  row.get("display_text"),
                (String)  row.get("hint_text"),
                (String)  row.get("answers"),
                (String)  row.get("preconditions"),
                row.get("stage") != null ? ((Number) row.get("stage")).shortValue() : (short) 1,
                (String)  row.get("section"),
                0,
                linkedRuleChunk != null ? linkedRuleChunk.longValue() : null,
                Boolean.TRUE.equals(row.get("is_terminal"))
        );
    }

    private List<RetrievedChunk> resolveRuleChunks(List<InterviewAnswer> answers) {
        if (answers.isEmpty()) return List.of();
        Set<String> answeredKeys = answers.stream()
                .map(InterviewAnswer::getQuestionKey)
                .collect(Collectors.toSet());
        String sql = """
                SELECT drc.rule_id
                FROM interview_questions iq
                JOIN decision_rule_chunks drc ON iq.linked_rule_chunk = drc.id
                WHERE iq.question_key IN (:keys)
                  AND iq.linked_rule_chunk IS NOT NULL
                """;
        List<String> ruleIds = jdbc.queryForList(sql, Map.of("keys", answeredKeys), String.class);
        return ruleIds.isEmpty() ? List.of() : retrieval.retrieveByRuleIds(ruleIds);
    }

    // ── Session guards ─────────────────────────────────────────────────────

    private InterviewSession requireActiveSession(UUID sessionId) {
        InterviewSession s = requireSession(sessionId);
        if (s.getStatus() != InterviewSession.Status.active) {
            throw new InterviewStateException(
                    "Session " + sessionId + " is not active (status=" + s.getStatus() + ")");
        }
        return s;
    }

    private InterviewSession requireSession(UUID sessionId) {
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new InterviewStateException(
                        "Interview session not found: " + sessionId));
    }

    // ── Internal DTOs ──────────────────────────────────────────────────────

    /** LLM response for next-question selection. */
    private record NextQuestionDecision(
            @JsonProperty("next_question")    String  nextQuestion,
            @JsonProperty("ready_to_classify") boolean readyToClassify,
            String reason
    ) {}

    /** Wraps the session entity and its first question for the caller. */
    public record InterviewSessionWithQuestion(
            InterviewSession      session,
            InterviewQuestionDto  question
    ) {}
}

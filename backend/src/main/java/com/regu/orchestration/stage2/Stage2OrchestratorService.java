package com.regu.orchestration.stage2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regu.domain.AnnexIvRequirement;
import com.regu.domain.AnnexIvSection;
import com.regu.domain.InterviewSession;
import com.regu.domain.Stage2Submission;
import com.regu.domain.repository.*;
import com.regu.orchestration.dto.GapAnalysisItem;
import com.regu.orchestration.dto.GapAnalysisResult;
import com.regu.orchestration.dto.NextRequirementResponse;
import com.regu.orchestration.dto.Stage2Status;
import com.regu.orchestration.exception.InterviewStateException;
import com.regu.orchestration.exception.Stage2NotApplicableException;
import com.regu.orchestration.llm.LlmClient;
import com.regu.orchestration.prompt.PromptBuilder;
import com.regu.retrieval.RetrievalOrchestrator;
import com.regu.retrieval.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Drives the Stage 2 Annex IV technical-documentation gap assessment.
 *
 * <p>Stage 2 is only reachable for sessions classified as high-risk.
 * Each of the 9 Annex IV sections can be submitted either as a document
 * upload (for automated extraction) or via guided Q&amp;A.
 */
@Service
public class Stage2OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(Stage2OrchestratorService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final TypeReference<List<GapAnalysisItem>> GAP_ITEM_LIST_TYPE =
            new TypeReference<>() {};

    // System prompts -------------------------------------------------------

    private static final String DOCUMENT_GAP_SYSTEM = """
            You are analyzing a technical document against EU AI Act Annex IV
            requirements (Article 11). For each requirement listed, determine
            whether the uploaded document adequately addresses it.

            RULES:
            1. Set found=true ONLY if the document contains substantive information
               addressing the extraction_target — vague or boilerplate statements do NOT qualify
            2. severity: "critical" if the requirement is a core Article 9-15 obligation,
               "major" if it is a documentation completeness issue, "minor" if it is a
               formatting or detail issue
            3. recommendation must be specific and actionable, not generic
            4. extractedValue should quote or paraphrase the relevant document passage (null if not found)
            5. gapDescription should explain exactly what is missing (null if found)

            Respond with a JSON array of GapAnalysisItem objects. Each object must have:
            requirementId, entityName, found, extractedValue, gapDescription, severity, recommendation
            """;

    private static final String QA_GAP_SYSTEM = """
            You are evaluating user-provided answers about an AI system's technical
            documentation against EU AI Act Annex IV requirements (Article 11).

            For each requirement, assess whether the provided answer adequately addresses it.
            A missing or empty answer automatically counts as a gap (found=false).

            RULES:
            1. found=true only if the answer provides substantive, specific information
            2. severity and recommendation follow the same rules as document analysis
            3. extractedValue should summarize the user's answer (null if not provided)
            4. gapDescription should note what is still missing (null if found=true)

            Respond with a JSON array of GapAnalysisItem objects with:
            requirementId, entityName, found, extractedValue, gapDescription, severity, recommendation
            """;

    // Dependencies ---------------------------------------------------------

    private final InterviewSessionRepository    sessionRepo;
    private final AnnexIvSectionRepository      sectionRepo;
    private final AnnexIvRequirementRepository  requirementRepo;
    private final Stage2SubmissionRepository    submissionRepo;
    private final RetrievalOrchestrator         retrieval;
    private final LlmClient                     llm;
    private final PromptBuilder                 promptBuilder;

    public Stage2OrchestratorService(
            InterviewSessionRepository sessionRepo,
            AnnexIvSectionRepository sectionRepo,
            AnnexIvRequirementRepository requirementRepo,
            Stage2SubmissionRepository submissionRepo,
            RetrievalOrchestrator retrieval,
            LlmClient llm,
            PromptBuilder promptBuilder) {
        this.sessionRepo     = sessionRepo;
        this.sectionRepo     = sectionRepo;
        this.requirementRepo = requirementRepo;
        this.submissionRepo  = submissionRepo;
        this.retrieval       = retrieval;
        this.llm             = llm;
        this.promptBuilder   = promptBuilder;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Validates the session is high-risk and returns the status of all 9 sections.
     */
    @Transactional
    public Stage2Status startStage2(UUID sessionId) {
        InterviewSession session = requireHighRiskSession(sessionId);
        session.setStatus(InterviewSession.Status.stage2_started);
        session.setStage((short) 2);
        sessionRepo.save(session);

        List<AnnexIvSection> sections = sectionRepo.findAll();
        Set<Short> completedSections = submissionRepo
                .findAllBySessionIdOrderBySectionNumberAsc(sessionId)
                .stream()
                .map(Stage2Submission::getSectionNumber)
                .collect(Collectors.toSet());

        List<Stage2Status.SectionStatus> statuses = sections.stream()
                .sorted(Comparator.comparing(AnnexIvSection::getSectionNumber))
                .map(s -> new Stage2Status.SectionStatus(
                        s.getSectionNumber(),
                        s.getDisplayTitle(),
                        completedSections.contains(s.getSectionNumber())))
                .toList();

        int completed = (int) statuses.stream().filter(Stage2Status.SectionStatus::completed).count();
        log.info("Stage 2 started for session {} — {}/{} sections complete", sessionId,
                completed, statuses.size());

        return new Stage2Status(sessionId, statuses, statuses.size(), completed);
    }

    /**
     * Analyses an uploaded document against Annex IV requirements for one section.
     */
    @Transactional
    public GapAnalysisResult processDocumentUpload(UUID sessionId, int sectionNumber,
                                                    String documentText) {
        requireHighRiskSession(sessionId);
        AnnexIvSection section = requireSection(sectionNumber);
        List<AnnexIvRequirement> requirements =
                requirementRepo.findAllBySectionNumberOrderByDisplayOrder(
                        section.getSectionNumber());

        List<RetrievedChunk> legalChunks = retrieveLegalForSection(section);

        String userPrompt = promptBuilder.buildGapAnalysisPrompt(
                sectionNumber, section.getDisplayTitle(), requirements,
                legalChunks, documentText);

        List<GapAnalysisItem> items = llm.callWithTypeRef(
                DOCUMENT_GAP_SYSTEM, userPrompt, GAP_ITEM_LIST_TYPE, 4096);

        GapAnalysisResult result = GapAnalysisResult.of(
                sectionNumber, section.getDisplayTitle(), items);

        // Persist submission
        Stage2Submission submission = findOrCreateSubmission(
                sessionId, section.getSectionNumber(), "upload");
        submission.setFileName("uploaded_document");
        submission.setFileContent(documentText);
        submission.setGapsFound(serializeQuietly(items));
        submission.setAnalysisStatus("complete");
        submission.setAnalysedAt(Instant.now());
        submissionRepo.save(submission);

        log.info("Section {} document analysis complete for session {}: {}/{} met",
                sectionNumber, sessionId, result.metRequirements(), result.totalRequirements());
        return result;
    }

    /**
     * Evaluates user-provided Q&amp;A answers against Annex IV requirements for one section.
     * Missing answers (requirements not in the map) are treated as gaps automatically.
     */
    @Transactional
    public GapAnalysisResult processQAAnswers(UUID sessionId, int sectionNumber,
                                               Map<String, String> answers) {
        requireHighRiskSession(sessionId);
        AnnexIvSection section = requireSection(sectionNumber);
        List<AnnexIvRequirement> requirements =
                requirementRepo.findAllBySectionNumberOrderByDisplayOrder(
                        section.getSectionNumber());

        List<RetrievedChunk> legalChunks = retrieveLegalForSection(section);

        // Build Q&A-style prompt embedding the answers map
        String userPrompt = buildQAPrompt(
                sectionNumber, section.getDisplayTitle(), requirements,
                legalChunks, answers);

        List<GapAnalysisItem> items = llm.callWithTypeRef(
                QA_GAP_SYSTEM, userPrompt, GAP_ITEM_LIST_TYPE, 4096);

        GapAnalysisResult result = GapAnalysisResult.of(
                sectionNumber, section.getDisplayTitle(), items);

        // Build transcript JSON: {"answers": {"req_id": "answer text", ...}}
        Map<String, Object> transcript = Map.of("answers", answers);
        Stage2Submission submission = findOrCreateSubmission(
                sessionId, section.getSectionNumber(), "qa");
        submission.setQaTranscript(serializeQuietly(transcript));
        submission.setGapsFound(serializeQuietly(items));
        submission.setAnalysisStatus("complete");
        submission.setAnalysedAt(Instant.now());
        submissionRepo.save(submission);

        log.info("Section {} Q&A analysis complete for session {}: {}/{} met",
                sectionNumber, sessionId, result.metRequirements(), result.totalRequirements());
        return result;
    }

    /**
     * Returns the next unanswered Annex IV requirement for Q&amp;A mode.
     * Deterministic — no LLM call.
     */
    @Transactional(readOnly = true)
    public NextRequirementResponse getNextRequirement(UUID sessionId, int sectionNumber) {
        requireHighRiskSession(sessionId);
        AnnexIvSection section = requireSection(sectionNumber);
        List<AnnexIvRequirement> requirements =
                requirementRepo.findAllBySectionNumberOrderByDisplayOrder(
                        section.getSectionNumber());

        // Find already-answered requirement IDs from qa_transcript
        Set<String> answered = loadAnsweredRequirementIds(sessionId, section.getSectionNumber());

        List<AnnexIvRequirement> remaining = requirements.stream()
                .filter(r -> !answered.contains(r.getRequirementId()))
                .toList();

        if (remaining.isEmpty()) {
            throw new InterviewStateException(
                    "All requirements for section " + sectionNumber + " are already answered.");
        }

        AnnexIvRequirement next = remaining.get(0);
        return new NextRequirementResponse(
                next.getRequirementId(),
                next.getEntityName(),
                next.getFallbackPrompt(),
                sectionNumber,
                remaining.size() == 1
        );
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private InterviewSession requireHighRiskSession(UUID sessionId) {
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new InterviewStateException(
                        "Session not found: " + sessionId));
        if (session.getRiskClassification() != InterviewSession.RiskClassification.high) {
            throw new Stage2NotApplicableException(
                    "Stage 2 is only applicable to high-risk sessions. " +
                    "Session " + sessionId + " has classification: " +
                    session.getRiskClassification());
        }
        return session;
    }

    private AnnexIvSection requireSection(int sectionNumber) {
        return sectionRepo.findBySectionNumber((short) sectionNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Annex IV section not found: " + sectionNumber));
    }

    private List<RetrievedChunk> retrieveLegalForSection(AnnexIvSection section) {
        int[] articles = section.getLinkedArticles();
        if (articles == null || articles.length == 0) return List.of();
        return retrieval.retrieveLegalWithArticles(articles).chunks();
    }

    private Stage2Submission findOrCreateSubmission(UUID sessionId, Short sectionNumber,
                                                     String mode) {
        return submissionRepo.findBySessionIdAndSectionNumber(sessionId, sectionNumber)
                .orElseGet(() -> new Stage2Submission(sessionId, sectionNumber, mode));
    }

    private Set<String> loadAnsweredRequirementIds(UUID sessionId, Short sectionNumber) {
        return submissionRepo.findBySessionIdAndSectionNumber(sessionId, sectionNumber)
                .map(sub -> {
                    String transcript = sub.getQaTranscript();
                    if (transcript == null || transcript.isBlank()) return Set.<String>of();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = MAPPER.readValue(transcript, Map.class);
                        Object answersObj = parsed.get("answers");
                        if (answersObj instanceof Map<?, ?> answersMap) {
                            return answersMap.keySet().stream()
                                    .map(Object::toString)
                                    .collect(Collectors.toSet());
                        }
                    } catch (Exception e) {
                        log.warn("Could not parse qa_transcript for section {}: {}", sectionNumber, e.getMessage());
                    }
                    return Set.<String>of();
                })
                .orElse(Set.of());
    }

    private String buildQAPrompt(int sectionNumber, String sectionTitle,
                                  List<AnnexIvRequirement> requirements,
                                  List<RetrievedChunk> legalChunks,
                                  Map<String, String> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Annex IV Section ").append(sectionNumber)
          .append(": ").append(sectionTitle).append("\n\n");

        sb.append("## Requirements with user answers\n\n");
        for (AnnexIvRequirement req : requirements) {
            String answer = answers.getOrDefault(req.getRequirementId(), null);
            sb.append("requirementId: ").append(req.getRequirementId()).append("\n");
            sb.append("entityName: ").append(req.getEntityName()).append("\n");
            sb.append("extractionTarget: ").append(req.getExtractionTarget()).append("\n");
            sb.append("userAnswer: ").append(answer != null ? answer : "(not provided)").append("\n\n");
        }

        sb.append("## Legal context (relevant EU AI Act articles)\n\n");
        if (legalChunks.isEmpty()) {
            sb.append("(none retrieved)\n");
        } else {
            for (RetrievedChunk chunk : legalChunks) {
                String body = chunk.contentWithContext() != null
                        ? chunk.contentWithContext() : chunk.content();
                sb.append(body).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String serializeQuietly(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Could not serialize object to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}

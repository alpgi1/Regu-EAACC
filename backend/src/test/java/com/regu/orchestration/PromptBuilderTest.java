package com.regu.orchestration;

import com.regu.domain.InterviewAnswer;
import com.regu.domain.InterviewSession;
import com.regu.orchestration.dto.ClassificationResult;
import com.regu.orchestration.dto.GapAnalysisResult;
import com.regu.orchestration.llm.ClaudeSonnetClient;
import com.regu.orchestration.prompt.PromptBuilder;
import com.regu.orchestration.stage1.InterviewQuestionDto;
import com.regu.retrieval.dto.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PromptBuilder}. No LLM calls, no database.
 * Verifies that prompts are formatted correctly and contain the required markers.
 */
class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    // ── buildClassificationPrompt ──────────────────────────────────────────

    @Test
    void classificationPrompt_containsChunkIdMarker() {
        RetrievedChunk legalChunk = new RetrievedChunk(
                42L, "legal_chunks",
                "Training data shall be subject to data governance practices.",
                "[Article 10, Paragraph 1]\nTraining data shall be subject to data governance practices.",
                0.95,
                Map.of("article_number", 10, "paragraph_number", 1, "article_title", "Data and data governance")
        );

        InterviewAnswer answer = buildAnswer("HR1", "yes, we use biometric data");

        String prompt = promptBuilder.buildClassificationPrompt(
                List.of(answer),
                List.of(legalChunk),
                List.of(),
                List.of()
        );

        assertThat(prompt).contains("[CHUNK ID: 42 | Source: legal_chunks |");
        assertThat(prompt).contains("Article 10");
        assertThat(prompt).contains("HR1");
        assertThat(prompt).contains("yes, we use biometric data");
    }

    @Test
    void classificationPrompt_emptyChunks_showsNoneRetrieved() {
        String prompt = promptBuilder.buildClassificationPrompt(
                List.of(), List.of(), List.of(), List.of());

        assertThat(prompt).contains("(none retrieved)");
    }

    @Test
    void classificationPrompt_multipleChunks_allIncluded() {
        RetrievedChunk chunk1 = new RetrievedChunk(10L, "legal_chunks", "content A", null, 0.9,
                Map.of("article_number", 9));
        RetrievedChunk chunk2 = new RetrievedChunk(20L, "legal_chunks", "content B", null, 0.8,
                Map.of("article_number", 10));

        String prompt = promptBuilder.buildClassificationPrompt(
                List.of(), List.of(chunk1, chunk2), List.of(), List.of());

        assertThat(prompt).contains("[CHUNK ID: 10 | Source: legal_chunks |");
        assertThat(prompt).contains("[CHUNK ID: 20 | Source: legal_chunks |");
    }

    // ── buildGapAnalysisPrompt ─────────────────────────────────────────────

    @Test
    void gapAnalysisPrompt_containsSectionHeader() {
        String prompt = promptBuilder.buildGapAnalysisPrompt(
                1, "General Description of the AI System",
                List.of(), List.of(),
                "This is our technical documentation."
        );

        assertThat(prompt).contains("Annex IV Section 1");
        assertThat(prompt).contains("General Description of the AI System");
        assertThat(prompt).contains("This is our technical documentation.");
    }

    @Test
    void gapAnalysisPrompt_containsDocumentText() {
        String docText = "Provider: ACME Corp. Purpose: fraud detection.";
        String prompt = promptBuilder.buildGapAnalysisPrompt(
                3, "Some Section", List.of(), List.of(), docText);

        assertThat(prompt).contains(docText);
    }

    // ── buildReportPrompt ──────────────────────────────────────────────────

    @Test
    void reportPrompt_containsClassificationDetails() {
        ClassificationResult classification = new ClassificationResult(
                "high",
                "Annex III, point 4(a)",
                List.of(9, 10, 13, 14, 15),
                "high",
                "The system performs biometric identification in public spaces.",
                List.of("42", "55")
        );

        String prompt = promptBuilder.buildReportPrompt(
                classification, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );

        assertThat(prompt).contains("high");
        assertThat(prompt).contains("Annex III, point 4(a)");
        assertThat(prompt).contains("[9, 10, 13, 14, 15]");
    }

    @Test
    void reportPrompt_highRiskWithGaps_includesGapSummary() {
        ClassificationResult classification = new ClassificationResult(
                "high", "Annex III", List.of(9), "high", "Reason.", List.of());

        GapAnalysisResult gap = GapAnalysisResult.of(1, "General Description", List.of(
                new com.regu.orchestration.dto.GapAnalysisItem(
                        "1_a_1", "Provider name", false, null,
                        "Provider name not found in documentation", "critical",
                        "Add the full legal name of the provider.")
        ));

        String prompt = promptBuilder.buildReportPrompt(
                classification, List.of(), List.of(gap),
                List.of(), List.of(), List.of(), List.of()
        );

        assertThat(prompt).contains("Section 1");
        assertThat(prompt).contains("0/1 requirements met");
        assertThat(prompt).contains("GAP [critical]");
    }

    // ── buildNextQuestionPrompt ────────────────────────────────────────────

    @Test
    void nextQuestionPrompt_containsAnsweredAndRemainingKeys() {
        InterviewAnswer answered = buildAnswer("E1", "provider");

        InterviewQuestionDto remaining = new InterviewQuestionDto(
                "HR1",
                "Does your system perform biometric identification?",
                "Hint: include real-time surveillance.",
                "{}",
                "{\"require_flags\":[\"scope_confirmed\"]}",
                (short) 1, "high_risk", 2, null, false
        );

        String prompt = promptBuilder.buildNextQuestionPrompt(
                List.of(answered), List.of(remaining));

        assertThat(prompt).contains("E1");
        assertThat(prompt).contains("provider");
        assertThat(prompt).contains("HR1");
        assertThat(prompt).contains("Does your system perform biometric identification?");
        assertThat(prompt).contains("preconditions");
    }

    @Test
    void nextQuestionPrompt_noRemaining_mentionsNoRemainingQuestions() {
        String prompt = promptBuilder.buildNextQuestionPrompt(
                List.of(), List.of());

        assertThat(prompt).contains("No remaining questions");
    }

    // ── ClaudeSonnetClient.extractJson ─────────────────────────────────────

    @Test
    void extractJson_stripsMarkdownCodeFence() {
        String input = "```json\n{\"key\": \"value\"}\n```";
        String result = ClaudeSonnetClient.extractJson(input);
        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractJson_stripsPlainCodeFence() {
        String input = "```\n{\"key\": 1}\n```";
        String result = ClaudeSonnetClient.extractJson(input);
        assertThat(result).isEqualTo("{\"key\": 1}");
    }

    @Test
    void extractJson_returnsUnchangedIfNoFence() {
        String input = "{\"key\": \"value\"}";
        assertThat(ClaudeSonnetClient.extractJson(input)).isEqualTo(input);
    }

    @Test
    void extractJson_nullReturnsEmptyObject() {
        assertThat(ClaudeSonnetClient.extractJson(null)).isEqualTo("{}");
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private InterviewAnswer buildAnswer(String questionKey, String rawInput) {
        InterviewSession session = InterviewSession.create();
        InterviewAnswer answer = new InterviewAnswer(session, questionKey, rawInput);
        return answer;
    }
}

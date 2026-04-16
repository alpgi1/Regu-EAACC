package com.regu.orchestration.prompt;

import com.regu.domain.AnnexIvRequirement;
import com.regu.domain.InterviewAnswer;
import com.regu.orchestration.stage1.InterviewQuestionDto;
import com.regu.retrieval.dto.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Constructs LLM prompts from structured data.
 *
 * <p>Every chunk is formatted with a header that includes its database ID so
 * the LLM can reference specific chunks in its citations:
 * <pre>
 * [CHUNK ID: 42 | Source: legal_chunks | Article 10, Paragraph 2]
 * Training, validation and testing data sets shall be subject to...
 * </pre>
 *
 * <p>All methods are stateless — this is a pure formatting utility.
 */
@Component
public class PromptBuilder {

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Builds the user-prompt for Stage 1 risk classification.
     */
    public String buildClassificationPrompt(
            List<InterviewAnswer> answers,
            List<RetrievedChunk> legalChunks,
            List<RetrievedChunk> ruleChunks,
            List<RetrievedChunk> useCaseChunks) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Interview answers\n\n");
        if (answers.isEmpty()) {
            sb.append("No answers recorded.\n");
        } else {
            for (InterviewAnswer a : answers) {
                sb.append("Q [").append(a.getQuestionKey()).append("]: ")
                  .append(a.getRawInput()).append("\n");
            }
        }

        sb.append("\n## Legal context (from EU AI Act)\n\n");
        appendChunks(sb, legalChunks);

        sb.append("\n## Decision rules (procedural compliance logic)\n\n");
        appendChunks(sb, ruleChunks);

        sb.append("\n## Similar use cases\n\n");
        appendChunks(sb, useCaseChunks);

        return sb.toString();
    }

    /**
     * Builds the user-prompt for Stage 2 Annex IV gap analysis.
     */
    public String buildGapAnalysisPrompt(
            int sectionNumber,
            String sectionTitle,
            List<AnnexIvRequirement> requirements,
            List<RetrievedChunk> legalChunks,
            String documentText) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Annex IV Section ").append(sectionNumber)
          .append(": ").append(sectionTitle).append("\n\n");

        sb.append("## Requirements to check\n\n");
        for (AnnexIvRequirement req : requirements) {
            sb.append("- requirementId: ").append(req.getRequirementId()).append("\n");
            sb.append("  entityName: ").append(req.getEntityName()).append("\n");
            sb.append("  extractionTarget: ").append(req.getExtractionTarget()).append("\n\n");
        }

        sb.append("## Legal context (relevant EU AI Act articles)\n\n");
        appendChunks(sb, legalChunks);

        sb.append("## Uploaded document\n\n");
        sb.append(documentText != null ? documentText : "(no document provided)").append("\n");

        return sb.toString();
    }

    /**
     * Builds the user-prompt for final report generation.
     */
    public String buildReportPrompt(
            com.regu.orchestration.dto.ClassificationResult classification,
            List<InterviewAnswer> answers,
            List<com.regu.orchestration.dto.GapAnalysisResult> gaps,
            List<RetrievedChunk> legalChunks,
            List<RetrievedChunk> guideChunks,
            List<RetrievedChunk> useCaseChunks,
            List<RetrievedChunk> ruleChunks) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Classification\n\n");
        sb.append("Risk category: ").append(classification.riskCategory()).append("\n");
        sb.append("Primary legal basis: ").append(classification.primaryLegalBasis()).append("\n");
        sb.append("Applicable articles: ").append(classification.applicableArticles()).append("\n");
        sb.append("Confidence: ").append(classification.confidence()).append("\n");
        sb.append("Reasoning: ").append(classification.reasoning()).append("\n");

        sb.append("\n## Interview transcript\n\n");
        if (answers.isEmpty()) {
            sb.append("No interview transcript available.\n");
        } else {
            for (InterviewAnswer a : answers) {
                sb.append("Q [").append(a.getQuestionKey()).append("]: ")
                  .append(a.getRawInput()).append("\n");
            }
        }

        sb.append("\n## Annex IV gap analysis\n\n");
        if (gaps == null || gaps.isEmpty()) {
            sb.append("Not applicable — system is not high-risk.\n");
        } else {
            for (com.regu.orchestration.dto.GapAnalysisResult gap : gaps) {
                sb.append("Section ").append(gap.sectionNumber())
                  .append(": ").append(gap.sectionTitle())
                  .append(" — ").append(gap.metRequirements()).append("/")
                  .append(gap.totalRequirements()).append(" requirements met (")
                  .append(String.format("%.1f", gap.compliancePercentage())).append("%)\n");
                for (com.regu.orchestration.dto.GapAnalysisItem item : gap.items()) {
                    if (!item.found()) {
                        sb.append("  GAP [").append(item.severity()).append("] ")
                          .append(item.entityName()).append(": ")
                          .append(item.gapDescription()).append("\n");
                    }
                }
            }
        }

        sb.append("\n## Legal context (for citations — use these chunk IDs in [cite:N] references)\n\n");
        appendChunks(sb, legalChunks);

        sb.append("\n## Practical guidance\n\n");
        appendChunks(sb, guideChunks);

        sb.append("\n## Similar use cases (for reference)\n\n");
        appendChunks(sb, useCaseChunks);

        sb.append("\n## Decision logic reference\n\n");
        appendChunks(sb, ruleChunks);

        return sb.toString();
    }

    /**
     * Builds the user-prompt for next-question selection during Stage 1.
     */
    public String buildNextQuestionPrompt(
            List<InterviewAnswer> answered,
            List<InterviewQuestionDto> remaining) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Questions already answered\n\n");
        if (answered.isEmpty()) {
            sb.append("None yet.\n");
        } else {
            for (InterviewAnswer a : answered) {
                sb.append("- [").append(a.getQuestionKey()).append("] answer: ")
                  .append(a.getRawInput()).append("\n");
            }
        }

        sb.append("\n## Remaining questions available\n\n");
        if (remaining.isEmpty()) {
            sb.append("No remaining questions — enough information to classify.\n");
        } else {
            for (InterviewQuestionDto q : remaining) {
                sb.append("- questionKey: ").append(q.questionKey()).append("\n");
                sb.append("  text: ").append(q.displayText()).append("\n");
                if (q.preconditionsJson() != null) {
                    sb.append("  preconditions: ").append(q.preconditionsJson()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Appends each chunk in the standard citation-friendly format:
     * <pre>
     * [CHUNK ID: 42 | Source: legal_chunks | Article 10, Paragraph 2]
     * {content}
     * </pre>
     */
    private void appendChunks(StringBuilder sb, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            sb.append("(none retrieved)\n");
            return;
        }
        for (RetrievedChunk chunk : chunks) {
            sb.append("[CHUNK ID: ").append(chunk.chunkId())
              .append(" | Source: ").append(chunk.sourceTable())
              .append(" | ").append(chunkReference(chunk)).append("]\n");
            String body = chunk.contentWithContext() != null
                    ? chunk.contentWithContext()
                    : chunk.content();
            sb.append(body).append("\n\n");
        }
    }

    /**
     * Derives a human-readable reference label from chunk metadata.
     * Falls back to a generic label if specific fields are absent.
     */
    private String chunkReference(RetrievedChunk chunk) {
        Map<String, Object> meta = chunk.metadata();
        if (meta == null) return chunk.sourceTable();

        return switch (chunk.sourceTable()) {
            case "legal_chunks" -> {
                Object art  = meta.get("article_number");
                Object para = meta.get("paragraph_number");
                Object title = meta.get("article_title");
                if (art != null) {
                    String ref = "Article " + art;
                    if (para != null) ref += "(" + para + ")";
                    if (title != null) ref += " — " + title;
                    yield ref;
                }
                yield "Legal chunk " + chunk.chunkId();
            }
            case "guide_chunks" -> {
                Object path = meta.get("section_path");
                yield path != null ? "Guide: " + path : "Guide chunk " + chunk.chunkId();
            }
            case "use_case_chunks" -> {
                Object id = meta.get("use_case_id");
                yield id != null ? "Use case: " + id : "Use case " + chunk.chunkId();
            }
            case "decision_rule_chunks" -> {
                Object rid = meta.get("rule_id");
                yield rid != null ? "Rule: " + rid : "Decision rule " + chunk.chunkId();
            }
            default -> chunk.sourceTable() + " chunk " + chunk.chunkId();
        };
    }
}

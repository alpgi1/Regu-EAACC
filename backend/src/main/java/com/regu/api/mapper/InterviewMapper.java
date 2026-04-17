package com.regu.api.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regu.api.dto.*;
import com.regu.orchestration.dto.ClassificationResult;
import com.regu.orchestration.dto.ComplianceReport;
import com.regu.orchestration.stage1.InterviewQuestionDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Static mappers for Stage 1 interview DTOs. */
public final class InterviewMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private InterviewMapper() {}

    public static QuestionDto toQuestionDto(InterviewQuestionDto q) {
        if (q == null) return null;
        String type = "free_text";
        List<AnswerOptionDto> options = null;

        if (q.answersJson() != null && !q.answersJson().isBlank()) {
            try {
                JsonNode root = JSON.readTree(q.answersJson());
                JsonNode typeNode = root.get("type");
                if (typeNode != null && !typeNode.isNull()) {
                    type = typeNode.asText("free_text");
                }
                JsonNode optionsNode = root.get("options");
                if (optionsNode != null && optionsNode.isArray()) {
                    options = new ArrayList<>();
                    for (JsonNode opt : optionsNode) {
                        String id = opt.has("id") ? opt.get("id").asText() : opt.path("value").asText();
                        String label = opt.has("label") ? opt.get("label").asText() : id;
                        options.add(new AnswerOptionDto(id, label));
                    }
                }
            } catch (Exception ignored) {
                // Malformed JSON — treat as free_text with no options
            }
        }

        return new QuestionDto(
                q.questionKey(),
                q.displayText(),
                type,
                q.section(),
                options,
                q.hintText()
        );
    }

    public static StartInterviewResponse toStartResponse(UUID sessionId, QuestionDto firstQuestion) {
        return new StartInterviewResponse(sessionId, "in_progress", firstQuestion);
    }

    public static ApiNextStepResponse toNextQuestion(InterviewQuestionDto q) {
        return new ApiNextStepResponse("next_question", toQuestionDto(q), null, null);
    }

    public static ApiNextStepResponse toStage2Required(ClassificationResult classification) {
        return new ApiNextStepResponse(
                "stage2_required", null, toClassificationSummary(classification), null);
    }

    public static ApiNextStepResponse toReportReady(ClassificationResult classification, UUID reportId) {
        return new ApiNextStepResponse(
                "report_ready", null, toClassificationSummary(classification), reportId);
    }

    public static ClassificationSummaryDto toClassificationSummary(ClassificationResult r) {
        if (r == null) return null;
        return new ClassificationSummaryDto(
                r.riskCategory(),
                r.primaryLegalBasis(),
                r.confidence(),
                r.reasoning(),
                r.applicableArticles()
        );
    }
}

package com.regu.orchestration.stage1;

/**
 * Read-only projection of one row from the {@code interview_questions} table.
 *
 * <p>The table has no JPA entity — it is accessed via
 * {@code NamedParameterJdbcTemplate} and mapped to this DTO.
 *
 * @param questionKey        stable short key, e.g. "E1", "HR1"
 * @param displayText        question text shown to the user
 * @param hintText           optional hint (may be null)
 * @param answersJson        JSONB blob describing answer options and routing
 * @param preconditionsJson  JSONB blob with required flags/prior answers (may be null)
 * @param stage              1 = risk classification, 2 = Annex IV
 * @param section            e.g. "entity", "high_risk"
 * @param displayOrder       sort order within the stage
 * @param linkedRuleChunk    FK to decision_rule_chunks.id (may be null)
 * @param isTerminal         true when reaching this question ends the interview
 */
public record InterviewQuestionDto(
        String  questionKey,
        String  displayText,
        String  hintText,
        String  answersJson,
        String  preconditionsJson,
        short   stage,
        String  section,
        int     displayOrder,
        Long    linkedRuleChunk,
        boolean isTerminal
) {}

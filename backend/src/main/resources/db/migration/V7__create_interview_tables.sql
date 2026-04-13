-- V7: Create interview tables for the two-stage guided compliance interview.
--
-- Domain model:
--   interview_questions  — static decision-tree nodes (loaded at startup, never user-written)
--   interview_sessions   — one session per user analysis attempt; tracks current position + flags
--   interview_answers    — one row per question answered per session
--
-- Stage 1: risk classification via the FLI flowchart (questions E1→R5)
-- Stage 2: Annex IV technical-documentation gap assessment (high-risk only)
--
-- Key relationships:
--   interview_sessions → analysis (nullable: session may exist without a saved analysis)
--   interview_answers  → interview_sessions (mandatory)
--   interview_questions → decision_rule_chunks (nullable: RAG linkage where applicable)
--
-- NOTE: decision_rule_chunks uses BIGSERIAL (BIGINT) as its primary key.
--       linked_rule_chunk is therefore BIGINT, not UUID.

-- ============================================================
-- Table: interview_questions
-- ============================================================
-- Static reference data. Loaded via the ingestion pipeline on startup.
-- Never written by user requests. Forms the interview decision tree.
--
-- answers JSONB shape:
--   {
--     "type": "single_select" | "multi_select",
--     "allow_free_text": true,
--     "options": [
--       {
--         "value":       "string",   -- machine key, snake_case
--         "label":       "string",   -- shown to user
--         "next_key":    "string | null", -- next question_key; null if terminal
--         "is_terminal": false,
--         "obligations": ["string"], -- obligation codes triggered
--         "flags":       ["string"], -- status flags set (e.g. "high_risk", "prohibited")
--         "keywords":    ["string"]  -- LLM mapping hints
--       }
--     ]
--   }
--
-- preconditions JSONB shape (null = always reachable if routed to):
--   {
--     "require_flags":   ["string"],          -- all flags must be set
--     "require_answers": { "q_key": "value" } -- specific prior answers required
--   }

CREATE TABLE interview_questions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_key        VARCHAR(20) NOT NULL UNIQUE,
    stage               SMALLINT    NOT NULL CHECK (stage IN (1, 2)),
    section             VARCHAR(50) NOT NULL,
    display_text        TEXT        NOT NULL,
    hint_text           TEXT,
    answers             JSONB       NOT NULL,
    preconditions       JSONB,

    -- Nullable RAG linkage to the decision_rule_chunks table.
    -- decision_rule_chunks uses BIGSERIAL (BIGINT) as its primary key.
    linked_rule_chunk   BIGINT      REFERENCES decision_rule_chunks(id),

    linked_articles     INTEGER[],
    is_terminal         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_iq_question_key ON interview_questions (question_key);
CREATE INDEX idx_iq_stage        ON interview_questions (stage);

COMMENT ON TABLE  interview_questions IS 'Static decision-tree nodes for the guided EU AI Act compliance interview (Stages 1 & 2)';
COMMENT ON COLUMN interview_questions.question_key    IS 'Stable short key used for routing, e.g. "E1", "HR1", "R5"';
COMMENT ON COLUMN interview_questions.stage           IS '1 = risk classification (FLI flowchart), 2 = Annex IV gap assessment';
COMMENT ON COLUMN interview_questions.answers         IS 'Answer options with routing, obligation codes, and flags (see migration comment for schema)';
COMMENT ON COLUMN interview_questions.preconditions   IS 'Flags and prior answers required before this question is reachable; null = unconditional';
COMMENT ON COLUMN interview_questions.linked_rule_chunk IS 'Optional FK into decision_rule_chunks for RAG-augmented question hints';
COMMENT ON COLUMN interview_questions.linked_articles IS 'EU AI Act article numbers relevant to this question';
COMMENT ON COLUMN interview_questions.is_terminal     IS 'True when reaching this question ends the interview immediately';

-- ============================================================
-- Table: interview_sessions
-- ============================================================
-- One session per user analysis attempt. Tracks the current position
-- in the decision tree and accumulates flags as the interview progresses.
--
-- active_flags accumulates status flags such as:
--   'high_risk', 'prohibited', 'scope_confirmed', 'gpai_systemic_risk',
--   'transparency_obligations', 'fria_required', 'out_of_scope', etc.
--
-- entity_types records which operator roles were selected at E1
--   e.g. 'provider', 'deployer', 'distributor', 'importer'

CREATE TABLE interview_sessions (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Nullable: a session may be created before an analysis record exists
    analysis_id           UUID        REFERENCES analysis(id),

    stage                 SMALLINT    NOT NULL DEFAULT 1,

    status                VARCHAR(30) NOT NULL DEFAULT 'active'
                          CHECK (status IN (
                              'active',
                              'stage1_complete',
                              'stage2_started',
                              'complete',
                              'abandoned'
                          )),

    -- Current position in the decision tree
    current_question_key  VARCHAR(20),

    -- Multi-value columns stored as PostgreSQL native arrays
    entity_types          VARCHAR(30)[],           -- operator roles selected at E1
    active_flags          VARCHAR(50)[],            -- accumulated interview flags

    -- Final outcome populated when status = 'stage1_complete' or 'complete'
    risk_classification   VARCHAR(20) CHECK (risk_classification IN (
                              'unacceptable', 'high', 'limited', 'minimal', 'out_of_scope'
                          )),

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_is_analysis_id ON interview_sessions (analysis_id);
CREATE INDEX idx_is_status       ON interview_sessions (status);

COMMENT ON TABLE  interview_sessions IS 'One guided-interview session per user analysis attempt; tracks position and accumulated flags';
COMMENT ON COLUMN interview_sessions.analysis_id          IS 'FK to analysis; nullable — session may be created before an analysis is persisted';
COMMENT ON COLUMN interview_sessions.stage                IS 'Current interview stage: 1 = risk classification, 2 = Annex IV gap assessment';
COMMENT ON COLUMN interview_sessions.status               IS 'Lifecycle state: active → stage1_complete → stage2_started → complete (or abandoned)';
COMMENT ON COLUMN interview_sessions.current_question_key IS 'question_key of the next question to present to the user';
COMMENT ON COLUMN interview_sessions.entity_types         IS 'Operator roles identified at E1 (provider, deployer, distributor, importer, etc.)';
COMMENT ON COLUMN interview_sessions.active_flags         IS 'Flags accumulated throughout the session (e.g. high_risk, prohibited, fria_required)';
COMMENT ON COLUMN interview_sessions.risk_classification  IS 'Final Stage 1 risk classification; populated when stage1_complete or complete';

-- ============================================================
-- Table: interview_answers
-- ============================================================
-- One row per question answered per session.
--
-- raw_input   — verbatim user input (text or selected option value)
-- mapped_answer — normalised answer value after LLM mapping (may differ from raw_input)
-- mapped_confidence — LLM confidence in the mapping (0.000–1.000)
-- obligations — obligation codes triggered by this answer (copied from the option)
-- flags_applied — flags that were set on the session as a result of this answer

CREATE TABLE interview_answers (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id        UUID        NOT NULL REFERENCES interview_sessions(id),
    question_key      VARCHAR(20) NOT NULL,

    -- User input
    raw_input         TEXT        NOT NULL,

    -- LLM-normalised mapping
    mapped_answer     VARCHAR(50),
    mapped_confidence NUMERIC(4,3) CHECK (mapped_confidence BETWEEN 0 AND 1),

    -- Effects of this answer (populated at answer-time from the matching option)
    obligations       VARCHAR(50)[],
    flags_applied     VARCHAR(50)[],

    answered_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ia_session_id   ON interview_answers (session_id);
CREATE INDEX idx_ia_question_key ON interview_answers (question_key);

COMMENT ON TABLE  interview_answers IS 'One recorded answer per question per interview session';
COMMENT ON COLUMN interview_answers.session_id        IS 'FK to the owning interview session';
COMMENT ON COLUMN interview_answers.question_key      IS 'Key of the question that was answered (denormalised for fast lookup)';
COMMENT ON COLUMN interview_answers.raw_input         IS 'Verbatim user input before LLM normalisation';
COMMENT ON COLUMN interview_answers.mapped_answer     IS 'Normalised answer value (snake_case option value) after LLM mapping';
COMMENT ON COLUMN interview_answers.mapped_confidence IS 'LLM confidence in the mapping, 0.000–1.000';
COMMENT ON COLUMN interview_answers.obligations       IS 'Obligation codes triggered by this answer (copied from the matched option at write-time)';
COMMENT ON COLUMN interview_answers.flags_applied     IS 'Status flags applied to the session as a result of this answer';

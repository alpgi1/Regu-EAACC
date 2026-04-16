-- V10: Populate annex_iv_sections and create annex_iv_requirements table.
--
-- annex_iv_sections was created empty in V8. This migration seeds the 9
-- canonical Annex IV sections so the FK in annex_iv_requirements can be
-- satisfied immediately.
--
-- annex_iv_requirements stores granular extraction targets for each Annex IV
-- technical-documentation requirement. Each row corresponds to one specific
-- piece of information that must appear in a provider's technical dossier.
--
-- Used in Stage 2: when a user uploads a document, the system checks each
-- extraction_target against the document content. If the target is not found,
-- fallback_prompt is used to ask the user a follow-up question.
--
-- requirement_id format: "<section>_<subsection>_<item>"
--   e.g. "1_a_1", "2_b_3", "9_2"
-- section_number is parsed from the leading digit (1–9).

-- ============================================================
-- Step 1 — Seed annex_iv_sections (idempotent)
-- ============================================================

INSERT INTO annex_iv_sections (section_number, section_key, display_title, instructions)
VALUES
  (1, 'annex_iv_s1',
      'General description of the AI system',
      'Please provide or upload documentation covering your AI system general description: intended purpose, provider information, version history, and how the system interacts with hardware and software.'),
  (2, 'annex_iv_s2',
      'Detailed description of elements and development process',
      'Please provide or upload documentation covering your AI system development process: development methods, design specifications, system architecture, data requirements, human oversight assessment, pre-determined changes, validation/testing, and cybersecurity.'),
  (3, 'annex_iv_s3',
      'Monitoring, functioning and control',
      'Please provide or upload documentation covering system monitoring and control: performance capabilities and limitations, accuracy levels, foreseeable unintended outcomes, risk sources, human oversight measures, and input data specifications.'),
  (4, 'annex_iv_s4',
      'Appropriateness of performance metrics',
      'Please provide or upload documentation justifying your chosen performance metrics and explaining why they are appropriate for your specific AI system.'),
  (5, 'annex_iv_s5',
      'Risk management system',
      'Please provide or upload your risk management system documentation established in accordance with Article 9 of the EU AI Act.'),
  (6, 'annex_iv_s6',
      'Relevant changes through lifecycle',
      'Please provide or upload documentation of relevant changes made to the AI system by the provider throughout its lifecycle.'),
  (7, 'annex_iv_s7',
      'Harmonised standards applied',
      'Please provide or upload information about harmonised EU standards, alternative solutions, or other technical specifications applied to the system.'),
  (8, 'annex_iv_s8',
      'EU declaration of conformity',
      'Please upload a copy of your EU declaration of conformity as referred to in Article 47 of the EU AI Act.'),
  (9, 'annex_iv_s9',
      'Post-market monitoring',
      'Please provide or upload your post-market monitoring plan and evaluation system in accordance with Article 72 of the EU AI Act.')
ON CONFLICT (section_number) DO NOTHING;

-- ============================================================
-- Step 2 — Create annex_iv_requirements table
-- ============================================================

CREATE TABLE annex_iv_requirements (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Stable identifier matching Annex IV numbering (e.g. "1_a_1", "2_b_3")
    requirement_id    VARCHAR(20) NOT NULL UNIQUE,

    -- Section this requirement belongs to (FK → annex_iv_sections.section_number)
    section_number    SMALLINT    NOT NULL,

    -- Annex IV category label (e.g. "1_a (Intended Purpose, Provider and Version)")
    category          VARCHAR(200) NOT NULL,

    -- Human-readable name of the specific information item
    entity_name       VARCHAR(200) NOT NULL,

    -- Description of what to extract/look for in uploaded documents
    extraction_target TEXT        NOT NULL,

    -- Question to ask if extraction_target is not found in the document
    fallback_prompt   TEXT        NOT NULL,

    -- Whether this requirement may be omitted without non-compliance
    is_optional       BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Presentation order within a section (auto-assigned during ingestion)
    display_order     INTEGER     NOT NULL,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_annex_iv_req_section
        FOREIGN KEY (section_number)
        REFERENCES annex_iv_sections(section_number)
        ON DELETE CASCADE,

    CONSTRAINT chk_section_range CHECK (section_number BETWEEN 1 AND 9)
);

-- Index for the most common access pattern: fetch all requirements for a section
CREATE INDEX idx_annex_iv_req_section ON annex_iv_requirements (section_number);

-- Ordered retrieval within a section
CREATE INDEX idx_annex_iv_req_display_order ON annex_iv_requirements (section_number, display_order);

COMMENT ON TABLE annex_iv_requirements IS
    'Granular extraction targets for each Annex IV technical-documentation requirement. '
    'One row per atomic piece of information required under Article 11 / Annex IV. '
    'Populated by the ingestion pipeline from corpus/annex_iv/requirements.json.';

COMMENT ON COLUMN annex_iv_requirements.requirement_id IS
    'Stable Annex IV identifier — e.g. "1_a_1", "2_b_3". '
    'The leading digit maps to annex_iv_sections.section_number.';

COMMENT ON COLUMN annex_iv_requirements.extraction_target IS
    'What to look for in user-uploaded technical documentation. '
    'Used by the document analysis pipeline in Stage 2.';

COMMENT ON COLUMN annex_iv_requirements.fallback_prompt IS
    'Follow-up question to ask the user when extraction_target is not '
    'found in their uploaded document. Drives the Stage 2 Q&A flow.';

COMMENT ON COLUMN annex_iv_requirements.display_order IS
    'Order in which requirements are shown within a section. '
    'Matches the order they appear in corpus/annex_iv/requirements.json.';

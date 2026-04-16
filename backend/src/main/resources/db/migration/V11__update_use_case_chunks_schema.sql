-- V11: Extend use_case_chunks with columns for the revised use case corpus format.
--
-- The original use_case_chunks schema (V3) was designed for scenarios with
-- fields like scenario_domain and risk_category. The new corpus format
-- (corpus/use_cases/scenarios.json) introduces a richer metadata structure
-- with sector, actor_role, sme_privilege, legal_basis, and a compliance_roadmap.
--
-- This migration adds the new columns without touching existing data, so any
-- legacy rows inserted with the old schema are preserved.
--
-- The use_case_id is the stable identifier matching the corpus JSON
-- (e.g. "UC_HR_A1"). A partial unique index enforces uniqueness for
-- non-null values, allowing legacy rows without a use_case_id to coexist.

ALTER TABLE use_case_chunks
    ADD COLUMN IF NOT EXISTS use_case_id       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS sector            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS actor_role        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sme_privilege     BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS compliance_roadmap TEXT,
    ADD COLUMN IF NOT EXISTS legal_basis       VARCHAR(100);

-- Partial unique index: enforces uniqueness only when use_case_id is set,
-- so legacy rows (use_case_id IS NULL) are unaffected.
CREATE UNIQUE INDEX IF NOT EXISTS idx_use_case_id
    ON use_case_chunks (use_case_id) WHERE use_case_id IS NOT NULL;

COMMENT ON COLUMN use_case_chunks.use_case_id IS
    'Stable identifier from corpus JSON (e.g. "UC_HR_A1"). '
    'Used as the idempotency key during ingestion.';

COMMENT ON COLUMN use_case_chunks.sector IS
    'Industry sector of the use case (e.g. "HR & Staffing", "FinTech").';

COMMENT ON COLUMN use_case_chunks.actor_role IS
    'EU AI Act actor role — "Provider", "Deployer", or "Both (Provider & Deployer)".';

COMMENT ON COLUMN use_case_chunks.sme_privilege IS
    'True when the use case involves SME-specific privileges or reduced obligations.';

COMMENT ON COLUMN use_case_chunks.compliance_roadmap IS
    'JSON array of concrete compliance steps as a TEXT column. '
    'Serialised from the compliance_roadmap array in the corpus JSON.';

COMMENT ON COLUMN use_case_chunks.legal_basis IS
    'Primary legal basis from the corpus metadata (e.g. "Annex III", "Article 50").';

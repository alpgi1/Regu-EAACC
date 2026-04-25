ALTER TABLE citation DROP CONSTRAINT chk_citation_source_table;

ALTER TABLE citation ADD CONSTRAINT chk_citation_source_table CHECK (
    source_table IN ('legal_chunks', 'use_case_chunks', 'guide_chunks', 'decision_rule_chunks')
);

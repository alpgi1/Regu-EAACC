package com.regu.domain.repository;

import com.regu.domain.AnnexIvSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link AnnexIvSection}.
 *
 * <p>Annex IV sections are static reference data populated by the ingestion
 * pipeline. At runtime, the service layer looks sections up by section_number
 * to attach context to Stage 2 submissions.
 */
@Repository
public interface AnnexIvSectionRepository extends JpaRepository<AnnexIvSection, UUID> {

    /**
     * Find a section by its Annex IV section number (1–9).
     * Used to validate and enrich Stage 2 submissions.
     */
    Optional<AnnexIvSection> findBySectionNumber(Short sectionNumber);

    /**
     * Find a section by its stable section key (e.g. {@code "annex_iv_s1"}).
     */
    Optional<AnnexIvSection> findBySectionKey(String sectionKey);
}

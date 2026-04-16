package com.regu.domain.repository;

import com.regu.domain.AnnexIvRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link AnnexIvRequirement}.
 *
 * <p>Annex IV requirements are static reference data populated by the
 * ingestion pipeline. At runtime the Stage 2 analysis pipeline retrieves
 * requirements by section to drive document gap-checking and Q&amp;A follow-ups.
 */
@Repository
public interface AnnexIvRequirementRepository extends JpaRepository<AnnexIvRequirement, UUID> {

    /**
     * Returns all requirements for a given Annex IV section, ordered by
     * display_order. Used by the Stage 2 analysis pipeline.
     */
    List<AnnexIvRequirement> findAllBySectionNumberOrderByDisplayOrder(Short sectionNumber);

    /**
     * Look up a single requirement by its stable Annex IV identifier.
     * Useful for direct lookups from citation references.
     */
    Optional<AnnexIvRequirement> findByRequirementId(String requirementId);
}

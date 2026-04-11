package com.regu.domain.repository;

import com.regu.domain.Citation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CitationRepository extends JpaRepository<Citation, UUID> {

    List<Citation> findAllByReportIdOrderByDisplayOrderAsc(UUID reportId);

    List<Citation> findAllByReportIdAndClaimSection(UUID reportId, Citation.ClaimSection claimSection);
}

package com.regu.domain.repository;

import com.regu.domain.Analysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    Page<Analysis> findAllByStatus(Analysis.Status status, Pageable pageable);

    Page<Analysis> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

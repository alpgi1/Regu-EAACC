package com.regu.domain.repository;

import com.regu.domain.Stage2Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Stage2Submission}.
 *
 * <p>Provides standard CRUD plus queries needed by the Stage 2 assessment flow:
 * <ul>
 *   <li>Find all submissions for a session (to build the progress view)</li>
 *   <li>Find a specific section's submission for a session (to resume or update)</li>
 *   <li>Find submissions pending analysis (for background processing)</li>
 * </ul>
 */
@Repository
public interface Stage2SubmissionRepository extends JpaRepository<Stage2Submission, UUID> {

    /**
     * Find all submissions for a given session, ordered by section number.
     * Used to build the Stage 2 progress view and final report.
     */
    List<Stage2Submission> findAllBySessionIdOrderBySectionNumberAsc(UUID sessionId);

    /**
     * Find the submission for a specific section within a session.
     * The pair (sessionId, sectionNumber) is UNIQUE in the database.
     */
    Optional<Stage2Submission> findBySessionIdAndSectionNumber(UUID sessionId, Integer sectionNumber);

    /**
     * Find all submissions in a given analysis status, ordered oldest first.
     * Used to pick up pending or stalled analyses for background processing.
     */
    List<Stage2Submission> findAllByAnalysisStatusOrderByCreatedAtAsc(String analysisStatus);
}

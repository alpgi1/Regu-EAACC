package com.regu.domain.repository;

import com.regu.domain.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link InterviewSession}.
 *
 * <p>Provides standard CRUD plus queries needed by the interview orchestration layer:
 * <ul>
 *   <li>Find active sessions for a given analysis (to resume an interrupted session)</li>
 *   <li>Find sessions by status for monitoring / administrative queries</li>
 * </ul>
 *
 * <p>No custom JPQL or native queries are required at this stage; all queries
 * are derived from method names following Spring Data conventions.
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    /**
     * Find all sessions for the given analysis, ordered most recent first.
     * Used to detect whether a prior session exists and whether it can be resumed.
     */
    List<InterviewSession> findAllByAnalysis_IdOrderByCreatedAtDesc(UUID analysisId);

    /**
     * Find the single active session for a given analysis, if one exists.
     * There should be at most one {@code active} session per analysis at any time.
     */
    Optional<InterviewSession> findByAnalysis_IdAndStatus(UUID analysisId, InterviewSession.Status status);

    /**
     * Find all sessions that are in a given status, ordered most recent first.
     * Useful for monitoring (e.g. finding sessions stuck in {@code active}).
     */
    List<InterviewSession> findAllByStatusOrderByCreatedAtDesc(InterviewSession.Status status);
}

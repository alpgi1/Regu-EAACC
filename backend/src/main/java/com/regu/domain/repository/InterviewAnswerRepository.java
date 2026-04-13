package com.regu.domain.repository;

import com.regu.domain.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link InterviewAnswer}.
 *
 * <p>Provides standard CRUD plus queries needed by the interview orchestration layer:
 * <ul>
 *   <li>Retrieve all answers for a session (for report generation and resume logic)</li>
 *   <li>Find the answer to a specific question within a session (precondition checks)</li>
 * </ul>
 *
 * <p>No custom JPQL or native queries are required at this stage.
 */
@Repository
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, UUID> {

    /**
     * Return all answers recorded in a session, ordered chronologically.
     * Used when resuming a session, generating the final report, or
     * evaluating preconditions for later questions.
     */
    List<InterviewAnswer> findAllBySession_IdOrderByAnsweredAtAsc(UUID sessionId);

    /**
     * Find the recorded answer for a specific question within a session.
     * Answers are expected to be unique per (session, question_key) pair —
     * the caller must handle re-answering by deleting the prior row first.
     */
    Optional<InterviewAnswer> findBySession_IdAndQuestionKey(UUID sessionId, String questionKey);

    /**
     * Check whether a specific question has already been answered in a session.
     * Lightweight existence check used during routing and precondition evaluation.
     */
    boolean existsBySession_IdAndQuestionKey(UUID sessionId, String questionKey);
}

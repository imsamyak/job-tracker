package com.jobtracker.mail;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailEventRepository extends JpaRepository<MailEvent, Long> {
    boolean existsByMessageId(String messageId);

    java.util.Optional<MailEvent> findFirstByJobIdOrderByReceivedAtAsc(Long jobId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByReceivedAtBefore(java.time.Instant cutoff);

    /** The emails looked at, newest first (not the marker rows). */
    List<MailEvent> findTop10ByOutcomeNotOrderByReceivedAtDesc(String outcome);

    @org.springframework.transaction.annotation.Transactional
    void deleteByOutcomeAndSubjectContainingIgnoreCase(String outcome, String part);

    /** The latest emails that added or changed a job, by when they were processed. */
    List<MailEvent> findTop50ByAppliedStatusIsNotNullOrderByCreatedAtDesc();

    /** The emails that changed a job, newest first. */
    List<MailEvent> findTop10ByAppliedStatusIsNotNullOrderByReceivedAtDesc();
}

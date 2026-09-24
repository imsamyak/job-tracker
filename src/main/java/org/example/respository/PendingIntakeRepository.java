package org.example.respository;

import org.example.domain.constant.PendingIntakeStatus;
import org.example.domain.entity.PendingIntake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingIntakeRepository extends JpaRepository<PendingIntake, UUID> {

    List<PendingIntake> findByStatus(PendingIntakeStatus status);

    /** Pre-extraction dedupe: the same URL is already queued (or awaiting a Yes/No) - don't accept it again. */
    Optional<PendingIntake> findFirstByUrlAndStatusInOrderByCreatedAtDesc(String url, Collection<PendingIntakeStatus> statuses);
}

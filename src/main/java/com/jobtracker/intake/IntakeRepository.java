package com.jobtracker.intake;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeRepository extends JpaRepository<Intake, Long> {
    List<Intake> findByStatusOrderByCreatedAtAsc(Intake.Status status);

    List<Intake> findByStatusNotInOrderByCreatedAtDesc(Collection<Intake.Status> statuses);
}

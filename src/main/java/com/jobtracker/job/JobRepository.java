package com.jobtracker.job;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findAllByOrderByAddedOnDescIdDesc();
}

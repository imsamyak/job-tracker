package org.example.respository;

import org.example.domain.constant.FitStatus;
import org.example.domain.constant.JobStatus;
import org.example.domain.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByFolderName(String folderName);

    /** Pre-extraction dedupe: the same URL was already turned into a job - never re-read it. */
    Optional<Job> findByUrl(String url);

    List<Job> findByStatusIn(Collection<JobStatus> statuses);

    List<Job> findByFitStatusIn(Collection<FitStatus> fitStatuses);

    /** Rule 1: job id given - a match here, for the same company, is a certain duplicate. */
    Optional<Job> findByCompanyAndJobId(String company, String jobId);

    /** Rule 2: job id not given - the single most recent job in the system, whatever its company/role. */
    Optional<Job> findTopByOrderByCreatedAtDesc();

    /** Rule 3: job id not given, rules 1/2 didn't fire - is there a same company+role job from the last 24h? */
    Optional<Job> findFirstByCompanyAndRoleAndCreatedAtAfterOrderByCreatedAtDesc(String company, String role, Instant after);
}

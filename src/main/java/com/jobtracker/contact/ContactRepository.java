package com.jobtracker.contact;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByJobIdOrderByCreatedAtAscIdAsc(Long jobId);

    void deleteByJobId(Long jobId);
}

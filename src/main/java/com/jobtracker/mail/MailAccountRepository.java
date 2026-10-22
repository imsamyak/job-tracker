package com.jobtracker.mail;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailAccountRepository extends JpaRepository<MailAccount, Long> {
    List<MailAccount> findAllByOrderByAddedAtAsc();

    Optional<MailAccount> findByEmail(String email);
}

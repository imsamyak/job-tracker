package com.jobtracker.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtracker.job.Job;
import com.jobtracker.job.JobRepository;
import com.jobtracker.job.JobStatus;
import com.jobtracker.mail.MailClassifier.Outcome;
import com.jobtracker.mail.MailClassifier.Verdict;
import com.jobtracker.secret.SecretBox;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest
class MailSyncTest {

    // what the fake mailbox holds, what the fake Claude was asked, and how it answers
    static final List<MailMessage> inbox = new ArrayList<>();
    static final List<String> asked = new ArrayList<>();
    static BiFunction<MailMessage, List<Job>, Verdict> answer;
    static String mailboxError;

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        MailSource source() {
            return (email, password, since, max, progress) -> {
                if (mailboxError != null) throw new MailVerifier.MailException(mailboxError);
                return inbox.stream().filter(m -> !m.receivedAt().isBefore(since)).toList().stream().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            };
        }

        @Bean
        @Primary
        MailClassifier classifier() {
            return (m, candidates) -> {
                asked.add(m.subject());
                return answer.apply(m, candidates);
            };
        }
    }

    @Autowired MailSyncService sync;
    @Autowired MailAccountRepository accounts;
    @Autowired MailEventRepository events;
    @Autowired JobRepository jobs;
    @Autowired SecretBox box;

    MailAccount account;

    @BeforeEach
    void reset() {
        events.deleteAll();
        accounts.deleteAll();
        jobs.deleteAll();
        inbox.clear();
        asked.clear();
        mailboxError = null;
        answer = (m, c) -> Verdict.none("default");
        MailAccount a = new MailAccount();
        a.setEmail("me@gmail.com");
        a.setLockedPassword(box.lock("secret"));
        account = accounts.save(a);
    }

    Job job(String company, String role, String postingId, JobStatus status) {
        Job j = new Job();
        j.setCompany(company);
        j.setRole(role);
        j.setPostingId(postingId);
        j.setStatus(status);
        return jobs.save(j);
    }

    static MailMessage mail(String id, String from, String subject, String text, long minutesAgo) {
        return new MailMessage(id, from, subject, Instant.now().minusSeconds(minutesAgo * 60), text);
    }

    JobStatus statusOf(Job j) {
        return jobs.findById(j.getId()).orElseThrow().getStatus();
    }

    @Test
    void aRejectionMovesTheJobAndIsRecorded() {
        Job acme = job("Acme Systems", "Backend Engineer", "R-4821", JobStatus.APPLIED);
        inbox.add(mail("m1", "hr@acme.com", "Update on your application", "Unfortunately we are not moving forward with Acme Systems.", 10));
        answer = (m, c) -> new Verdict(acme.getId(), Outcome.REJECTED, "Turned down.");

        sync.syncAccount(account);

        assertThat(statusOf(acme)).isEqualTo(JobStatus.REJECTED);
        MailEvent e = events.findAll().get(0);
        assertThat(e.getAppliedStatus()).isEqualTo("REJECTED");
        assertThat(e.getJobId()).isEqualTo(acme.getId());
        assertThat(accounts.findById(account.getId()).orElseThrow().getLastSyncedAt()).isNotNull();
    }

    @Test
    void emailsThatMentionNoTrackedCompanyAreNeverSentToClaude() {
        job("Acme Systems", "Backend Engineer", null, JobStatus.APPLIED);
        inbox.add(mail("m1", "news@shop.com", "Big sale this weekend", "Everything half price.", 5));

        sync.syncAccount(account);

        assertThat(asked).isEmpty();
        assertThat(events.count()).isZero();
    }

    @Test
    void anEmailIsOnlyReadOnce() {
        Job acme = job("Acme Systems", "Backend Engineer", null, JobStatus.APPLIED);
        inbox.add(mail("m1", "hr@acme.com", "Interview", "Acme Systems would like to schedule an interview.", 10));
        answer = (m, c) -> new Verdict(acme.getId(), Outcome.IN_TALKS, "Interview.");

        sync.syncAccount(account);
        sync.syncAccount(account);

        assertThat(asked).hasSize(1);
        assertThat(statusOf(acme)).isEqualTo(JobStatus.IN_REVIEW);
    }

    @Test
    void theLaterEmailWinsWhateverOrderTheMailboxReturnsThem() {
        Job acme = job("Acme Systems", "Backend Engineer", null, JobStatus.APPLIED);
        inbox.add(mail("late", "hr@acme.com", "Decision", "Acme Systems: we will not proceed.", 5));
        inbox.add(mail("early", "hr@acme.com", "Interview", "Acme Systems invites you to an interview.", 500));
        answer = (m, c) -> new Verdict(acme.getId(), m.subject().equals("Decision") ? Outcome.REJECTED : Outcome.IN_TALKS, "");

        sync.syncAccount(account);

        assertThat(asked).containsExactly("Interview", "Decision");
        assertThat(statusOf(acme)).isEqualTo(JobStatus.REJECTED);
    }

    @Test
    void aRejectedJobIsLeftAlone() {
        Job acme = job("Acme Systems", "Backend Engineer", null, JobStatus.REJECTED);
        inbox.add(mail("m1", "hr@acme.com", "Interview", "Acme Systems invites you to an interview.", 5));

        sync.syncAccount(account);

        assertThat(statusOf(acme)).isEqualTo(JobStatus.REJECTED);
        assertThat(jobs.count()).isEqualTo(1); // nothing new was added either
    }

    @Test
    void aConfirmationNeverUndoesAnInterview() {
        Job acme = job("Acme Systems", "Backend Engineer", null, JobStatus.IN_REVIEW);
        inbox.add(mail("m1", "hr@acme.com", "Application received", "Acme Systems received your application.", 5));
        answer = (m, c) -> new Verdict(acme.getId(), Outcome.APPLIED, "Received.");

        sync.syncAccount(account);

        assertThat(statusOf(acme)).isEqualTo(JobStatus.IN_REVIEW);
        assertThat(events.findAll().get(0).getAppliedStatus()).isNull();
    }

    @Test
    void aJobClaudeWasNotOfferedCannotBeChanged() {
        Job acme = job("Acme Systems", "Backend Engineer", null, JobStatus.APPLIED);
        Job other = job("Orbit Works", "Data Engineer", null, JobStatus.APPLIED);
        inbox.add(mail("m1", "hr@acme.com", "Update", "Acme Systems: not moving forward.", 5));
        answer = (m, c) -> new Verdict(other.getId(), Outcome.REJECTED, "Wrong job.");

        sync.syncAccount(account);

        assertThat(statusOf(other)).isEqualTo(JobStatus.APPLIED);
        assertThat(statusOf(acme)).isEqualTo(JobStatus.APPLIED);
    }

    @Test
    void anUnreadableMailboxIsReportedAndRetriedFromTheSamePoint() {
        job("Acme Systems", "Backend Engineer", null, JobStatus.APPLIED);
        mailboxError = "Gmail no longer accepts the app password.";

        sync.syncAccount(account);

        MailAccount a = accounts.findById(account.getId()).orElseThrow();
        assertThat(a.getLastError()).contains("no longer accepts");
        assertThat(a.getLastSyncedAt()).isNull();
    }

    @Test
    void aClaudeFailureStopsTheRunWithoutLosingTheEmail() {
        Job acme = job("Acme Systems", "Backend Engineer", null, JobStatus.APPLIED);
        inbox.add(mail("m1", "hr@acme.com", "Update", "Acme Systems: not moving forward.", 5));
        answer = (m, c) -> {
            throw new RuntimeException("boom"); // the fake rethrows as unchecked; the service treats it as a failed run
        };

        sync.syncAccount(account);

        assertThat(events.count()).isZero(); // not marked as read, so the next run tries it again
        assertThat(accounts.findById(account.getId()).orElseThrow().getLastSyncedAt()).isNull();
        assertThat(statusOf(acme)).isEqualTo(JobStatus.APPLIED);
    }

    @Test
    void theMatcherFindsACompanyByNameByItsFirstWordOrByJobId() {
        Job full = job("Acme Systems", "QA", null, JobStatus.APPLIED);
        Job first = job("Nimbus Cloud", "SRE", null, JobStatus.APPLIED);
        Job byId = job("Quartz Labs", "UI", "QL-9921", JobStatus.APPLIED);
        Job generic = job("Cloud Nine", "Dev", null, JobStatus.APPLIED);
        List<Job> all = jobs.findAll();

        assertThat(MailMatcher.candidates(mail("a", "x@y.com", "Hello", "Thanks from Acme Systems, Inc.", 1), all)).extracting(Job::getId).containsExactly(full.getId());
        assertThat(MailMatcher.candidates(mail("b", "careers@nimbus.io", "Hi", "We at Nimbus liked your profile.", 1), all)).extracting(Job::getId).containsExactly(first.getId());
        assertThat(MailMatcher.candidates(mail("c", "x@y.com", "Re: ql-9921", "Please see the attached.", 1), all)).extracting(Job::getId).containsExactly(byId.getId());
        // "cloud" alone is too common to count, but the full name still does
        assertThat(MailMatcher.candidates(mail("d", "x@y.com", "Hi", "Try our cloud product.", 1), all)).isEmpty();
        assertThat(MailMatcher.candidates(mail("e", "x@y.com", "Hi", "Welcome to Cloud Nine.", 1), all)).extracting(Job::getId).containsExactly(generic.getId());
    }

    @Test
    void mailOlderThanTheWindowIsNeverReadWhateverDateIsAsked() {
        Job acme = job("Acme Systems", "Backend Engineer", null, JobStatus.APPLIED);
        inbox.add(mail("old", "hr@acme.com", "Old rejection", "Acme Systems: not moving forward.", 100L * 24 * 60)); // 100 days old
        answer = (m, c) -> new Verdict(acme.getId(), Outcome.REJECTED, "");

        sync.syncAccount(account, Instant.now().minusSeconds(400L * 86400)); // asks for more than a year back

        assertThat(asked).isEmpty();
        assertThat(statusOf(acme)).isEqualTo(JobStatus.APPLIED);
    }

    @Test
    void whatWasDecidedAboutMailThatFellOutOfTheWindowIsForgotten() {
        MailEvent old = new MailEvent();
        old.setMessageId("old");
        old.setOutcome("NONE");
        old.setReceivedAt(Instant.now().minusSeconds(200L * 86400));
        events.save(old);
        MailEvent recent = new MailEvent();
        recent.setMessageId("recent");
        recent.setOutcome("NONE");
        recent.setReceivedAt(Instant.now().minusSeconds(5L * 86400));
        events.save(recent);

        sync.syncAll();

        assertThat(events.findAll()).filteredOn(e -> !"MARKER".equals(e.getOutcome())).extracting(MailEvent::getMessageId).containsExactly("recent");
    }

    @Test
    void anApplicationForACompanyNotTrackedYetIsAdded() {
        inbox.add(mail("m1", "no-reply@zeta.com", "Thank you for applying to Zeta Corp", "We received your application for Data Engineer (R-77).", 100));
        answer = (m, c) -> new Verdict(0, Outcome.APPLIED, "Confirmation.", "Zeta Corp", "Data Engineer", "R-77");

        sync.syncAccount(account);

        Job added = jobs.findAll().get(0);
        assertThat(added.getCompany()).isEqualTo("Zeta Corp");
        assertThat(added.getRole()).isEqualTo("Data Engineer");
        assertThat(added.getPostingId()).isEqualTo("R-77");
        assertThat(added.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(added.getAddedFrom()).isEqualTo("GMAIL");
        assertThat(added.getPlatform()).isEqualTo("Company email");
        assertThat(MailPlatform.of("Samyak <jobs-noreply@linkedin.com>")).isEqualTo("LinkedIn");
        assertThat(MailPlatform.of("GE Vernova Workday Notifications <ge@myworkday.com>")).isEqualTo("Workday");
        assertThat(MailPlatform.of("Naukri <info@naukri.com>")).isEqualTo("Naukri");
        assertThat(events.findAll().get(0).getAppliedStatus()).isEqualTo("APPLIED");
    }

    @Test
    void aRejectionForAnUntrackedJobIsAddedAsRejectedAndADuplicateIsNot() {
        job("Zeta Corp", "Data Engineer", null, JobStatus.APPLIED);
        inbox.add(mail("m1", "hr@orbit.io", "Update on your application", "Unfortunately Orbit Labs will not be moving forward.", 100));
        inbox.add(mail("m2", "hr@zeta.com", "Your application", "We received your application at Zeta Corp.", 90));
        answer = (m, c) -> m.subject().startsWith("Update")
                ? new Verdict(0, Outcome.REJECTED, "", "Orbit Labs", "QA Engineer", "")
                : new Verdict(0, Outcome.APPLIED, "", "Zeta Corp", "Data Engineer", "");

        sync.syncAccount(account);

        assertThat(jobs.findAll()).extracting(Job::getCompany).containsExactlyInAnyOrder("Zeta Corp", "Orbit Labs");
        assertThat(jobs.findAll().stream().filter(j -> j.getCompany().equals("Orbit Labs")).findFirst().orElseThrow().getStatus()).isEqualTo(JobStatus.REJECTED);
    }

    @Test
    void aSummaryOfAppliedJobsAddsEveryOne() {
        inbox.add(mail("m1", "info@naukri.com", "You applied for 3 jobs on 14 Oct", "You applied to A Corp, B Corp and C Corp.", 100));
        answer = (m, c) -> new Verdict(0, Outcome.APPLIED, "Summary.", "A Corp", "Dev", "",
                List.of(new MailClassifier.Application("B Corp", "QA", ""), new MailClassifier.Application("C Corp", "", "")));

        sync.syncOne(account.getId(), Instant.now().minusSeconds(30L * 86400));

        assertThat(jobs.findAll()).extracting(Job::getCompany).containsExactlyInAnyOrder("A Corp", "B Corp", "C Corp");
        assertThat(jobs.findAll()).extracting(Job::getStatus).containsOnly(JobStatus.APPLIED);
        assertThat(sync.lastRun().added).isEqualTo(3);
    }

    @Test
    void aCompanyWithNoJobTitleIsAddedAndNotAddedTwice() {
        inbox.add(mail("m1", "hr@hindco.com", "Shortlisted: your application", "You are shortlisted at HINDCO.", 100));
        inbox.add(mail("m2", "hr@hindco.com", "Your application: next steps", "HINDCO would like a resume.", 90));
        answer = (m, c) -> new Verdict(0, Outcome.IN_TALKS, "", "HINDCO", "", "");

        sync.syncAccount(account);

        assertThat(jobs.findAll()).hasSize(1);
        assertThat(jobs.findAll().get(0).getRole()).isEqualTo("Role not mentioned");
        assertThat(jobs.findAll().get(0).getStatus()).isEqualTo(JobStatus.IN_REVIEW);
    }

    @Test
    void jobAlertsAndOrdinaryMailAreNeverAskedAbout() {
        inbox.add(mail("m1", "jobs@site.com", "New jobs for you: Backend Engineer", "Your application could stand out. Apply to interview-ready roles.", 100));
        inbox.add(mail("m2", "friend@x.com", "Lunch?", "Are you free on Friday?", 90));

        sync.syncAccount(account);

        assertThat(asked).isEmpty();
        assertThat(jobs.count()).isZero();
    }

    @Test
    void aSyncFromADateStopsAfter100EmailsAndAsksToReadMore() {
        for (int i = 1; i <= 130; i++) inbox.add(mail("m" + i, "hr@x.com", "Interview " + i, "We would like to schedule an interview.", i));

        sync.syncOne(account.getId(), Instant.now().minusSeconds(30L * 86400));
        assertThat(asked).hasSize(100);
        assertThat(sync.lastRun().more).isTrue();

        asked.clear();
        sync.syncOne(account.getId(), Instant.now().minusSeconds(30L * 86400)); // "read more": the rest, nothing twice
        assertThat(asked).hasSize(30);
        assertThat(sync.lastRun().more).isFalse();
    }

    @Test
    void theTransitionRulesNeverGoBackwards() {
        assertThat(MailSyncService.next(JobStatus.APPLIED, Outcome.REJECTED)).isEqualTo(JobStatus.REJECTED);
        assertThat(MailSyncService.next(JobStatus.REJECTED, Outcome.REJECTED)).isNull();
        assertThat(MailSyncService.next(JobStatus.REJECTED, Outcome.IN_TALKS)).isNull();
        assertThat(MailSyncService.next(JobStatus.TO_APPLY, Outcome.IN_TALKS)).isEqualTo(JobStatus.IN_REVIEW);
        assertThat(MailSyncService.next(JobStatus.TO_APPLY, Outcome.APPLIED)).isEqualTo(JobStatus.APPLIED);
        assertThat(MailSyncService.next(JobStatus.APPLIED, Outcome.APPLIED)).isNull();
        assertThat(MailSyncService.next(JobStatus.IN_REVIEW, Outcome.APPLIED)).isNull();
        assertThat(MailSyncService.next(JobStatus.APPLIED, Outcome.NONE)).isNull();
    }
}

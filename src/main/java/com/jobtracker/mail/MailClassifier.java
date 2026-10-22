package com.jobtracker.mail;

import com.jobtracker.job.Job;
import java.util.List;

/** Decides what one email says about one of your applications. Swappable: Claude today. */
public interface MailClassifier {

    enum Outcome {
        /** They turned the application down. */
        REJECTED,
        /** An interview, a call, an assessment, a shortlist or an offer: it is moving forward. */
        IN_TALKS,
        /** They only confirm the application was received. */
        APPLIED,
        /** Nothing about an application, or not clearly about one of these jobs. */
        NONE
    }

    /** @param jobId which of the offered jobs it is about; 0 when none. With 0, company and role say which application it is about, so it can be added. */
    /** One more application named in the same email (a summary such as "You applied for 44 jobs" lists many). */
    record Application(String company, String role, String postingId) {}

    record Verdict(long jobId, Outcome outcome, String reason, String company, String role, String postingId, List<Application> others) {
        public Verdict(long jobId, Outcome outcome, String reason, String company, String role, String postingId) {
            this(jobId, outcome, reason, company, role, postingId, List.of());
        }

        /** A verdict about a job that is already tracked: nothing new to learn about the company or role. */
        public Verdict(long jobId, Outcome outcome, String reason) {
            this(jobId, outcome, reason, "", "", "");
        }

        static Verdict none(String reason) {
            return new Verdict(0, Outcome.NONE, reason);
        }
    }

    Verdict classify(MailMessage message, List<Job> candidates) throws ClassifyException;

    /** The email could not be judged. The message is written for the user to see. */
    class ClassifyException extends Exception {
        public ClassifyException(String message, Throwable cause) {
            super(message, cause);
        }

        public ClassifyException(String message) {
            super(message);
        }
    }
}

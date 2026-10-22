package com.jobtracker.mail;

/** Checks that an email and app password really open the mailbox. Swappable so tests need no Gmail. */
public interface MailVerifier {

    void verify(String email, String appPassword) throws MailException;

    /** The mailbox could not be opened. The message is written for the user to see. */
    class MailException extends Exception {
        public MailException(String message) {
            super(message);
        }
    }
}

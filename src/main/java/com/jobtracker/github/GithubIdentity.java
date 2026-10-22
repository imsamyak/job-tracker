package com.jobtracker.github;

/** Asks GitHub whose token this is. Swappable so tests need no GitHub. */
public interface GithubIdentity {

    /** The GitHub username the token belongs to. */
    String loginFor(String token) throws TokenException;

    /** The token was not accepted. The message is written for the user to see. */
    class TokenException extends Exception {
        public TokenException(String message) {
            super(message);
        }
    }
}

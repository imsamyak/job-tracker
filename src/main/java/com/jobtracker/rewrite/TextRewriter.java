package com.jobtracker.rewrite;

/** Rewrites a piece of your profile text. Swappable: Claude today. */
public interface TextRewriter {

    enum Kind { ABOUT, EXPERIENCE, PROJECT, SKILLS }

    /**
     * @param context  what the text is about (the role and company, or the project and its stack), to keep the rewrite on topic
     * @param instruction  your own request, such as "make it shorter" or "focus on backend work"; may be empty
     */
    String rewrite(Kind kind, String text, String context, String instruction) throws RewriteException;

    /** The text could not be rewritten. The message is written for the user to see. */
    class RewriteException extends Exception {
        public RewriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

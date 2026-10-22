package com.jobtracker.resume;

/** Turns the text of a resume into profile fields. Swappable: Claude today, another model later. */
public interface ProfileExtractor {

    ExtractedProfile extract(String resumeText) throws ProfileExtractionException;

    /** The resume could not be read. The message is written for the user to see. */
    class ProfileExtractionException extends Exception {
        public ProfileExtractionException(String message) {
            super(message);
        }

        public ProfileExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.jobtracker.intake;

import java.nio.file.Path;

/** Turns a job description into structured fields. Swappable: Claude today, another model later. */
public interface JdExtractor {

    /** Reads the posting from {@code text}, or, when {@code image} is given, from that screenshot. */
    ExtractedJd extract(String text, Path image) throws ExtractionException;
}

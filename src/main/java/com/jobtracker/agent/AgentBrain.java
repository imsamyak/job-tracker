package com.jobtracker.agent;

import java.util.List;

/** Decides what to say and what to do. Swappable: Claude today, a fake in tests. */
public interface AgentBrain {

    /** One thing it wants done. Only the fields that belong to its type are used. */
    record Step(String type, long jobId, String status, String text, String route) {}

    record Plan(String reply, List<Step> steps) {}

    Plan think(String prompt) throws AgentException;

    /** The assistant could not answer. The message is written for the user to see. */
    class AgentException extends Exception {
        public AgentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

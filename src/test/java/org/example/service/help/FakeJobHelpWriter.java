package org.example.service.help;

import java.util.function.Function;

/** Returns a canned answer (or throws) instead of shelling out to Claude. */
public class FakeJobHelpWriter implements JobHelpWriter {

    private final Function<JobHelpRequest, String> answer;

    public FakeJobHelpWriter(String fixedAnswer) {
        this(r -> fixedAnswer);
    }

    public FakeJobHelpWriter(Function<JobHelpRequest, String> answer) {
        this.answer = answer;
    }

    public static FakeJobHelpWriter failing(String message) {
        return new FakeJobHelpWriter(r -> {
            throw new JobHelpWriteException(message);
        });
    }

    @Override
    public String answer(JobHelpRequest request) {
        return answer.apply(request);
    }
}

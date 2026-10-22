package com.jobtracker.project;

import java.util.List;

/** What GitHub says about one public repository. */
public record GithubRepo(String owner, String name, String url, String description, List<String> topics,
                         List<String> languages, String readme) {}

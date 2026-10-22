package com.jobtracker.project;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import com.jobtracker.ai.ClaudeCli;
import com.jobtracker.intake.ExtractionException;
import com.jobtracker.intake.LinkFetcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Reads a link to a project into the fields of a profile project. Nothing is stored here: the form shows it for review. */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    public record FromLink(String url) {}

    private final GithubFetcher github;
    private final LinkFetcher pages;
    private final ProjectReader reader;

    public ProjectController(GithubFetcher github, LinkFetcher pages, ProjectReader reader) {
        this.github = github;
        this.pages = pages;
        this.reader = reader;
    }

    /** A GitHub repository link is read through GitHub; any other link is read as a web page. */
    @PostMapping("/from-link")
    public ProjectReader.ProjectDraft fromLink(@RequestBody FromLink body) {
        String url = body.url() == null ? "" : body.url().strip();
        try {
            if (GithubFetcher.parse(url) != null) return reader.read(github.fetch(url));
            LinkFetcher.Page page = pages.fetch(url);
            return reader.readPage(page.url(), page.text());
        } catch (ExtractionException e) {
            // the page reader's advice is about job posts; here the form can simply be filled by hand
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY,
                    e.getMessage().replace(" Paste the text or add a screenshot instead.", " You can fill the details in yourself."));
        } catch (ClaudeCli.AiException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Claude could not read the project: " + e.getMessage());
        }
    }
}

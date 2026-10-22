package com.jobtracker.project;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import com.jobtracker.github.GithubAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a public GitHub repository through GitHub's API. Only github.com repository links are accepted and
 * only api.github.com is ever called, so a pasted link cannot make the server fetch anything else.
 */
@Component
public class GithubFetcher {

    static final int MAX_README_CHARS = 12_000;
    private static final Pattern REPO = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9._-]{1,100}?)(?:\\.git)?(?:[/?#].*)?$");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String api;
    private final Supplier<String> token;

    /** Uses the connected GitHub account's token, if there is one: it lifts the rate limit and reaches your private repositories. */
    @Autowired
    public GithubFetcher(GithubAccountService account) {
        this("https://api.github.com", account::token);
    }

    public GithubFetcher() {
        this("https://api.github.com", () -> null);
    }

    /** The API address and token can be changed so tests do not call the real GitHub. */
    GithubFetcher(String api, Supplier<String> token) {
        this.api = api;
        this.token = token;
    }

    /** {owner, repo} from a link, or null when it is not a repository link. */
    static String[] parse(String link) {
        if (link == null) return null;
        Matcher m = REPO.matcher(link.strip());
        return m.matches() ? new String[]{m.group(1), m.group(2)} : null;
    }

    public GithubRepo fetch(String link) {
        String[] r = parse(link);
        if (r == null) throw new ResponseStatusException(BAD_REQUEST, "That is not a GitHub repository link (github.com/owner/repo).");
        String base = api + "/repos/" + r[0] + "/" + r[1];

        JsonNode info = readJson(base);
        List<String> topics = new ArrayList<>();
        for (JsonNode t : info.path("topics")) topics.add(t.asText());

        // languages by amount of code, largest first
        List<String> languages = new ArrayList<>();
        try {
            JsonNode langs = readJson(base + "/languages");
            List<String> names = new ArrayList<>();
            langs.properties().forEach(e -> names.add(e.getKey()));
            names.sort(Comparator.comparingLong((String n) -> langs.path(n).asLong()).reversed());
            languages.addAll(names.stream().limit(6).toList());
        } catch (ResponseStatusException ignored) {
            // the languages are a nicety: the project can still be read without them
        }

        String readme = "";
        try {
            readme = readText(base + "/readme");
            if (readme.length() > MAX_README_CHARS) readme = readme.substring(0, MAX_README_CHARS);
        } catch (ResponseStatusException ignored) {
            // no README: the description alone is used
        }

        String desc = info.path("description").asText("");
        return new GithubRepo(r[0], info.path("name").asText(r[1]), info.path("html_url").asText("https://github.com/" + r[0] + "/" + r[1]),
                desc, topics, languages, readme);
    }

    private JsonNode readJson(String url) {
        return JSON.readTree(send(url, "application/vnd.github+json"));
    }

    private String readText(String url) {
        return send(url, "application/vnd.github.raw+json");
    }

    private String send(String url, String accept) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Accept", accept).header("User-Agent", "JobTracker");
            String t = token.get();
            if (t != null) b.header("Authorization", "Bearer " + t);
            HttpRequest req = b.GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 404) throw new ResponseStatusException(NOT_FOUND, "That repository was not found. Without a connected GitHub account only public repositories can be read.");
            if (code == 403 || code == 429) throw new ResponseStatusException(BAD_GATEWAY, "GitHub is limiting requests right now. Try again in a little while.");
            if (code / 100 != 2) throw new ResponseStatusException(BAD_GATEWAY, "GitHub answered " + code + ".");
            return res.body();
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Could not reach GitHub: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Reading the repository was interrupted.");
        }
    }
}

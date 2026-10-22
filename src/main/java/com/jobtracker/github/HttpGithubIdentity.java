package com.jobtracker.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Calls GET /user on api.github.com with the token; GitHub answers with the account's username. */
@Component
public class HttpGithubIdentity implements GithubIdentity {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String loginFor(String token) throws TokenException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/user")).timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "JobTracker")
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                throw new TokenException("GitHub did not accept that token. Check it was copied whole and has not expired.");
            }
            if (res.statusCode() / 100 != 2) throw new TokenException("GitHub answered " + res.statusCode() + ".");
            String login = JSON.readTree(res.body()).path("login").asText("");
            if (login.isEmpty()) throw new TokenException("GitHub did not say whose token this is.");
            return login;
        } catch (IOException e) {
            throw new TokenException("Could not reach GitHub: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenException("Checking the token was interrupted.");
        }
    }
}

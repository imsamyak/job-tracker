package com.jobtracker.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class GithubAccountTest {

    static boolean refuse;

    @TestConfiguration
    static class Fake {
        @Bean
        @Primary
        GithubIdentity identity() {
            return token -> {
                if (refuse) throw new GithubIdentity.TokenException("GitHub did not accept that token.");
                return "octocat";
            };
        }
    }

    @Autowired GithubAccountController controller;
    @Autowired GithubAccountService service;
    @Autowired GithubAccountRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        refuse = false;
    }

    @Test
    void startsDisconnected() {
        assertThat(controller.status().connected()).isFalse();
        assertThat(service.token()).isNull();
    }

    @Test
    void connectKeepsTheTokenLockedAndHandsItBackToTheApp() {
        var status = controller.connect(new GithubAccountController.Token("  ghp_secret123 "));
        assertThat(status.connected()).isTrue();
        assertThat(status.login()).isEqualTo("octocat");
        assertThat(status.toString()).doesNotContain("secret123");
        assertThat(repo.findById(1L).orElseThrow().getLockedToken()).doesNotContain("secret123");
        assertThat(service.token()).isEqualTo("ghp_secret123");
    }

    @Test
    void aRefusedTokenIsNotSaved() {
        refuse = true;
        assertThatThrownBy(() -> controller.connect(new GithubAccountController.Token("bad")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("did not accept");
        assertThat(controller.status().connected()).isFalse();
    }

    @Test
    void emptyTokenIsRejected() {
        assertThatThrownBy(() -> controller.connect(new GithubAccountController.Token("  "))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disconnectForgetsTheToken() {
        controller.connect(new GithubAccountController.Token("t"));
        controller.disconnect();
        assertThat(controller.status().connected()).isFalse();
        assertThat(service.token()).isNull();
    }
}

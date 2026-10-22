package com.jobtracker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaudeAccountTest {

    @Test
    void anApiKeyLoginIsReportedAsBilledPerUse() {
        var s = ClaudeAccount.parse("{\"loggedIn\":true,\"authMethod\":\"api_key\",\"email\":\"me@x.com\"}");
        assertThat(s.loggedIn()).isTrue();
        assertThat(s.method()).isEqualTo("api");
        assertThat(s.email()).isEqualTo("me@x.com");
    }

    @Test
    void anyOtherLoginIsASubscription() {
        var s = ClaudeAccount.parse("{\"loggedIn\":true,\"authMethod\":\"claude.ai\"}");
        assertThat(s.method()).isEqualTo("subscription");
        assertThat(s.email()).isNull();
    }

    @Test
    void signedOutOrUnreadableMeansNotLoggedIn() {
        assertThat(ClaudeAccount.parse("{\"loggedIn\":false}").loggedIn()).isFalse();
        assertThat(ClaudeAccount.parse("not json").loggedIn()).isFalse();
        assertThat(ClaudeAccount.parse("").method()).isNull();
    }
}

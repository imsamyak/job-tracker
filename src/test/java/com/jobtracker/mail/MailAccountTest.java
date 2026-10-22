package com.jobtracker.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtracker.secret.SecretBox;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class MailAccountTest {

    /** What the fake Gmail was asked, and whether it should refuse. */
    static final List<String> asked = new ArrayList<>();
    static boolean refuse;

    @TestConfiguration
    static class Fake {
        @Bean
        @Primary
        MailVerifier verifier() {
            return (email, password) -> {
                asked.add(email + "/" + password);
                if (refuse) throw new MailVerifier.MailException("Gmail did not accept that.");
            };
        }
    }

    @Autowired MailAccountController controller;
    @Autowired MailAccountRepository repo;
    @Autowired SecretBox box;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        asked.clear();
        refuse = false;
    }

    @Test
    void addsAnAccountAndNeverReturnsOrStoresThePasswordAsText() {
        var view = controller.add(new MailAccountController.NewAccount("  Me@Gmail.com ", "abcd efgh ijkl mnop"));
        assertThat(view.email()).isEqualTo("me@gmail.com");
        assertThat(asked).containsExactly("me@gmail.com/abcdefghijklmnop"); // spaces dropped

        String stored = repo.findByEmail("me@gmail.com").orElseThrow().getLockedPassword();
        assertThat(stored).doesNotContain("abcdefgh");
        assertThat(box.unlock(stored)).isEqualTo("abcdefghijklmnop");
        assertThat(view.toString()).doesNotContain("abcdefgh");
    }

    @Test
    void severalAccountsCanBeConnected() {
        controller.add(new MailAccountController.NewAccount("a@gmail.com", "aaaaaaaaaaaaaaaa"));
        controller.add(new MailAccountController.NewAccount("b@gmail.com", "bbbbbbbbbbbbbbbb"));
        assertThat(controller.list()).extracting(MailAccountController.AccountView::email).containsExactly("a@gmail.com", "b@gmail.com");
    }

    @Test
    void addingTheSameAddressAgainReplacesItsPassword() {
        controller.add(new MailAccountController.NewAccount("a@gmail.com", "aaaaaaaaaaaaaaaa"));
        controller.add(new MailAccountController.NewAccount("A@gmail.com", "cccccccccccccccc"));
        assertThat(controller.list()).hasSize(1);
        assertThat(box.unlock(repo.findByEmail("a@gmail.com").orElseThrow().getLockedPassword())).isEqualTo("cccccccccccccccc");
    }

    @Test
    void aRefusedLoginIsNotSaved() {
        refuse = true;
        assertThatThrownBy(() -> controller.add(new MailAccountController.NewAccount("a@gmail.com", "wrong")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("did not accept");
        assertThat(controller.list()).isEmpty();
    }

    @Test
    void badInputIsRejectedBeforeAskingGmail() {
        assertThatThrownBy(() -> controller.add(new MailAccountController.NewAccount("nope", "x"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.add(new MailAccountController.NewAccount("a@gmail.com", "  "))).isInstanceOf(ResponseStatusException.class);
        assertThat(asked).isEmpty();
    }

    @Test
    void removeDeletesTheAccount() {
        var v = controller.add(new MailAccountController.NewAccount("a@gmail.com", "aaaaaaaaaaaaaaaa"));
        controller.remove(v.id());
        assertThat(controller.list()).isEmpty();
    }

    @Test
    void secretBoxRoundTripsAndNeverRepeatsItself() {
        String a = box.lock("hello");
        assertThat(a).isNotEqualTo(box.lock("hello"));
        assertThat(box.unlock(a)).isEqualTo("hello");
    }
}

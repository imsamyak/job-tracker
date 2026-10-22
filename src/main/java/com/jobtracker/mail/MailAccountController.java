package com.jobtracker.mail;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.jobtracker.secret.SecretBox;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The Gmail accounts you have connected. The password goes in once and is never sent back out. */
@RestController
@RequestMapping("/api/mail-accounts")
public class MailAccountController {

    public record AccountView(Long id, String email, Instant addedAt, Instant lastSyncedAt, String lastError) {
        static AccountView of(MailAccount a) {
            return new AccountView(a.getId(), a.getEmail(), a.getAddedAt(), a.getLastSyncedAt(), a.getLastError());
        }
    }

    public record NewAccount(String email, String appPassword) {}

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MailAccountRepository accounts;
    private final MailVerifier verifier;
    private final SecretBox box;

    public MailAccountController(MailAccountRepository accounts, MailVerifier verifier, SecretBox box) {
        this.accounts = accounts;
        this.verifier = verifier;
        this.box = box;
    }

    @GetMapping
    public List<AccountView> list() {
        return accounts.findAllByOrderByAddedAtAsc().stream().map(AccountView::of).toList();
    }

    /** Checks the mailbox opens, then keeps the account. Adding an address already there just updates its password. */
    @PostMapping
    public AccountView add(@RequestBody NewAccount body) {
        String email = body.email() == null ? "" : body.email().strip().toLowerCase(Locale.ROOT);
        // Google shows app passwords in four groups of four letters; the spaces are not part of it
        String password = body.appPassword() == null ? "" : body.appPassword().replaceAll("\\s+", "");
        if (!EMAIL.matcher(email).matches()) throw new ResponseStatusException(BAD_REQUEST, "Enter the Gmail address.");
        if (password.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Enter the app password.");

        try {
            verifier.verify(email, password);
        } catch (MailVerifier.MailException e) {
            throw new ResponseStatusException(BAD_GATEWAY, e.getMessage());
        }

        MailAccount a = accounts.findByEmail(email).orElseGet(MailAccount::new);
        a.setEmail(email);
        a.setLockedPassword(box.lock(password));
        return AccountView.of(accounts.save(a));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        accounts.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

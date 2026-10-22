package com.jobtracker.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import java.util.Properties;
import org.springframework.stereotype.Component;

/** Opens the mailbox on Gmail's IMAP server, which is the whole test. */
@Component
public class ImapMailVerifier implements MailVerifier {

    static final String HOST = "imap.gmail.com";

    @Override
    public void verify(String email, String appPassword) throws MailException {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "10000");
        p.put("mail.imaps.timeout", "15000");
        try (Store store = Session.getInstance(p).getStore("imaps")) {
            store.connect(HOST, 993, email, appPassword);
        } catch (AuthenticationFailedException e) {
            throw new MailException("Gmail did not accept that email and app password. Use a 16-letter app password, not your normal password, and check IMAP is on in Gmail settings.");
        } catch (MessagingException e) {
            throw new MailException("Could not reach Gmail: " + e.getMessage());
        }
    }
}

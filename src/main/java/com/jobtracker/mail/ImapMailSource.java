package com.jobtracker.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.eclipse.angus.mail.imap.IMAPFolder;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reads the inbox over IMAP, read-only: nothing is marked as read, moved or deleted. */
@Component
public class ImapMailSource implements MailSource {

    private static final Logger log = LoggerFactory.getLogger(ImapMailSource.class);
    static final int MAX_TEXT_CHARS = 20_000;
    /** Emails downloaded per request: one round trip per batch, not one per email, which is what makes a year of mail quick. */
    static final int BATCH = 40;

    @Override
    public List<MailMessage> fetchSince(String email, String appPassword, Instant since, int max, Progress progress) throws MailVerifier.MailException {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "10000");
        p.put("mail.imaps.timeout", "30000");
        p.put("mail.imaps.peek", "true"); // reading a message must not mark it as read

        try (Store store = Session.getInstance(p).getStore("imaps")) {
            store.connect(ImapMailVerifier.HOST, 993, email, appPassword);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                Message[] found = inbox.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(since)));
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                inbox.fetch(found, fp);

                // the newest `max` by date, then oldest first so that a later email can override an earlier one
                List<Message> chosen = new ArrayList<>(Arrays.asList(found));
                chosen.sort(Comparator.comparing(ImapMailSource::receivedOf).reversed());
                if (chosen.size() > max) chosen = chosen.subList(0, max);
                chosen.sort(Comparator.comparing(ImapMailSource::receivedOf));

                List<MailMessage> out = new ArrayList<>();
                int total = chosen.size();
                progress.update(0, total);
                for (int from = 0; from < total; from += BATCH) {
                    Message[] batch = chosen.subList(from, Math.min(from + BATCH, total)).toArray(new Message[0]);
                    try {
                        // the whole batch in one request; the folder is open read-only, so nothing is marked as read
                        FetchProfile whole = new FetchProfile();
                        whole.add(IMAPFolder.FetchProfileItem.MESSAGE);
                        inbox.fetch(batch, whole);
                    } catch (MessagingException e) {
                        log.debug("Batch download failed, reading its messages one by one: {}", e.toString());
                    }
                    for (Message m : batch) {
                        try {
                            out.add(toMessage(email, (MimeMessage) m));
                        } catch (MessagingException | IOException | RuntimeException e) {
                            log.debug("Skipping one unreadable message: {}", e.toString());
                        }
                    }
                    progress.update(Math.min(from + BATCH, total), total);
                }
                return out;
            } finally {
                if (inbox.isOpen()) inbox.close(false);
            }
        } catch (AuthenticationFailedException e) {
            throw new MailVerifier.MailException("Gmail no longer accepts the app password for " + email + ". Connect the account again.");
        } catch (MessagingException e) {
            throw new MailVerifier.MailException("Could not read " + email + ": " + e.getMessage());
        }
    }

    private static Instant receivedOf(Message m) {
        try {
            Date d = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
            return d == null ? Instant.EPOCH : d.toInstant();
        } catch (MessagingException e) {
            return Instant.EPOCH;
        }
    }

    private static MailMessage toMessage(String mailbox, MimeMessage m) throws MessagingException, IOException {
        String id = m.getMessageID();
        Instant at = receivedOf(m);
        if (id == null || id.isBlank()) id = mailbox + "|" + at.toEpochMilli() + "|" + m.getSubject();
        String from = m.getFrom() == null || m.getFrom().length == 0 ? "" : m.getFrom()[0] instanceof InternetAddress ia ? ia.toUnicodeString() : m.getFrom()[0].toString();
        String subject = m.getSubject() == null ? "" : m.getSubject();
        return new MailMessage(id, from, subject, at, textOf(m));
    }

    /** The readable text of a message: the plain part if there is one, else the HTML part with its tags removed. */
    static String textOf(Part part) throws MessagingException, IOException {
        String plain = find(part, "text/plain");
        String text = plain != null && !plain.isBlank() ? plain : null;
        if (text == null) {
            String html = find(part, "text/html");
            text = html == null ? "" : Jsoup.parse(html).text();
        }
        text = text.strip();
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    private static String find(Part part, String mime) throws MessagingException, IOException {
        if (part.isMimeType(mime) && !Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            Object c = part.getContent();
            return c instanceof String s ? s : null;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = find(mp.getBodyPart(i), mime);
                if (s != null) return s;
            }
        }
        return null;
    }
}

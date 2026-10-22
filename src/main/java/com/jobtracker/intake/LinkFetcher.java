package com.jobtracker.intake;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Downloads a job page and turns it into plain text. A pasted link is untrusted input, so before
 * every request (and every redirect) the address is checked: only http(s), and never an address that
 * points at this computer or the local network.
 */
@Component
public class LinkFetcher {

    public record Page(String url, String text, String platform) {}

    static final int MIN_TEXT_CHARS = 200;
    private static final int MAX_TEXT_CHARS = 25_000;
    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    public Page fetch(String link) throws ExtractionException {
        URI uri = validate(link);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Connection.Response res;
            try {
                res = Jsoup.connect(uri.toString())
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "en")
                        .timeout(15_000)
                        .maxBodySize(2_000_000)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();
            } catch (IOException e) {
                throw new ExtractionException("Could not open that link (" + e.getMessage() + "). Paste the text or add a screenshot instead.", e);
            }
            int code = res.statusCode();
            if (code >= 300 && code < 400 && res.hasHeader("Location")) {
                uri = validate(uri.resolve(res.header("Location")).toString());
                continue;
            }
            if (code >= 400) {
                throw new ExtractionException("That site did not let the app read the page (HTTP " + code
                        + "). Paste the text or add a screenshot instead.");
            }
            Document doc;
            try {
                doc = res.parse();
            } catch (IOException e) {
                throw new ExtractionException("That link did not return a web page. Paste the text or add a screenshot instead.", e);
            }
            return new Page(uri.toString(), pageText(doc), platformOf(uri.getHost()));
        }
        throw new ExtractionException("That link redirects too many times.");
    }

    /** Package-private for tests. */
    static String pageText(Document doc) throws ExtractionException {
        StringBuilder sb = new StringBuilder();
        // Many job sites describe the posting as structured data in the page; it is the cleanest source.
        for (Element ld : doc.select("script[type=application/ld+json]")) {
            String json = ld.data();
            if (json.contains("JobPosting")) {
                sb.append("Structured data from the page:\n").append(json.length() > 8000 ? json.substring(0, 8000) : json).append("\n\n");
            }
        }
        boolean hasStructured = sb.length() > 0;
        doc.select("script, style, noscript, svg, iframe, nav, footer").remove();
        String body = doc.body() == null ? "" : doc.body().text().strip();
        if (body.length() < MIN_TEXT_CHARS && !hasStructured) {
            throw new ExtractionException("The page had no readable job text (it may need a login or load with JavaScript). "
                    + "Paste the text or add a screenshot instead.");
        }
        String title = doc.title() == null ? "" : doc.title().strip();
        sb.append("Page title: ").append(title).append("\n\n").append(body);
        return sb.length() > MAX_TEXT_CHARS ? sb.substring(0, MAX_TEXT_CHARS) : sb.toString();
    }

    /** Only http(s) to a public address. Package-private for tests. */
    static URI validate(String link) throws ExtractionException {
        URI uri;
        try {
            uri = new URI(link.strip());
        } catch (URISyntaxException e) {
            throw new ExtractionException("That does not look like a valid link.", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ExtractionException("Only http and https links can be read.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new ExtractionException("That does not look like a valid link.");
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()
                        || a.isMulticastAddress() || isUniqueLocalV6(a)) {
                    throw new ExtractionException("That link points to a private address, so it will not be opened.");
                }
            }
        } catch (UnknownHostException e) {
            throw new ExtractionException("Could not find that website (" + host + ").", e);
        }
        return uri;
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC; // fc00::/7
    }

    static String platformOf(String host) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (h.endsWith("linkedin.com")) return "LinkedIn";
        if (h.endsWith("naukri.com")) return "Naukri";
        if (h.endsWith("indeed.com")) return "Indeed";
        if (h.endsWith("myworkdayjobs.com") || h.endsWith("myworkday.com")) return "Workday";
        if (h.endsWith("lever.co")) return "Lever";
        if (h.endsWith("greenhouse.io")) return "Greenhouse";
        if (h.endsWith("wellfound.com") || h.endsWith("angel.co")) return "Wellfound";
        if (h.endsWith("glassdoor.com")) return "Glassdoor";
        if (h.endsWith("hirist.tech") || h.endsWith("hirist.com")) return "Hirist";
        if (h.endsWith("instahyre.com")) return "Instahyre";
        if (h.endsWith("foundit.in") || h.endsWith("monster.com")) return "Foundit";
        return null; // an employer's own site: the extractor decides
    }
}

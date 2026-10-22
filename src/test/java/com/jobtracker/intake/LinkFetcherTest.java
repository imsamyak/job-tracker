package com.jobtracker.intake;

import static org.junit.jupiter.api.Assertions.*;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class LinkFetcherTest {

    @Test
    void refusesAnythingThatIsNotAPublicWebAddress() {
        for (String bad : new String[] {
                "file:///C:/Windows/win.ini", "ftp://example.com/x", "javascript:alert(1)",
                "http://localhost:8080/api/jobs", "http://127.0.0.1/", "http://0.0.0.0/", "http://[::1]/",
                "http://10.0.0.5/admin", "http://192.168.1.1/", "http://172.16.0.9/", "http://169.254.169.254/latest/meta-data",
                "not a link", "http://"}) {
            assertThrows(ExtractionException.class, () -> LinkFetcher.validate(bad), bad);
        }
    }

    @Test
    void acceptsAPublicAddress() throws Exception {
        assertEquals("https://8.8.8.8/jobs/1", LinkFetcher.validate("https://8.8.8.8/jobs/1").toString());
    }

    @Test
    void pageTextKeepsStructuredJobDataAndDropsPageFurniture() throws Exception {
        String html = """
                <html><head><title>Backend Engineer - Acme</title>
                <script type="application/ld+json">{"@type":"JobPosting","title":"Backend Engineer","hiringOrganization":{"name":"Acme"}}</script>
                <script>var tracking = 1;</script></head>
                <body><nav>Home Jobs Companies</nav><h1>Backend Engineer</h1>
                <p>We are hiring a backend engineer to build services in Java and Spring Boot. You will work with a small team,
                own features end to end, and help us scale to millions of users across several regions of the world.</p>
                <footer>Cookie settings</footer></body></html>""";
        String text = LinkFetcher.pageText(Jsoup.parse(html));
        assertTrue(text.contains("\"hiringOrganization\""));
        assertTrue(text.contains("Page title: Backend Engineer - Acme"));
        assertTrue(text.contains("build services in Java"));
        assertFalse(text.contains("Cookie settings"));
        assertFalse(text.contains("var tracking"));
    }

    @Test
    void aPageWithNothingReadableTellsTheUserWhatToDoInstead() {
        var e = assertThrows(ExtractionException.class,
                () -> LinkFetcher.pageText(Jsoup.parse("<html><body><div id='root'></div><noscript>Enable JS</noscript></body></html>")));
        assertTrue(e.getMessage().contains("Paste the text or add a screenshot"));
    }

    @Test
    void knowsTheBigJobBoards() {
        assertEquals("LinkedIn", LinkFetcher.platformOf("www.linkedin.com"));
        assertEquals("Naukri", LinkFetcher.platformOf("www.naukri.com"));
        assertEquals("Workday", LinkFetcher.platformOf("acme.wd5.myworkdayjobs.com"));
        assertNull(LinkFetcher.platformOf("careers.acme.com"));
    }
}

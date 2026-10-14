package com.pulse.bluetoothdisable.privacy;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.CookieJar;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrivacyFetcherTest {
    private static final String DOCUMENT = PrivacyUrlPolicy.documentForLanguage("ru");
    private static final String LEGACY_DOCUMENT = DOCUMENT + ".html";
    private static final String CSS = "https://bluetoothdisable.app/assets/css/main.css";

    @Test public void validRedirectIsCheckedAndResponsesAreClosed() throws Exception {
        FakePrivacyTransport transport = new FakePrivacyTransport();
        FakePrivacyTransport.FakeReply first = transport.add(LEGACY_DOCUMENT, 301, "text/html", "");
        first.headers.put("Location", "/privacy/ru/privacy");
        FakePrivacyTransport.FakeReply last = transport.add(DOCUMENT, 200, "text/html; charset=UTF-8", "policy");
        PrivacyFetcher.Payload result = new PrivacyFetcher(transport).get(LEGACY_DOCUMENT,
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget());
        assertEquals("policy", result.text());
        assertEquals(2, transport.requests.size());
        assertTrue(first.closed); assertTrue(last.closed);
    }

    @Test public void everyRedirectHopIsCheckedBeforeAnyRequest() {
        FakePrivacyTransport transport = new FakePrivacyTransport();
        transport.add(LEGACY_DOCUMENT, 302, "text/html", "").headers.put("Location", "/privacy/ru/privacy");
        transport.add(DOCUMENT, 302, "text/html", "").headers.put("Location", "https://evil.example/steal");
        assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(LEGACY_DOCUMENT,
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
        assertEquals(Arrays.asList(LEGACY_DOCUMENT, DOCUMENT), transport.requests);
    }

    @Test public void stylesheetRedirectsCannotReachHttpExternalOrDocumentUrls() {
        for (String target : new String[] {"http://bluetoothdisable.app/assets/css/main.css",
                "https://evil.example/style.css", DOCUMENT, "https://bluetoothdisable.app/favicon.svg"}) {
            FakePrivacyTransport transport = new FakePrivacyTransport();
            transport.add(CSS, 302, "text/css", "").headers.put("Location", target);
            assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(CSS,
                    PrivacyUrlPolicy.Kind.STYLESHEET, new PrivacyFetcher.Budget()));
            assertEquals(Arrays.asList(CSS), transport.requests);
        }
    }

    @Test public void malformedInitialUrlNeverReachesTransport() {
        FakePrivacyTransport transport = new FakePrivacyTransport();
        assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get("https://evil.example/",
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
        assertTrue(transport.requests.isEmpty());
    }

    @Test public void redirectLoopAndMissingLocationFailClosed() {
        FakePrivacyTransport transport = new FakePrivacyTransport();
        transport.add(DOCUMENT, 302, "text/html", "").headers.put("Location", DOCUMENT);
        assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(DOCUMENT,
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
        assertEquals(PrivacyFetcher.MAX_REDIRECTS + 1, transport.requests.size());
        transport.replies.get(DOCUMENT).headers.remove("Location");
        assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(DOCUMENT,
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
    }

    @Test public void errorsAttachmentsAndUnexpectedMimeAreRejected() {
        for (int code : new int[] {401, 403, 404, 500, 204, 206}) {
            FakePrivacyTransport transport = new FakePrivacyTransport();
            transport.add(DOCUMENT, code, "text/html", "policy");
            assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(DOCUMENT,
                    PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
        }
        for (String type : new String[] {"application/javascript", "image/svg+xml", "application/octet-stream", "text/html; charset=ISO-8859-1"}) {
            FakePrivacyTransport transport = new FakePrivacyTransport();
            transport.add(DOCUMENT, 200, type, "policy");
            assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(DOCUMENT,
                    PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
        }
        FakePrivacyTransport transport = new FakePrivacyTransport();
        transport.add(DOCUMENT, 200, "text/html", "policy").headers.put("Content-Disposition", "attachment; filename=payload.apk");
        assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(DOCUMENT,
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
    }

    @Test public void bodyAndTotalLimitsApplyToActualDecodedBytes() {
        FakePrivacyTransport transport = new FakePrivacyTransport();
        transport.add(CSS, 200, "text/css", "").bytes = new byte[PrivacyFetcher.MAX_STYLESHEET_BYTES + 1];
        assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(CSS,
                PrivacyUrlPolicy.Kind.STYLESHEET, new PrivacyFetcher.Budget()));
        transport.add(DOCUMENT, 200, "text/html", "").bytes = new byte[PrivacyFetcher.MAX_DOCUMENT_BYTES + 1];
        assertThrows(IOException.class, () -> new PrivacyFetcher(transport).get(DOCUMENT,
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
        PrivacyFetcher.Budget budget = new PrivacyFetcher.Budget();
        assertThrows(IOException.class, () -> budget.consume(PrivacyFetcher.MAX_TOTAL_BYTES + 1));
    }

    @Test public void tlsFailureIsPropagatedWithoutRetryOrFallback() {
        FakePrivacyTransport transport = new FakePrivacyTransport();
        transport.failure = new SSLHandshakeException("Untrusted certificate");
        assertThrows(SSLHandshakeException.class, () -> new PrivacyFetcher(transport).get(DOCUMENT,
                PrivacyUrlPolicy.Kind.DOCUMENT, new PrivacyFetcher.Budget()));
        assertEquals(Arrays.asList(DOCUMENT), transport.requests);
    }

    @Test public void privateClientCannotUseCookiesAutomaticRedirectsOrCache() throws Exception {
        OkHttpPrivacyTransport transport = new OkHttpPrivacyTransport();
        assertSame(CookieJar.NO_COOKIES, transport.client.cookieJar());
        assertFalse(transport.client.followRedirects());
        assertFalse(transport.client.followSslRedirects());
        assertNull(transport.client.cache());
        assertEquals(15000, transport.client.callTimeoutMillis());
        transport.close();
        assertThrows(IOException.class, () -> transport.get(URI.create(DOCUMENT)));
    }
}

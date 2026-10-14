package com.pulse.bluetoothdisable.privacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrivacyPageLoaderTest {
    private static final String CSS = "https://bluetoothdisable.app/assets/css/themes.css?v=123abc";

    private FakePrivacyTransport page(String language, String extra) {
        FakePrivacyTransport transport = new FakePrivacyTransport();
        transport.add(PrivacyUrlPolicy.documentForLanguage(language), 200, "text/html; charset=utf-8",
                "<html><head><link rel=stylesheet href='../../assets/css/themes.css?v=123abc'>"
                + "<script src='https://evil.example/tracker.js'></script></head><body>"
                + "<main><article class=card><h1>Policy " + language + "</h1><p>Readable text</p>"
                + extra + "</article></main></body></html>");
        transport.add(CSS, 200, "text/css", ":root[data-theme=dark]{color:white;background:black}");
        return transport;
    }

    @Test public void bothLanguagesAndThemesRemainReadableWithoutActiveContent() throws Exception {
        for (String language : new String[] {"ru", "en"}) for (boolean dark : new boolean[] {false, true}) {
            FakePrivacyTransport transport = page(language,
                    "<script>alert(1)</script><iframe src='https://evil.example'></iframe>"
                    + "<form action='https://evil.example'><input name=secret></form>"
                    + "<img src='https://evil.example/pixel'><a href='intent://evil'>Bad</a>"
                    + "<p onclick='alert(1)'>More text</p><a href='../../'>Home</a>");
            try (PrivacyPageLoader loader = new PrivacyPageLoader(transport)) {
                PrivacyPageLoader.Page result = loader.load(PrivacyUrlPolicy.documentForLanguage(language), dark);
                Document rendered = Jsoup.parse(new String(result.resource(result.url, true, "GET").getBytes(), StandardCharsets.UTF_8));
                assertEquals(dark ? "dark" : "light", rendered.selectFirst("html").attr("data-theme"));
                assertEquals(language, rendered.selectFirst("html").attr("lang"));
                assertTrue(rendered.text().contains("Readable text"));
                assertTrue(rendered.select("script,iframe,form,input,img,[onclick],a[href]").isEmpty());
                assertEquals(2, transport.requests.size());
                assertNotNull(result.resource(CSS, false, "GET"));
            }
            assertTrue(transport.closed);
        }
    }

    @Test public void rendererLookupHasNoFallbackForUnknownMethodsFramesOrResources() throws Exception {
        try (PrivacyPageLoader loader = new PrivacyPageLoader(page("ru", ""))) {
            PrivacyPageLoader.Page result = loader.load(PrivacyUrlPolicy.documentForLanguage("ru"), false);
            assertNull(result.resource(result.url, false, "GET"));
            assertNull(result.resource(result.url, true, "POST"));
            assertNull(result.resource("https://evil.example/x", false, "GET"));
            assertNull(result.resource("https://bluetoothdisable.app/assets/css/main.css", false, "GET"));
            assertNull(result.resource("file:///data/private", false, "GET"));
            assertTrue(result.isCurrentDocument(result.url + "#section"));
        }
    }

    @Test public void untrustedCssIsRejectedBeforeAnyExternalFetch() {
        FakePrivacyTransport transport = page("ru", "");
        transport.add(PrivacyUrlPolicy.documentForLanguage("ru"), 200, "text/html",
                "<link rel=stylesheet href='https://evil.example/a.css'><main><h1>Policy</h1></main>");
        assertThrows(IOException.class, () -> new PrivacyPageLoader(transport)
                .load(PrivacyUrlPolicy.documentForLanguage("ru"), false));
        assertEquals(1, transport.requests.size());
    }

    @Test public void missingDocumentAndMalformedUtf8FailClosed() {
        FakePrivacyTransport transport = page("ru", "");
        transport.add(PrivacyUrlPolicy.documentForLanguage("ru"), 200, "text/html", "<h1>Server challenge</h1>");
        assertThrows(IOException.class, () -> new PrivacyPageLoader(transport)
                .load(PrivacyUrlPolicy.documentForLanguage("ru"), false));
        transport.replies.get(PrivacyUrlPolicy.documentForLanguage("ru")).bytes = new byte[] {(byte) 0xc3, (byte) 0x28};
        assertThrows(IOException.class, () -> new PrivacyPageLoader(transport)
                .load(PrivacyUrlPolicy.documentForLanguage("ru"), false));
    }
}

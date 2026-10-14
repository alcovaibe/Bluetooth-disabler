package com.pulse.bluetoothdisable.privacy;

import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrivacyUrlPolicyTest {
    private static final String DOCUMENT = PrivacyUrlPolicy.documentForLanguage("ru");

    @Test public void languagesAndConfirmedCanonicalAliasesAreAllowed() {
        for (String language : new String[] {"ru", "en"}) {
            String url = PrivacyUrlPolicy.documentForLanguage(language);
            assertFalse(url.endsWith(".html"));
            assertEquals(url, PrivacyUrlPolicy.require(url, PrivacyUrlPolicy.Kind.DOCUMENT).toString());
            assertEquals(url + ".html", PrivacyUrlPolicy.require(url + ".html", PrivacyUrlPolicy.Kind.DOCUMENT).toString());
            assertEquals(url, PrivacyUrlPolicy.resolve(URI.create(url + ".html"),
                    "privacy", PrivacyUrlPolicy.Kind.DOCUMENT).toString());
        }
        assertEquals(PrivacyUrlPolicy.documentForLanguage("en"), PrivacyUrlPolicy.documentForLanguage("evil"));
    }

    @Test public void explicit443AndSafeAnchorsUseSameCacheKey() {
        assertEquals(DOCUMENT, PrivacyUrlPolicy.require(DOCUMENT.replace(".app/", ".app:443/")
                + "#retention", PrivacyUrlPolicy.Kind.DOCUMENT).toString());
    }

    @Test public void lookalikeAuthoritiesAndNonHttpsSchemesAreRejected() {
        for (String value : new String[] {
                "https://bluetoothdisable.app.evil.example/privacy/ru/privacy.html",
                "https://evilbluetoothdisable.app/privacy/ru/privacy.html",
                "https://bluetoothdisable.app@evil.example/privacy/ru/privacy.html",
                "https://evil@bluetoothdisable.app/privacy/ru/privacy.html",
                "https://bluetoothdisable.app.:443/privacy/ru/privacy.html",
                "https://sub.bluetoothdisable.app/privacy/ru/privacy.html",
                "https://127.0.0.1/privacy/ru/privacy.html",
                "https://bluetoothdisable.app:8443/privacy/ru/privacy.html",
                "https://bluetoothdisable.app:0443/privacy/ru/privacy.html",
                "http://bluetoothdisable.app/privacy/ru/privacy.html",
                "javascript:alert(1)", "intent://bluetoothdisable.app/", "file:///data/data/secret",
                "content://bluetoothdisable.app/privacy/ru/privacy.html", "data:text/html,hello",
                "//bluetoothdisable.app/privacy/ru/privacy.html"}) {
            assertThrows(value, IllegalArgumentException.class,
                    () -> PrivacyUrlPolicy.require(value, PrivacyUrlPolicy.Kind.DOCUMENT));
        }
    }

    @Test public void ambiguousOrEncodedUrlsAreRejectedBeforeNormalization() {
        for (String value : new String[] {DOCUMENT + "\n", " " + DOCUMENT,
                DOCUMENT.replace(".app/", ".app\\/"), DOCUMENT.replace("/ru/", "/%72u/"),
                DOCUMENT.replace("bluetoothdisable", "bluetoothdіsable"),
                DOCUMENT.replace("/ru/", "/ru/../ru/"), DOCUMENT + "#%61", DOCUMENT + "?v=1",
                DOCUMENT.replace("/privacy/ru/", "//privacy/ru/"), DOCUMENT + "/",
                DOCUMENT.replace("/privacy/ru/", "/api/")}) {
            assertThrows(value, IllegalArgumentException.class,
                    () -> PrivacyUrlPolicy.require(value, PrivacyUrlPolicy.Kind.DOCUMENT));
        }
    }

    @Test public void stylesheetVersionsAreBoundedAndKindsCannotBeConfused() {
        String css = "https://bluetoothdisable.app/assets/css/main.css?v=21bac45afc";
        assertEquals(css, PrivacyUrlPolicy.require(css, PrivacyUrlPolicy.Kind.STYLESHEET).toString());
        for (String suffix : new String[] {"?v=", "?v=1&redirect=https://evil.example", "?v=%31", "#x", "?other=1"}) {
            assertThrows(IllegalArgumentException.class, () -> PrivacyUrlPolicy.require(
                    "https://bluetoothdisable.app/assets/css/main.css" + suffix, PrivacyUrlPolicy.Kind.STYLESHEET));
        }
        assertThrows(IllegalArgumentException.class, () -> PrivacyUrlPolicy.require(css, PrivacyUrlPolicy.Kind.DOCUMENT));
        assertThrows(IllegalArgumentException.class, () -> PrivacyUrlPolicy.require(DOCUMENT, PrivacyUrlPolicy.Kind.STYLESHEET));
        assertThrows(IllegalArgumentException.class, () -> PrivacyUrlPolicy.require(
                "https://bluetoothdisable.app/favicon.svg", PrivacyUrlPolicy.Kind.STYLESHEET));
    }

    @Test public void redirectsAreResolvedThenCheckedAgainstTheSameKind() {
        URI doc = URI.create(DOCUMENT);
        assertEquals("https://bluetoothdisable.app/privacy/ru/privacy",
                PrivacyUrlPolicy.resolve(doc, "/privacy/ru/privacy", PrivacyUrlPolicy.Kind.DOCUMENT).toString());
        for (String target : new String[] {"//evil.example/a", "http://bluetoothdisable.app/privacy/ru/privacy",
                "/api/test-config", "/privacy/ru/%70rivacy", "\\\\evil.example/a", "https://evil.example"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> PrivacyUrlPolicy.resolve(doc, target, PrivacyUrlPolicy.Kind.DOCUMENT));
        }
    }
}

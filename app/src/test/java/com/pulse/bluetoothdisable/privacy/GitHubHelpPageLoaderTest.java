package com.pulse.bluetoothdisable.privacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import static org.junit.Assert.*;

public class GitHubHelpPageLoaderTest {
    private static final String ROOT = "https://github.com/alcovaibe/Bluetooth-disabler";
    private FakePrivacyTransport fixture(String extra) {
        return addDocument(new FakePrivacyTransport(), ROOT, extra);
    }
    private FakePrivacyTransport addDocument(FakePrivacyTransport transport, String url, String extra) {
        transport.add(url, 200, "text/html; charset=utf-8", "<html><head><script src='https://evil.example/x'></script></head>"
                + "<body><form action='/login'>Login</form><article class=markdown-body><h1>Bluetooth Disable</h1>"
                + "<h2>Install</h2><a id=user-content-install href='#install'>Section</a><p>Readable instructions</p>"
                + extra + "</article></body></html>");
        return transport;
    }
    @Test public void bothLanguagesAndThemesRenderOnlyInertRepositoryContent() throws Exception {
        for (String language : new String[] {"ru", "en"}) for (boolean dark : new boolean[] {false, true}) {
            String target = GitHubHelpUrlPolicy.documentForLanguage(language);
            String network = GitHubHelpUrlPolicy.networkDocument(target).toString();
            FakePrivacyTransport transport = addDocument(new FakePrivacyTransport(), network,
                    "<script>alert(1)</script><iframe src='https://evil.example'></iframe><form><input></form>"
                    + "<img src='https://evil.example/tracker'><img src='file:///data/private'>"
                    + "<p onclick='evil()' style='background:url(https://evil.example)'>Text</p>"
                    + "<a href='intent://evil'>Bad intent</a><a href='https://evil.example'>External</a>"
                    + "<a href='/alcovaibe/Bluetooth-disabler/blob/main/docs/QR_PROVISIONING.md'>Details</a>");
            try (GitHubHelpPageLoader loader = new GitHubHelpPageLoader(transport)) {
                GitHubHelpPageLoader.Page page = loader.load(target, dark);
                Document html = Jsoup.parse(new String(page.resource(page.getUrl(), true, "GET").getBytes(), StandardCharsets.UTF_8));
                assertTrue(html.text().contains("Readable instructions"));
                assertFalse(html.text().contains("Login"));
                assertTrue(html.select("script,iframe,form,input,img,[onclick]").isEmpty());
                assertEquals(2, html.select("a[href]").size());
                assertNotNull(html.getElementById("install"));
                assertTrue(page.getUrl().contains("#"));
                assertTrue(page.isCurrentDocument(network + "#install"));
                assertTrue(html.head().html().contains(dark ? "#000" : "#fff"));
                assertEquals(1, transport.requests.size());
                assertNull(page.resource(network, false, "GET"));
                assertNull(page.resource(network, true, "POST"));
                assertNull(page.resource("https://evil.example", true, "GET"));
                assertNull(page.resource("file:///data/private", false, "GET"));
            }
            assertTrue(transport.closed);
        }
    }
    @Test public void redirectsAreValidatedBeforeAnAdditionalNetworkRequest() {
        FakePrivacyTransport transport = fixture("");
        transport.add(ROOT, 302, "text/html", "").headers.put("Location", "https://evil.example/login");
        assertThrows(IOException.class, () -> new GitHubHelpPageLoader(transport).load(ROOT, false));
        assertEquals(1, transport.requests.size());
    }
    @Test public void challengeDownloadsBadEncodingAndOversizedResponsesFailClosed() {
        for (String body : new String[] {"<h1>Sign in</h1>", "x".repeat(1024 * 1024 + 1)}) {
            FakePrivacyTransport transport = fixture("");
            transport.add(ROOT, 200, "text/html", body);
            assertThrows(IOException.class, () -> new GitHubHelpPageLoader(transport).load(ROOT, false));
        }
        FakePrivacyTransport transport = fixture("");
        transport.replies.get(ROOT).headers.put("Content-Disposition", "attachment; filename=payload.html");
        assertThrows(IOException.class, () -> new GitHubHelpPageLoader(transport).load(ROOT, false));
        FakePrivacyTransport malformed = fixture("");
        malformed.replies.get(ROOT).bytes = new byte[] {(byte)0xc3, 0x28};
        assertThrows(IOException.class, () -> new GitHubHelpPageLoader(malformed).load(ROOT, false));
    }
    @Test public void onlyTheBoundedRepositoryQrImageIsFetchedAndRegistered() throws Exception {
        FakePrivacyTransport transport = fixture("<img src='/alcovaibe/Bluetooth-disabler/raw/main/docs/bluetooth-disable-device-owner-qr.png'>");
        byte[] png = new byte[24];
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        System.arraycopy(signature, 0, png, 0, 8); png[19] = 10; png[23] = 10;
        transport.add(GitHubHelpUrlPolicy.QR, 200, "image/png", "").bytes = png;
        try (GitHubHelpPageLoader loader = new GitHubHelpPageLoader(transport)) {
            GitHubHelpPageLoader.Page page = loader.load(ROOT, false);
            assertArrayEquals(png, page.resource(GitHubHelpUrlPolicy.QR, false, "GET").getBytes());
            assertNull(page.resource(GitHubHelpUrlPolicy.QR, true, "GET"));
            assertEquals(2, transport.requests.size());
        }
        png[16] = 127;
        assertThrows(IOException.class, () -> GitHubHelpPageLoader.validatePng(png));
    }
}

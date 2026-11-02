package com.pulse.bluetoothdisable.privacy;

import org.junit.Test;
import static org.junit.Assert.*;

public class GitHubHelpUrlPolicyTest {
    @Test public void languageTargetsKeepTheirInstallationAnchors() {
        for (String language : new String[] {"ru", "en"}) {
            String target = GitHubHelpUrlPolicy.documentForLanguage(language);
            assertEquals("github.com", GitHubHelpUrlPolicy.requireDocument(target).getHost());
            assertNotNull(GitHubHelpUrlPolicy.requireDocument(target).getFragment());
            assertNull(GitHubHelpUrlPolicy.networkDocument(target).getFragment());
            assertEquals(GitHubHelpUrlPolicy.requireDocument(target),
                    GitHubHelpUrlPolicy.requireDocument(GitHubHelpUrlPolicy.requireDocument(target).toASCIIString()));
        }
        assertTrue(GitHubHelpUrlPolicy.documentForLanguage("ru").endsWith("#установка-через-device-owner-qr"));
        assertTrue(GitHubHelpUrlPolicy.documentForLanguage("en").endsWith("#device-owner-qr-installation"));
    }
    @Test public void untrustedOriginsPathsEncodingsAndSchemesAreRejected() {
        String root = "https://github.com/alcovaibe/Bluetooth-disabler";
        for (String url : new String[] {"http://github.com/alcovaibe/Bluetooth-disabler", "javascript:alert(1)",
                "intent://github.com/alcovaibe/Bluetooth-disabler", "file:///data/private", "content://private",
                "https://github.com.evil.example/alcovaibe/Bluetooth-disabler", "https://user@github.com/alcovaibe/Bluetooth-disabler",
                "https://github.com:444/alcovaibe/Bluetooth-disabler", "https://github.com/other/repo",
                root + "/issues", root + "/releases/latest", root + "/blob/other/README.md", root + "?next=evil",
                root + "/blob/main/../README.md", root + "/%2e%2e/evil", root + "#%00", root + "#%252f",
                root + "#bad\\anchor", root + "\n"}) {
            assertThrows(url, IllegalArgumentException.class, () -> GitHubHelpUrlPolicy.requireDocument(url));
        }
    }
}

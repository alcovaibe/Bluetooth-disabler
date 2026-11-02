package com.pulse.bluetoothdisable.privacy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Only this repository's rendered installation documents and its fixed QR image. */
public final class GitHubHelpUrlPolicy {
    public static final String REPOSITORY = "/alcovaibe/Bluetooth-disabler";
    public static final String QR = "https://raw.githubusercontent.com" + REPOSITORY
            + "/main/docs/bluetooth-disable-device-owner-qr.png";
    private static final Set<String> DOCUMENTS = new HashSet<>(Arrays.asList(
            REPOSITORY, REPOSITORY + "/", REPOSITORY + "/blob/main/README.md",
            REPOSITORY + "/blob/main/README_EN.md", REPOSITORY + "/blob/main/docs/QR_PROVISIONING.md",
            REPOSITORY + "/blob/main/docs/QR_PROVISIONING_EN.md"));
    private GitHubHelpUrlPolicy() {}

    public static String documentForLanguage(String language) {
        return "https://github.com" + ("ru".equals(language)
                ? REPOSITORY + "#установка-через-device-owner-qr"
                : REPOSITORY + "/blob/main/README_EN.md#device-owner-qr-installation");
    }

    public static URI requireDocument(String value) {
        if (value == null || value.isEmpty() || value.length() > 2048) throw new IllegalArgumentException("Invalid help URL");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == 0x7f || c == '\\') throw new IllegalArgumentException("Ambiguous help URL");
        }
        try {
            URI uri = new URI(value);
            String authority = uri.getRawAuthority();
            String fragment = uri.getFragment();
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                    || !("github.com".equals(authority) || "github.com:443".equals(authority))
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || !(uri.getPort() == -1 || uri.getPort() == 443)
                    || !DOCUMENTS.contains(uri.getRawPath()) || !uri.equals(uri.normalize())
                    || (fragment != null && !fragment.matches("[\\p{L}\\p{N}_-]{0,160}"))) {
                throw new IllegalArgumentException("Untrusted help URL");
            }
            String path = uri.getRawPath().equals(REPOSITORY + "/") ? REPOSITORY : uri.getRawPath();
            return new URI("https", null, "github.com", -1, path, null, fragment);
        } catch (URISyntaxException e) { throw new IllegalArgumentException("Invalid help URL", e); }
    }

    public static URI networkDocument(String value) {
        URI uri = requireDocument(value);
        return URI.create("https://github.com" + uri.getRawPath());
    }

    public static URI resolveDocument(URI base, String relative) {
        if (relative == null || relative.isEmpty()) throw new IllegalArgumentException("Missing help target");
        return requireDocument(base.resolve(relative).toString());
    }
}

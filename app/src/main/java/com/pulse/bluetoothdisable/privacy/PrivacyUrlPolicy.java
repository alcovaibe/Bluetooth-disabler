package com.pulse.bluetoothdisable.privacy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A deliberately small allowlist, not a general-purpose browser URL validator. */
public final class PrivacyUrlPolicy {
    public enum Kind { DOCUMENT, STYLESHEET }
    public static final String HOST = "bluetoothdisable.app";
    private static final Set<String> DOCUMENTS = new HashSet<>(Arrays.asList(
            "/privacy/ru/privacy.html", "/privacy/en/privacy.html",
            // Cloudflare Pages redirects the public .html addresses to these exact aliases.
            "/privacy/ru/privacy", "/privacy/en/privacy"));
    private static final Set<String> STYLESHEETS = new HashSet<>(Arrays.asList(
            "/assets/css/variables.css", "/assets/css/themes.css", "/assets/css/main.css",
            "/assets/css/components.css", "/assets/css/responsive.css"));

    private PrivacyUrlPolicy() {}

    public static String documentForLanguage(String language) {
        return "https://" + HOST + "/privacy/" + ("ru".equals(language) ? "ru" : "en") + "/privacy.html";
    }

    public static URI require(String value, Kind kind) {
        requireUnambiguous(value);
        try {
            URI uri = new URI(value);
            String authority = uri.getRawAuthority();
            if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())
                    || uri.getRawUserInfo() != null
                    || !(HOST.equals(authority) || (HOST + ":443").equals(authority))
                    || !(uri.getPort() == -1 || uri.getPort() == 443)
                    || !uri.equals(uri.normalize())) {
                throw new IllegalArgumentException("Untrusted privacy URL");
            }
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            String fragment = uri.getRawFragment();
            if (kind == Kind.DOCUMENT) {
                if (!DOCUMENTS.contains(path) || query != null
                        || (fragment != null && !fragment.matches("[A-Za-z0-9_-]{0,128}"))) {
                    throw new IllegalArgumentException("Not a privacy document");
                }
            } else if (!STYLESHEETS.contains(path) || fragment != null
                    || (query != null && !query.matches("v=[A-Za-z0-9._-]{1,64}"))) {
                throw new IllegalArgumentException("Not a privacy stylesheet");
            }
            // Canonical network/cache key: no fragment and no explicit default port.
            return new URI("https", HOST, path, query, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid privacy URL", e);
        }
    }

    public static URI resolve(URI base, String relative, Kind kind) {
        requireUnambiguous(relative);
        try {
            return require(base.resolve(new URI(relative)).toString(), kind);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid redirect or resource URL", e);
        }
    }

    private static void requireUnambiguous(String value) {
        if (value == null || value.isEmpty() || value.length() > 2048) {
            throw new IllegalArgumentException("Missing or excessive URL");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // The supported paths need no percent escapes, Unicode or whitespace.
            if (c <= 0x20 || c >= 0x7f || c == '\\' || c == '%') {
                throw new IllegalArgumentException("Ambiguous URL encoding");
            }
        }
    }
}

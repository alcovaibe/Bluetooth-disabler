package com.pulse.bluetoothdisable.updates;

import java.net.URI;
import java.net.URISyntaxException;

/** URL authority is checked before OkHttp/browser normalization. No remote URL configuration. */
public final class UpdateUrls {
    public static final String MANIFEST = "https://bluetoothdisable.app/updates/android.json";
    public static final String APPLICATION_ID = "com.pulse.bluetoothdisable";
    private UpdateUrls() {}

    private static URI parse(String value, String host) {
        if (value == null || value.length() > 300) throw new IllegalArgumentException("URL length");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 32 || c >= 127 || c == '\\' || c == '%')
                throw new IllegalArgumentException("Ambiguous URL");
        }
        try {
            URI uri = new URI(value);
            if (!"https".equals(uri.getScheme()) || !host.equals(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !(host.equals(uri.getRawAuthority()) || (host + ":443").equals(uri.getRawAuthority())))
                throw new IllegalArgumentException("URL authority");
            return uri;
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Invalid URL", error);
        }
    }

    public static URI manifest(String value) {
        URI uri = parse(value, "bluetoothdisable.app");
        if (!"/updates/android.json".equals(uri.getRawPath())) throw new IllegalArgumentException("Manifest path");
        return uri;
    }

    public static URI release(String value) {
        URI uri = parse(value, "github.com");
        if (!uri.getRawPath().matches("/alcovaibe/Bluetooth-disabler/releases/tag/v[0-9]{1,6}(\\.[0-9]{1,6}){1,3}"))
            throw new IllegalArgumentException("Release path");
        return uri;
    }
}

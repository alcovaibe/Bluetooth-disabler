package com.pulse.bluetoothdisable.updates;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateManifestTest {
    private static final String RELEASE = "{\"versionCode\":100,\"versionName\":\"1.2.0\",\"minSdk\":26,"
            + "\"releaseUrl\":\"https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0\","
            + "\"notes\":{\"ru\":\"Исправления\\nПодробнее\",\"en\":\"Fixes\"}}";
    private static final String JSON = "{\"schemaVersion\":1,\"platform\":\"android\",\"channel\":\"stable\","
            + "\"applicationId\":\"com.pulse.bluetoothdisable\",\"release\":" + RELEASE + "}";
    private UpdateManifest parse(String value) { return UpdateManifest.parse(value.getBytes(StandardCharsets.UTF_8)); }
    private void rejects(String value) { assertThrows(IllegalArgumentException.class, () -> parse(value)); }

    @Test public void comparesNumericCodeAndCompatibility() {
        UpdateManifest.Release release = parse(JSON).release;
        assertTrue(release.isUpdateFor(99, 26));
        assertFalse(release.isUpdateFor(100, 26));
        assertFalse(release.isUpdateFor(101, 37));
        assertFalse(release.isUpdateFor(99, 25));
        assertEquals("Исправления\nПодробнее", release.ru);
    }
    @Test public void emptyReleaseIsExplicit() {
        assertNull(parse(JSON.replace(RELEASE, "null")).release);
    }
    @Test public void rejectsWrongIdentityAndSchema() {
        for (String[] replacement : new String[][]{{"stable", "internal"}, {"android", "windows"},
                {"com.pulse.bluetoothdisable", "com.pulse.bluetoothdisable.internal"}, {"schemaVersion\":1", "schemaVersion\":2"}})
            rejects(JSON.replace(replacement[0], replacement[1]));
    }
    @Test public void rejectsDuplicatesIncludingEscapedKeysAndNullValues() {
        rejects(JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":null,\"schemaVersion\":1"));
        rejects(JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schema\\u0056ersion\":1"));
        rejects(JSON.replace("\"ru\":", "\"ru\":null,\"ru\":"));
    }
    @Test public void rejectsBadNumbersTypesAndUnknownFields() {
        for (String number : new String[]{"0", "-1", "01", "1.0", "1e2", "true", "\"100\"", "2147483648", "999999999999999999999"})
            rejects(JSON.replace("\"versionCode\":100", "\"versionCode\":" + number));
        rejects(JSON.replace("\"minSdk\":26", "\"minSdk\":null"));
        rejects(JSON.replace("\"en\":\"Fixes\"", "\"en\":[]"));
        rejects(JSON.replace("\"platform\":", "\"unknown\":1,\"platform\":"));
    }
    @Test public void rejectsMalformedOversizeAndAmbiguousStrings() {
        for (String value : new String[]{"", JSON + "{}", JSON.replace("\"channel\":", "/*comment*/\"channel\":"),
                JSON.replace("Fixes", "\\uD800"), JSON.replace("Fixes", "\\u202eFixes"),
                JSON.replace("Fixes", "\\u0000"), JSON.replace("Fixes", "x".repeat(1501)),
                JSON.replace("Fixes", "\\u+123"), JSON.replace("\"en\":\"Fixes\"", "\"en\":\"Fixes\","),
                "{".repeat(1000)}) rejects(value);
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse(new byte[16385]));
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse(new byte[]{(byte) 0xc3, 0x28}));
    }
    @Test public void rejectsVersionUrlMismatch() {
        rejects(JSON.replace("/tag/v1.2.0", "/tag/v1.2.1"));
    }
    @Test public void fixedManifestAndReleaseUrlsOnly() {
        assertNotNull(UpdateUrls.manifest(UpdateUrls.MANIFEST));
        assertNotNull(UpdateUrls.manifest("https://bluetoothdisable.app:443/updates/android.json"));
        for (String value : new String[]{
                "http://bluetoothdisable.app/updates/android.json", "https://bluetoothdisable.app.evil.example/updates/android.json",
                "https://evilbluetoothdisable.app/updates/android.json", "https://user@bluetoothdisable.app/updates/android.json",
                "https://bluetoothdisable.app@evil.example/updates/android.json", "https://bluetoothdisable.app:444/updates/android.json",
                "https://bluetoothdisable.app:0443/updates/android.json", "https://bluetoothdisable.app./updates/android.json",
                "https://bluetoothdisable.app/updates/%61ndroid.json", "https://bluetoothdisable.app/updates/../updates/android.json",
                "https://bluetoothdisable.app/updates/android.json?version=1", "https://bluetoothdisable.app/updates/android.json#x",
                "https://bluetoothdisable.app\\@evil.example/updates/android.json", "https://bluetoothdisable.app\n/updates/android.json",
                "https://127.0.0.1/updates/android.json", "https://bluetoothdisable.app/updates/%2561ndroid.json", "javascript:alert(1)",
                "intent://updates", "file:///updates/android.json", "content://updates", "data:application/json,{}"})
            assertThrows(value, IllegalArgumentException.class, () -> UpdateUrls.manifest(value));
        for (String value : new String[]{"https://github.com.evil.example/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0",
                "https://user@github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0",
                "https://github.com/other/Bluetooth-disabler/releases/tag/v1.2.0",
                "https://github.com/alcovaibe/other/releases/tag/v1.2.0",
                "https://github.com/alcovaibe/Bluetooth-disabler/releases/latest",
                "https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0/evil",
                "https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1%2e2%2e0",
                "https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0?x=1",
                "intent://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0"})
            assertThrows(value, IllegalArgumentException.class, () -> UpdateUrls.release(value));
    }
}

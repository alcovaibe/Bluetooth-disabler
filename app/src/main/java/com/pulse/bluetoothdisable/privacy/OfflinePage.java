package com.pulse.bluetoothdisable.privacy;

/** A validated in-memory document; the renderer has no network fallback. */
public interface OfflinePage {
    String getUrl();
    String getContentSecurityPolicy();
    Resource resource(String value, boolean mainFrame, String method);
    boolean isCurrentDocument(String value);
    /** Validates a navigation, including its anchor, or throws. */
    String navigationTarget(String value);

    class Resource {
        public final String mime;
        private final byte[] bytes;
        Resource(String mime, byte[] bytes) { this.mime = mime; this.bytes = bytes.clone(); }
        public byte[] getBytes() { return bytes.clone(); }
    }
}

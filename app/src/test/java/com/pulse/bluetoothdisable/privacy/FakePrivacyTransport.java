package com.pulse.bluetoothdisable.privacy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FakePrivacyTransport implements PrivacyTransport {
    final Map<String, FakeReply> replies = new LinkedHashMap<>();
    final List<String> requests = new ArrayList<>();
    boolean closed;
    IOException failure;
    FakeReply add(String url, int code, String type, String text) {
        FakeReply reply = new FakeReply(code, type, text.getBytes(StandardCharsets.UTF_8));
        replies.put(url, reply);
        return reply;
    }
    @Override public Reply get(URI uri) throws IOException {
        requests.add(uri.toString());
        if (failure != null) throw failure;
        if (closed) throw new IOException("Closed");
        FakeReply reply = replies.get(uri.toString());
        if (reply == null) throw new IOException("Unexpected request: " + uri);
        return reply;
    }
    @Override public void close() { closed = true; }
    static final class FakeReply implements Reply {
        final int status;
        byte[] bytes;
        boolean closed;
        final Map<String, String> headers = new LinkedHashMap<>();
        FakeReply(int status, String type, byte[] bytes) {
            this.status = status; this.bytes = bytes;
            headers.put("Content-Type", type);
        }
        @Override public int code() { return status; }
        @Override public String header(String name) { return headers.get(name); }
        @Override public InputStream body() { return new ByteArrayInputStream(bytes); }
        @Override public void close() { closed = true; }
    }
}

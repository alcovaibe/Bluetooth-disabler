package com.pulse.bluetoothdisable.privacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class PrivacyFetcher {
    static final int MAX_REDIRECTS = 5;
    static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    static final int MAX_STYLESHEET_BYTES = 256 * 1024;
    static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;
    private final PrivacyTransport transport;

    PrivacyFetcher(PrivacyTransport transport) { this.transport = transport; }

    static final class Budget {
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        private int bytes;
        void check() throws IOException {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) {
                throw new IOException("Privacy load cancelled or timed out");
            }
        }
        void consume(int count) throws IOException {
            check();
            bytes += count;
            if (bytes > MAX_TOTAL_BYTES) throw new IOException("Privacy page exceeds size limit");
        }
    }

    static final class Payload {
        final URI uri;
        final String mime;
        final byte[] bytes;
        Payload(URI uri, String mime, byte[] bytes) { this.uri = uri; this.mime = mime; this.bytes = bytes; }
        String text() throws IOException {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                throw new IOException("Privacy content is not UTF-8", e);
            }
        }
    }

    Payload get(String value, PrivacyUrlPolicy.Kind kind, Budget budget) throws IOException {
        URI uri;
        try { uri = PrivacyUrlPolicy.require(value, kind); }
        catch (IllegalArgumentException e) { throw new IOException("Blocked privacy URL", e); }
        for (int redirects = 0; ; redirects++) {
            budget.check();
            try (PrivacyTransport.Reply reply = transport.get(uri)) {
                budget.check();
                int code = reply.code();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    if (redirects >= MAX_REDIRECTS) throw new IOException("Too many privacy redirects");
                    try { uri = PrivacyUrlPolicy.resolve(uri, reply.header("Location"), kind); }
                    catch (IllegalArgumentException e) { throw new IOException("Blocked privacy redirect", e); }
                    continue;
                }
                if (code != 200) throw new IOException("Privacy HTTP error: " + code);
                String disposition = reply.header("Content-Disposition");
                if (disposition != null && !disposition.toLowerCase(Locale.ROOT).startsWith("inline")) {
                    throw new IOException("Privacy downloads are disabled");
                }
                String type = reply.header("Content-Type");
                String expected = kind == PrivacyUrlPolicy.Kind.DOCUMENT ? "text/html" : "text/css";
                if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase(expected)) {
                    throw new IOException("Unsupported privacy content type");
                }
                for (String parameter : type.split(";")) {
                    String part = parameter.trim().toLowerCase(Locale.ROOT);
                    if (part.startsWith("charset=")
                            && !part.substring(8).replace("\"", "").equals("utf-8")) {
                        throw new IOException("Unsupported privacy charset");
                    }
                }
                int maximum = kind == PrivacyUrlPolicy.Kind.DOCUMENT ? MAX_DOCUMENT_BYTES : MAX_STYLESHEET_BYTES;
                try (InputStream input = reply.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        budget.consume(count);
                        if (count > maximum - output.size()) throw new IOException("Privacy response exceeds size limit");
                        output.write(buffer, 0, count);
                    }
                    budget.check();
                    return new Payload(uri, expected, output.toByteArray());
                }
            }
        }
    }
}

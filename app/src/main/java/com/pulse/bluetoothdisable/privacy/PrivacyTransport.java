package com.pulse.bluetoothdisable.privacy;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/** One GET only; implementations must never follow redirects themselves. */
interface PrivacyTransport extends Closeable {
    Reply get(URI uri) throws IOException;

    interface Reply extends Closeable {
        int code();
        String header(String name);
        InputStream body() throws IOException;
    }
}

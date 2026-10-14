package com.pulse.bluetoothdisable.privacy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Authenticator;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Private, cookie-free client; platform TLS trust and hostname checks are unchanged. */
final class OkHttpPrivacyTransport implements PrivacyTransport {
    private final AtomicBoolean closed = new AtomicBoolean();
    final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES).cache(null)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS).build();

    @Override public Reply get(URI uri) throws IOException {
        if (closed.get()) throw new IOException("Privacy request cancelled");
        Request request = new Request.Builder().url(uri.toString())
                .get().header("Accept", "text/html, text/css")
                .header("Cache-Control", "no-store")
                .header("User-Agent", "Bluetooth-Disable-Privacy/1.0").build();
        okhttp3.Call call = client.newCall(request);
        if (closed.get()) call.cancel();
        Response response = call.execute();
        return new Reply() {
            @Override public int code() { return response.code(); }
            @Override public String header(String name) { return response.header(name); }
            @Override public InputStream body() throws IOException {
                if (response.body() == null) throw new IOException("Missing privacy response");
                return response.body().byteStream();
            }
            @Override public void close() { response.close(); }
        };
    }

    @Override public void close() {
        closed.set(true);
        client.dispatcher().cancelAll();
        client.connectionPool().evictAll();
    }
}

package com.pulse.bluetoothdisable.privacy;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/** Reads GitHub's server-rendered Markdown, never its scripts, forms or browser session. */
public final class GitHubHelpPageLoader implements Closeable {
    static final String CSP = "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'; "
            + "img-src https://raw.githubusercontent.com; font-src 'none'; connect-src 'none'; "
            + "frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
    private final PrivacyTransport transport;
    public GitHubHelpPageLoader() { this(new OkHttpPrivacyTransport()); }
    GitHubHelpPageLoader(PrivacyTransport transport) { this.transport = transport; }

    public static final class Page implements OfflinePage {
        private final String url;
        private final String networkUrl;
        private final Map<String, OfflinePage.Resource> resources;
        Page(String url, Map<String, OfflinePage.Resource> resources) {
            this.url = url;
            networkUrl = GitHubHelpUrlPolicy.networkDocument(url).toString();
            this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        }
        @Override public String getUrl() { return url; }
        @Override public String getContentSecurityPolicy() { return CSP; }
        @Override public String navigationTarget(String value) {
            return GitHubHelpUrlPolicy.requireDocument(value).toASCIIString();
        }
        @Override public boolean isCurrentDocument(String value) {
            try { return networkUrl.equals(GitHubHelpUrlPolicy.networkDocument(value).toString()); }
            catch (IllegalArgumentException e) { return false; }
        }
        @Override public OfflinePage.Resource resource(String value, boolean mainFrame, String method) {
            if (!"GET".equals(method)) return null;
            if (mainFrame) {
                try { return resources.get(GitHubHelpUrlPolicy.networkDocument(value).toString()); }
                catch (IllegalArgumentException e) { return null; }
            }
            return GitHubHelpUrlPolicy.QR.equals(value) ? resources.get(value) : null;
        }
    }

    public Page load(String value, boolean dark) throws IOException {
        URI requested;
        try { requested = GitHubHelpUrlPolicy.requireDocument(value); }
        catch (IllegalArgumentException e) { throw new IOException("Blocked help URL", e); }
        PrivacyFetcher.Budget budget = new PrivacyFetcher.Budget();
        PrivacyFetcher.Payload html = fetch(requested.toString(), false, budget);
        Document remote = Jsoup.parse(html.text(), html.uri.toString());
        Element article = remote.selectFirst("article.markdown-body");
        if (article == null || article.selectFirst("h1, h2") == null || article.text().isEmpty()) {
            throw new IOException("GitHub installation document is missing");
        }
        Map<String, OfflinePage.Resource> resources = new LinkedHashMap<>();
        boolean qr = false;
        for (Element image : article.select("img")) {
            String src = image.absUrl("src");
            if (src.equals(GitHubHelpUrlPolicy.QR) || src.equals("https://github.com"
                    + GitHubHelpUrlPolicy.REPOSITORY + "/raw/main/docs/bluetooth-disable-device-owner-qr.png")
                    || src.equals("https://github.com" + GitHubHelpUrlPolicy.REPOSITORY
                    + "/raw/refs/heads/main/docs/bluetooth-disable-device-owner-qr.png")) {
                image.attr("src", GitHubHelpUrlPolicy.QR);
                qr = true;
            } else image.remove();
        }
        if (qr) {
            PrivacyFetcher.Payload image = fetch(GitHubHelpUrlPolicy.QR, true, budget);
            validatePng(image.bytes);
            resources.put(GitHubHelpUrlPolicy.QR, new OfflinePage.Resource("image/png", image.bytes));
        }
        Safelist safe = Safelist.relaxed().addTags("article", "section", "div", "span")
                .addAttributes(":all", "id").removeProtocols("a", "href", "http", "ftp", "mailto")
                .preserveRelativeLinks(true);
        Document clean = Jsoup.parseBodyFragment(Jsoup.clean(article.outerHtml(), html.uri.toString(), safe));
        for (Element node : clean.select("[id]")) {
            String id = node.id();
            if (id.startsWith("user-content-")) node.attr("id", id.substring(13));
        }
        for (Element link : clean.select("a[href]")) {
            try { link.attr("href", GitHubHelpUrlPolicy.resolveDocument(html.uri, link.attr("href")).toASCIIString()); }
            catch (IllegalArgumentException e) { link.unwrap(); }
        }
        for (Element image : clean.select("img")) {
            if (!GitHubHelpUrlPolicy.QR.equals(image.attr("src"))) image.remove();
        }
        Document page = Document.createShell(html.uri.toString());
        page.outputSettings().charset(StandardCharsets.UTF_8).prettyPrint(false);
        page.selectFirst("html").attr("lang", requested.getPath().contains("_EN.md") ? "en" : "ru");
        page.head().appendElement("meta").attr("charset", "utf-8");
        page.head().appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1");
        page.head().appendElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", CSP);
        page.head().appendElement("style").text("html{color-scheme:" + (dark ? "dark" : "light")
                + ";background:" + (dark ? "#000;color:#f1f1f1" : "#fff;color:#171717")
                + "}body{font:16px/1.6 sans-serif;margin:20px;overflow-wrap:anywhere}"
                + "article{max-width:760px;margin:auto}a{color:" + (dark ? "#91c7ff" : "#075db3")
                + "}pre{overflow-x:auto;padding:12px;background:" + (dark ? "#191919" : "#f2f2f2")
                + "}code{font-family:monospace}img{max-width:100%;height:auto}table{display:block;overflow-x:auto}"
                + "td,th{border:1px solid #888;padding:6px}h1,h2,h3{line-height:1.3}");
        page.body().html(clean.body().html());
        resources.put(html.uri.toString(), new OfflinePage.Resource("text/html", page.outerHtml().getBytes(StandardCharsets.UTF_8)));
        // Keep the installation anchor after a same-allowlist redirect.
        String anchor = requested.getRawFragment();
        return new Page(html.uri.toASCIIString() + (anchor == null ? "" : "#" + anchor), resources);
    }

    private PrivacyFetcher.Payload fetch(String value, boolean image, PrivacyFetcher.Budget budget) throws IOException {
        URI uri = image ? URI.create(GitHubHelpUrlPolicy.QR) : GitHubHelpUrlPolicy.networkDocument(value);
        for (int redirects = 0; ; redirects++) {
            budget.check();
            try (PrivacyTransport.Reply reply = transport.get(uri)) {
                budget.check();
                int code = reply.code();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    if (redirects >= PrivacyFetcher.MAX_REDIRECTS) throw new IOException("Too many help redirects");
                    try {
                        String location = reply.header("Location");
                        if (location == null) throw new IllegalArgumentException("Missing redirect");
                        URI next = uri.resolve(location);
                        if (image) {
                            if (!GitHubHelpUrlPolicy.QR.equals(next.toString())) throw new IllegalArgumentException("Blocked image redirect");
                            uri = next;
                        } else uri = GitHubHelpUrlPolicy.networkDocument(next.toString());
                    } catch (IllegalArgumentException e) { throw new IOException("Blocked help redirect", e); }
                    continue;
                }
                if (code != 200) throw new IOException("GitHub HTTP error: " + code);
                String disposition = reply.header("Content-Disposition");
                if (disposition != null && !disposition.toLowerCase(Locale.ROOT).startsWith("inline")) throw new IOException("Help downloads disabled");
                String expected = image ? "image/png" : "text/html";
                String type = reply.header("Content-Type");
                if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase(expected)) throw new IOException("Unsupported help response");
                if (!image) for (String parameter : type.split(";")) {
                    String part = parameter.trim().toLowerCase(Locale.ROOT);
                    if (part.startsWith("charset=") && !part.substring(8).replace("\"", "").equals("utf-8")) throw new IOException("Unsupported help encoding");
                }
                int max = image ? 256 * 1024 : PrivacyFetcher.MAX_DOCUMENT_BYTES;
                try (InputStream input = reply.body(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        budget.consume(count);
                        if (count > max - out.size()) throw new IOException("Help response exceeds size limit");
                        out.write(buffer, 0, count);
                    }
                    budget.check();
                    return new PrivacyFetcher.Payload(uri, expected, out.toByteArray());
                }
            }
        }
    }

    static void validatePng(byte[] data) throws IOException {
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (data.length < 24) throw new IOException("Invalid QR image");
        for (int i = 0; i < 8; i++) if (data[i] != signature[i]) throw new IOException("Invalid QR image");
        for (int offset : new int[] {16, 20}) {
            long size = 0;
            for (int i = offset; i < offset + 4; i++) size = (size << 8) | (data[i] & 255);
            if (size < 1 || size > 2048) throw new IOException("Excessive QR dimensions");
        }
    }
    @Override public void close() throws IOException { transport.close(); }
}

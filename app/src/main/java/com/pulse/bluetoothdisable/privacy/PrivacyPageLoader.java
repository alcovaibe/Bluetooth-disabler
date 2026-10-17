package com.pulse.bluetoothdisable.privacy;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/** Fetches a bounded, inert page before the renderer can see any remote content. */
public final class PrivacyPageLoader implements Closeable {
    public static final String CSP = "default-src 'none'; script-src 'none'; "
            + "style-src https://bluetoothdisable.app 'unsafe-inline'; img-src 'none'; "
            + "font-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; "
            + "base-uri 'none'; form-action 'none'";
    private final PrivacyTransport transport;

    public PrivacyPageLoader() { this(new OkHttpPrivacyTransport()); }
    PrivacyPageLoader(PrivacyTransport transport) { this.transport = transport; }

    public static final class Resource extends OfflinePage.Resource {
        Resource(String mime, byte[] bytes) { super(mime, bytes); }
    }

    public static final class Page implements OfflinePage {
        public final String url;
        private final Map<String, PrivacyPageLoader.Resource> resources;
        Page(String url, Map<String, PrivacyPageLoader.Resource> resources) {
            this.url = url;
            this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        }
        @Override public String getUrl() { return url; }
        @Override public String getContentSecurityPolicy() { return CSP; }
        @Override public String navigationTarget(String value) {
            return PrivacyUrlPolicy.require(value, PrivacyUrlPolicy.Kind.DOCUMENT).toString();
        }
        /** No network fallback: an absent entry must produce a blocked WebResourceResponse. */
        public PrivacyPageLoader.Resource resource(String value, boolean mainFrame, String method) {
            if (!"GET".equals(method)) return null;
            try {
                URI uri = PrivacyUrlPolicy.require(value, mainFrame
                        ? PrivacyUrlPolicy.Kind.DOCUMENT : PrivacyUrlPolicy.Kind.STYLESHEET);
                return resources.get(uri.toString());
            } catch (IllegalArgumentException e) { return null; }
        }
        public boolean isCurrentDocument(String value) {
            try { return url.equals(PrivacyUrlPolicy.require(value, PrivacyUrlPolicy.Kind.DOCUMENT).toString()); }
            catch (IllegalArgumentException e) { return false; }
        }
    }

    public Page load(String url, boolean dark) throws IOException {
        PrivacyFetcher fetcher = new PrivacyFetcher(transport);
        PrivacyFetcher.Budget budget = new PrivacyFetcher.Budget();
        PrivacyFetcher.Payload html = fetcher.get(url, PrivacyUrlPolicy.Kind.DOCUMENT, budget);
        Document remote = Jsoup.parse(html.text(), html.uri.toString());
        Element main = remote.selectFirst("main");
        if (main == null || main.selectFirst("h1") == null || main.text().isEmpty()) {
            throw new IOException("The privacy document is missing");
        }
        Document page = Document.createShell(html.uri.toString());
        page.outputSettings().charset(StandardCharsets.UTF_8).prettyPrint(false);
        page.selectFirst("html").attr("lang", html.uri.getPath().contains("/ru/") ? "ru" : "en")
                .attr("data-theme", dark ? "dark" : "light");
        page.head().appendElement("meta").attr("charset", "utf-8");
        page.head().appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1");
        page.head().appendElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", CSP);
        Map<String, Resource> resources = new LinkedHashMap<>();
        int stylesheetCount = 0;
        for (Element link : remote.select("link[rel=stylesheet][href]")) {
            URI uri;
            try { uri = PrivacyUrlPolicy.resolve(html.uri, link.attr("href"), PrivacyUrlPolicy.Kind.STYLESHEET); }
            catch (IllegalArgumentException e) { throw new IOException("Untrusted privacy stylesheet", e); }
            if (resources.containsKey(uri.toString())) continue;
            if (++stylesheetCount > 5) throw new IOException("Too many privacy stylesheets");
            PrivacyFetcher.Payload css = fetcher.get(uri.toString(), PrivacyUrlPolicy.Kind.STYLESHEET, budget);
            css.text(); // Reject malformed UTF-8 before handing it to WebView.
            Resource resource = new Resource(css.mime, css.bytes);
            resources.put(uri.toString(), resource);
            resources.put(css.uri.toString(), resource);
            page.head().appendElement("link").attr("rel", "stylesheet").attr("href", uri.toString());
        }
        // Preserve readable document structure, not scripts, frames, forms, styles or media.
        Safelist safe = Safelist.relaxed().removeTags("img")
                .addTags("main", "article", "section", "div", "span")
                .addAttributes(":all", "class", "id")
                .removeProtocols("a", "href", "http", "ftp", "mailto")
                .preserveRelativeLinks(false);
        Document cleaned = Jsoup.parseBodyFragment(Jsoup.clean(main.outerHtml(), html.uri.toString(), safe));
        for (Element link : cleaned.select("a[href]")) {
            try { PrivacyUrlPolicy.require(link.attr("href"), PrivacyUrlPolicy.Kind.DOCUMENT); }
            catch (IllegalArgumentException e) { link.unwrap(); }
        }
        page.body().html(cleaned.body().html());
        page.head().appendElement("style").text(".card p,.card ul,.card ol{margin-top:1em} "
                + ".section{padding-block:24px}.card{max-width:760px;margin-inline:auto} "
                + "body{overflow-wrap:anywhere}");
        Resource document = new Resource("text/html", page.outerHtml().getBytes(StandardCharsets.UTF_8));
        String requested = PrivacyUrlPolicy.require(url, PrivacyUrlPolicy.Kind.DOCUMENT).toString();
        resources.put(requested, document);
        resources.put(html.uri.toString(), document);
        return new Page(html.uri.toString(), resources);
    }

    @Override public void close() throws IOException { transport.close(); }
}

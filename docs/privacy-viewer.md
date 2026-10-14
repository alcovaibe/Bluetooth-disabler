# Android privacy-policy viewer

The main screen keeps its existing setup-help link and adds a separate Privacy Policy link immediately below it. The policy link remains accessible after provisioning. The non-exported activity accepts only an `en`/`ru` identifier, uses the app's persisted light/dark preference and closes on the toolbar action or system Back.

## Trust boundary

The renderer never performs network loads. A private OkHttp client first downloads a bounded snapshot; `WebViewClient` serves only the snapshot's immutable HTML/CSS entries and returns a blocked response for everything else. `blockNetworkLoads=true` provides a separate safeguard against browser paths that bypass interception. No callback falls back to an uncontrolled network request.

The URL policy uses `java.net.URI`, requires HTTPS and the exact `bluetoothdisable.app` authority (optionally port 443), and rejects userinfo, percent escapes, non-ASCII characters, whitespace, backslashes and unsupported paths. Documents and stylesheets have separate allowlists. CSS accepts only an optional bounded `v` version query; documents accept safe in-page anchors and no query.

The primary URLs are `/privacy/ru/privacy` and `/privacy/en/privacy`; the app requests them directly. The two old `.html` URLs are compatibility inputs only. Cloudflare Workers Static Assets redirects them to the primary URLs with explicit HTTP 301 rules. Each redirect is resolved and validated before the next request. OkHttp's automatic redirects and retries are disabled. Limits: five redirects per resource, 15 seconds per HTTP call, five-second connect/read timeouts, a 60-second snapshot budget, 1 MiB per document, 256 KiB per stylesheet and 2 MiB total.

## Public URL architecture

`config/site-urls.json` is the build-time source of truth for the website and app. `python3 scripts/sync_site_urls.py --write` generates/updates Android URL constants, the site's language-link registry, canonical tags, sitemap, exact legacy 301 rules and GitHub Pages redirect targets. HTML source files keep their `.html` filenames: Cloudflare serves their contents at the extensionless URLs. The origin remains fixed, and no configuration is downloaded into the app at runtime.

`python3 scripts/sync_site_urls.py --check` is read-only and fails on drift. It runs in Platform CI and before both site deployments. Browser tests exercise the actual language-switching and GitHub Pages fallback scripts. Contract tests ensure that each primary URL has a real source file and matching canonical/sitemap entry, and that every legacy rule terminates at a primary URL without a reverse redirect or loop. Changes to routes require a reviewed commit and regenerated consumers.

The intended deployed responses are: `/` and the two extensionless policy routes return 200; `/index.html` and the two old policy `.html` routes return 301 to their primary URLs. Old URLs are not separately served documents and are absent from primary links, canonical tags and sitemap. Their redirect-only compatibility keeps external links working. Google may still list legacy URLs as excluded "page with redirect" entries; that is expected and does not establish whether the canonical URLs are indexed.

The client uses standard platform TLS verification, `CookieJar.NO_COOKIES`, no cache, no authentication handlers, no tokens and no application data. Only successful HTML/CSS responses with UTF-8 content are accepted; attachment responses fail closed. A native TLS error is propagated, never bypassed. No browser cookies or global storage are changed or cleared.

Jsoup creates an inert copy of the policy's `main` content with a restricted HTML safelist. Scripts, frames, forms, media and unsupported links are removed. The document has an explicit `data-theme` so the site's light/dark CSS works without its JavaScript theme switcher. Its headings, paragraphs, lists and tables remain readable. It is not a general-purpose browser and does not open the website's home-page link or external apps.

JavaScript, DOM storage, mixed content, file/content access, popups, file selection, downloads and web permission requests are disabled/denied. There are no JavaScript interfaces or messaging bridges. A restrictive CSP is supplied in both the generated HTML and response headers. Safe Browsing remains enabled when supported; unsafe pages are not allowed to proceed. SSL callbacks cancel. Renderer failure and unavailable WebView providers lead to the retry UI. The view and its private network client are released on close; no process-wide WebView timers/storage are touched.

## Supported resources

The site's current policy pages need only these five stylesheets:

- `/assets/css/variables.css`
- `/assets/css/themes.css`
- `/assets/css/main.css`
- `/assets/css/components.css`
- `/assets/css/responsive.css`

Scripts, images, favicon, fonts and new stylesheets are intentionally unsupported. A future site change requiring new resources needs an explicit allowlist review. Unsupported CSS subresources cannot reach the network.

## Verification

On 2026-10-14, both public policy addresses returned HTTP 307 to the above aliases and then HTTP 200. All five current stylesheets returned HTTP 200 with `text/css`. The real page fixtures were processed for RU/EN and light/dark; each snapshot required six native document/style responses, and no script.

JVM tests cover hostile URL authorities, schemes/encodings, document/resource separation, each redirect hop, loops, status/MIME/charset/download rejection, size limits, TLS error propagation, client cookie/redirect/cache settings, sanitization and renderer lookups without network fallback. Instrumentation tests use controlled native fixtures to exercise real WebView rendering for both languages/themes and failure/retry/close. They do not depend on the public site's availability.

Full Android build, lint and instrumented results must be checked in the PR's CI. Local JVM success alone does not establish Android compatibility. The test suite's simulated TLS failure verifies propagation; it is not a full certificate-pinning or penetration test. There is no certificate pinning: platform trust is used. Safe Browsing checks, if supplied by the device's WebView implementation, complement the closed URL/resource boundary rather than replacing it.

`INTERNET` is now required in every variant for this separate viewer. The packaged-permission check and READMEs reflect that change; the private test-config implementation remains isolated to `internal`. Device Owner behavior, Bluetooth protection and cover data are unchanged.

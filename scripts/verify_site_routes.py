#!/usr/bin/env python3
"""Exercise the actual Cloudflare local asset router; never deploy or access an account."""
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8788"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class PageLinks(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.canonical = []
        self.styles = []
        self.feed(text)

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag != "link":
            return
        if attrs.get("rel") == "canonical":
            self.canonical.append(attrs.get("href"))
        elif attrs.get("rel") == "stylesheet":
            self.styles.append(attrs.get("href"))


def verify():
    contract = json.loads((ROOT / "config/site-urls.json").read_text())
    # The loopback fixture is deliberately not sent through a machine's external proxy.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def get(path):
        try:
            response = opener.open(BASE + path, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, response.headers, response.read().decode("utf-8")

    with tempfile.TemporaryDirectory(prefix="site-route-check-") as directory:
        log_path = Path(directory) / "wrangler.log"
        with log_path.open("w+") as log:
            env = dict(os.environ, WRANGLER_SEND_METRICS="false", CI="1")
            process = subprocess.Popen([
                "npx", "--yes", "wrangler@4.151.0", "dev", "--local",
                "--ip", "127.0.0.1", "--port", "8788", "--persist-to", directory + "/state",
            ], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                deadline = time.monotonic() + 120
                while True:
                    if process.poll() is not None:
                        raise RuntimeError("Local Cloudflare router failed to start")
                    try:
                        if get("/")[0] == 200:
                            break
                    except (urllib.error.URLError, TimeoutError, ConnectionError):
                        pass
                    if time.monotonic() >= deadline:
                        raise RuntimeError("Local Cloudflare router startup timed out")
                    time.sleep(0.25)
                routes = [contract["home"], *contract["privacy"].values()]
                status, headers, body = get('/updates/android.json')
                assert status == 200 and headers.get('Location') is None, (status, headers)
                assert 'application/json' in headers.get('Content-Type', ''), headers
                assert 'max-age=300' in headers.get('Cache-Control', ''), headers
                assert json.loads(body)['schemaVersion'] == 1
                print('Verified JSON 200, no redirect and bounded cache: /updates/android.json')
                for route in routes:
                    status, headers, text = get(route)
                    assert status == 200 and headers.get("Location") is None, (route, status, headers)
                    page = PageLinks(text)
                    assert page.canonical == [contract["origin"] + route], (route, page.canonical)
                    for href in page.styles:
                        resource = urllib.parse.urljoin(BASE + route, href)
                        split = urllib.parse.urlsplit(resource)
                        assert split.netloc == "127.0.0.1:8788", resource
                        resource_path = split.path + ("?" + split.query if split.query else "")
                        code, resource_headers, _ = get(resource_path)
                        assert code == 200 and "text/css" in resource_headers.get("Content-Type", ""), resource
                    print(f"Verified 200, canonical and CSS: {route}")
                for legacy, primary in [("/index.html", "/"), *[(path + ".html", path) for path in contract["privacy"].values()]]:
                    status, headers, _ = get(legacy)
                    target = urllib.parse.urlsplit(headers.get("Location", ""))
                    assert status == 301 and target.path == primary, (legacy, status, headers)
                    assert target.netloc in {"", "127.0.0.1:8788"}, (legacy, target)
                    print(f"Verified one-way 301: {legacy} -> {primary}")
            except BaseException:
                log.flush()
                print("\n".join(log_path.read_text().splitlines()[-40:]))
                raise
            finally:
                # Include npm's child server, not only the npx parent.
                import signal
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=5)


if __name__ == "__main__":
    verify()

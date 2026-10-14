"""Cross-consumer checks for the extensionless public URL contract."""
import json
from html.parser import HTMLParser
from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET

import sync_site_urls


class Canonicals(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.urls = []
        self.feed(text)

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag == "link" and attrs.get("rel") == "canonical":
            self.urls.append(attrs.get("href"))


class SiteUrlTests(unittest.TestCase):
    def setUp(self):
        self.root = sync_site_urls.ROOT
        self.contract = sync_site_urls.load_contract(self.root)
        self.origin = self.contract["origin"]
        self.routes = [self.contract["home"], *self.contract["privacy"].values()]

    def fixture(self, root):
        files = set(sync_site_urls.outputs(self.root, self.contract)) | {
            "config/site-urls.json", "wrangler.jsonc",
            "docs/github-pages-redirect/index.html", "docs/github-pages-redirect/404.html",
        }
        for relative in files:
            destination = root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(self.root / relative, destination)

    def test_all_checked_in_consumers_agree(self):
        self.assertEqual([], sync_site_urls.sync(self.root))
        config = json.loads((self.root / "wrangler.jsonc").read_text())
        self.assertEqual("./docs/pages", config["assets"]["directory"])
        self.assertEqual("auto-trailing-slash", config["assets"]["html_handling"])

    def test_canonical_documents_have_sources_and_no_extension(self):
        for route in self.routes:
            with self.subTest(route=route):
                source = self.root / ("docs/pages/index.html" if route == "/" else "docs/pages" + route + ".html")
                self.assertTrue(source.is_file())
                self.assertEqual([self.origin + route], Canonicals(source.read_text()).urls)
                self.assertFalse(route.endswith(".html"))

    def test_sitemap_has_only_primary_urls_and_no_duplicates(self):
        tree = ET.parse(self.root / "docs/pages/sitemap.xml")
        urls = [node.text for node in tree.iter("{http://www.sitemaps.org/schemas/sitemap/0.9}loc")]
        self.assertEqual([self.origin + route for route in self.routes], urls)
        self.assertEqual(len(urls), len(set(urls)))

    def test_legacy_urls_redirect_once_and_never_from_canonical_to_legacy(self):
        rules = {}
        for line in (self.root / "docs/pages/_redirects").read_text().splitlines():
            if not line or line.startswith("#"):
                continue
            source, target, status = line.split()
            self.assertEqual("301", status)
            self.assertTrue(source.endswith(".html"))
            self.assertNotIn(source, self.routes)
            self.assertIn(target, self.routes)
            self.assertNotIn(source, rules)
            rules[source] = target
        self.assertEqual(len(self.routes), len(rules))
        for target in rules.values():
            self.assertNotIn(target, rules)

    def test_check_mode_detects_drift_without_modifying_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            relative = "docs/pages/privacy/ru/privacy.html"
            path = root / relative
            stale = path.read_text().replace(self.origin + self.contract["privacy"]["ru"],
                    self.origin + self.contract["privacy"]["ru"] + ".html")
            path.write_text(stale)
            self.assertIn(relative, sync_site_urls.sync(root))
            self.assertEqual(stale, path.read_text())
            sync_site_urls.sync(root, write=True)
            self.assertEqual([], sync_site_urls.sync(root))

    def test_external_origin_is_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            contract = dict(self.contract, origin="https://evil.example")
            (root / "config/site-urls.json").write_text(json.dumps(contract))
            before = (root / "docs/pages/sitemap.xml").read_bytes()
            with self.assertRaises(ValueError):
                sync_site_urls.sync(root, write=True)
            self.assertEqual(before, (root / "docs/pages/sitemap.xml").read_bytes())

    def test_missing_canonical_is_not_silently_invented(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            path = root / "docs/pages/privacy/en/privacy.html"
            path.write_text(path.read_text().replace('rel="canonical"', 'rel="alternate"'))
            with self.assertRaises(ValueError):
                sync_site_urls.sync(root)

    def test_encoded_external_or_html_contract_paths_are_rejected(self):
        for invalid in ["/privacy/ru/privacy.html", "/privacy/ru/%70rivacy", "/privacy/ru/../privacy",
                        "https://evil.example/policy", "/privacy/ru/privacy?x=1", "/privacy/en/privacy",
                        "/privacy/ru/a\\b", "/privacy/ru/политика"]:
            with self.subTest(path=invalid), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / "config").mkdir()
                contract = dict(self.contract, privacy=dict(self.contract["privacy"], ru=invalid))
                (root / "config/site-urls.json").write_text(json.dumps(contract))
                with self.assertRaises(ValueError):
                    sync_site_urls.load_contract(root)


if __name__ == "__main__":
    unittest.main()

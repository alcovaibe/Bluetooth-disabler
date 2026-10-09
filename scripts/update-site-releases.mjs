// Generates a small, same-origin release snapshot used by the static website.
// GitHub API access happens in Actions, not separately in every visitor's browser.
import { mkdir, readFile, writeFile } from 'node:fs/promises';

const repository = 'alcovaibe/Bluetooth-disabler';
const apiRoot = `https://api.github.com/repos/${repository}`;
const output = 'docs/pages/data/releases.json';
const token = process.env.GITHUB_TOKEN;

async function getJson(path) {
  const response = await fetch(apiRoot + path, {
    headers: {
      Accept: 'application/vnd.github+json',
      'User-Agent': 'BluetoothDisable-Site-Deploy',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    signal: AbortSignal.timeout(15000)
  });
  if (!response.ok) throw new Error(`GitHub API ${path}: HTTP ${response.status}`);
  return response.json();
}

function valid(item) {
  return item && item.draft === false && item.prerelease === false &&
    typeof item.tag_name === 'string' && item.tag_name.length > 0 &&
    typeof item.html_url === 'string' &&
    item.html_url.startsWith(`https://github.com/${repository}/releases/tag/`);
}

function summarize(item, includeAssets = false) {
  const summary = {
    id: item.id,
    tag_name: item.tag_name,
    published_at: item.published_at,
    html_url: item.html_url,
    draft: false,
    prerelease: false
  };
  if (includeAssets) {
    summary.assets = (Array.isArray(item.assets) ? item.assets : [])
      .filter(asset => typeof asset.name === 'string' && Number.isFinite(asset.size))
      .map(asset => ({
        name: asset.name,
        size: asset.size,
        digest: asset.digest || null,
        browser_download_url: asset.browser_download_url
      }));
  }
  return summary;
}

async function generate() {
  const latest = await getJson('/releases/latest');
  if (!valid(latest)) throw new Error('GitHub returned an invalid latest stable release');

  const all = [];
  // Keep history complete even if the number of releases exceeds one page.
  for (let page = 1; page <= 10; page += 1) {
    const chunk = await getJson(`/releases?per_page=100&page=${page}`);
    if (!Array.isArray(chunk)) throw new Error('GitHub returned an invalid release list');
    all.push(...chunk);
    if (chunk.length < 100) break;
  }

  const history = all.filter(item => valid(item) && item.id !== latest.id)
    .map(item => summarize(item));
  const snapshot = {
    schema: 1,
    generated_at: new Date().toISOString(),
    latest: summarize(latest, true),
    history
  };
  await mkdir('docs/pages/data', { recursive: true });
  await writeFile(output, JSON.stringify(snapshot, null, 2) + '\n', 'utf8');
  console.log(`Release snapshot: ${latest.tag_name}, ${history.length} prior stable releases`);
}

// Keep the checked-in snapshot if GitHub is temporarily unavailable during a deploy.
try {
  await generate();
} catch (error) {
  try {
    const existing = JSON.parse(await readFile(output, 'utf8'));
    if (!existing.latest?.tag_name || !Array.isArray(existing.history)) throw error;
    console.warn(`Releases refresh failed; using checked-in snapshot: ${error.message}`);
  } catch {
    console.error('Neither GitHub nor the checked-in release snapshot is available.');
    throw error;
  }
}

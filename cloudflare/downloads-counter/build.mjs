import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

export const REPOSITORY = 'alcovaibe/Bluetooth-disabler';
export const ORIGIN = 'https://bluetoothdisable-downloads.ivanzavalov391.workers.dev';
const API = `https://api.github.com/repos/${REPOSITORY}/releases`;
const OUTPUT = fileURLToPath(new URL('./public/', import.meta.url));

export async function countDownloads(fetcher = fetch, token = '') {
  const seen = new Set();
  let total = 0;
  for (let page = 1; page <= 100; page++) {
    const response = await fetcher(`${API}?per_page=100&page=${page}`, {
      headers: {
        Accept: 'application/vnd.github+json',
        'User-Agent': 'BluetoothDisable-download-counter',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      redirect: 'error',
      signal: AbortSignal.timeout(15000),
    });
    if (!response.ok) throw new Error(`GitHub release statistics HTTP ${response.status}`);
    const releases = await response.json();
    if (!Array.isArray(releases)) throw new Error('Invalid release list');
    for (const release of releases) {
      if (typeof release?.draft !== 'boolean') throw new Error('Invalid release metadata');
      if (release.draft) continue;
      if (!Array.isArray(release.assets)) throw new Error('Invalid release assets');
      // Prereleases are deliberately included, including the original v1.0.0.
      for (const asset of release.assets) {
        if (typeof asset?.name !== 'string') throw new Error('Invalid asset name');
        if (!/\.apk$/i.test(asset.name) || asset.state !== 'uploaded') continue;
        if (!Number.isSafeInteger(asset.id) || asset.id <= 0 ||
            !Number.isSafeInteger(asset.download_count) || asset.download_count < 0) {
          throw new Error('Invalid APK download statistics');
        }
        if (seen.has(asset.id)) continue;
        seen.add(asset.id);
        total += asset.download_count;
        if (!Number.isSafeInteger(total)) throw new Error('Download total overflow');
      }
    }
    if (releases.length < 100) return total;
  }
  throw new Error('Release pagination limit reached; refusing an incomplete count');
}

export function badge(total, language = 'ru') {
  if (!Number.isSafeInteger(total) || total < 0) throw new Error('Invalid total');
  const label = language === 'en' ? 'Download count' : 'Количество скачиваний';
  const left = language === 'en' ? 118 : 158;
  const right = Math.max(44, String(total).length * 7 + 20);
  const width = left + right;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="20" role="img" aria-label="${label}: ${total}"><title>${label}: ${total}</title><linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient><clipPath id="r"><rect width="${width}" height="20" rx="3"/></clipPath><g clip-path="url(#r)"><path fill="#555" d="M0 0h${left}v20H0z"/><path fill="#007ec6" d="M${left} 0h${right}v20h-${right}z"/><path fill="url(#s)" d="M0 0h${width}v20H0z"/></g><g fill="#fff" text-anchor="middle" font-family="Verdana,DejaVu Sans,sans-serif" font-size="11"><text x="${left / 2}" y="15" fill="#010101" fill-opacity=".3">${label}</text><text x="${left / 2}" y="14">${label}</text><text x="${left + right / 2}" y="15" fill="#010101" fill-opacity=".3">${total}</text><text x="${left + right / 2}" y="14">${total}</text></g></svg>\n`;
}

export async function build(output = OUTPUT, fetcher = fetch, token = '') {
  // Complete the API traversal before touching published output. Errors never become zero.
  const totalDownloads = await countDownloads(fetcher, token);
  const snapshot = {
    totalDownloads,
    updatedAt: new Date().toISOString(),
    repository: REPOSITORY,
    includesPrereleases: true,
    assetType: 'apk',
  };
  await mkdir(output, { recursive: true });
  await writeFile(path.join(output, 'downloads.svg'), badge(totalDownloads));
  await writeFile(path.join(output, 'downloads-en.svg'), badge(totalDownloads, 'en'));
  await writeFile(path.join(output, 'downloads.json'), JSON.stringify(snapshot, null, 2) + '\n');
  await writeFile(path.join(output, '_headers'), '/*\n  Cache-Control: public, max-age=300, must-revalidate\n  X-Content-Type-Options: nosniff\n');
  console.log(`Counted ${totalDownloads} public APK downloads (including prereleases).`);
  return snapshot;
}

async function verifyLive() {
  for (const file of ['downloads.json', 'downloads.svg', 'downloads-en.svg']) {
    const expected = await readFile(path.join(OUTPUT, file), 'utf8');
    const response = await fetch(`${ORIGIN}/${file}`, {
      headers: { 'Cache-Control': 'no-cache' },
      redirect: 'error', signal: AbortSignal.timeout(15000),
    });
    if (response.status !== 200 || await response.text() !== expected) {
      throw new Error(`Published counter does not match generated ${file}`);
    }
    const type = file.endsWith('.svg') ? 'image/svg+xml' : 'application/json';
    if (!response.headers.get('Content-Type')?.includes(type)) throw new Error(`Wrong MIME type: ${file}`);
  }
  console.log('Live download counter JSON and both localized badges verified.');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.includes('--verify-live')) await verifyLive();
  else await build(OUTPUT, fetch, process.env.GITHUB_TOKEN || '');
}

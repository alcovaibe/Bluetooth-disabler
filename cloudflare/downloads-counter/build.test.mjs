import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { badge, build, countDownloads } from './build.mjs';

const asset = (id, name, download_count, state = 'uploaded') => ({ id, name, download_count, state });
const release = (assets, extras = {}) => ({ draft: false, prerelease: false, assets, ...extras });
const response = data => new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } });

test('counts APKs across stable and prereleases, not QR/provisioning files or drafts', async () => {
  const data = [
    release([asset(1, 'BluetoothDisable-v1.4.0.apk', 7), asset(2, 'qr.png', 90), asset(3, 'provisioning.json', 40)]),
    release([asset(4, 'NoBluetooth-v1.0.0.apk.apk', 11)], { prerelease: true }),
    release([asset(5, '1.0.4.APK', 13), asset(6, 'APK.zip', 30), asset(7, 'pending.apk', 99, 'new')]),
    release([asset(8, 'private-draft.apk', 800)], { draft: true }),
  ];
  const total = await countDownloads(async url => {
    assert.equal(url, 'https://api.github.com/repos/alcovaibe/Bluetooth-disabler/releases?per_page=100&page=1');
    return response(data);
  });
  assert.equal(total, 31);
});

test('paginates beyond 100 releases and never counts an asset twice', async () => {
  const first = Array.from({ length: 100 }, (_, i) => release([asset(i + 1, `${i}.apk`, 1)]));
  const calls = [];
  const total = await countDownloads(async url => {
    const page = new URL(url).searchParams.get('page');
    calls.push(page);
    return response(page === '1' ? first : [release([asset(100, '99.apk', 1), asset(101, 'older.apk', 5)])]);
  });
  assert.equal(total, 105);
  assert.deepEqual(calls, ['1', '2']);
});

test('only fetches fixed metadata URLs, never APKs or QR files; authentication is optional', async () => {
  await countDownloads(async (url, options) => {
    assert.equal(new URL(url).hostname, 'api.github.com');
    assert.equal(new URL(url).pathname, '/repos/alcovaibe/Bluetooth-disabler/releases');
    assert.equal(options.headers.Authorization, 'Bearer fixture-token');
    assert.equal(options.redirect, 'error');
    return response([release([])]);
  }, 'fixture-token');
});

test('rejects rate limits and malformed or negative counts instead of displaying zero', async () => {
  await assert.rejects(countDownloads(async () => new Response('', { status: 403 })), /HTTP 403/);
  for (const data of [{}, [{ draft: false }], [release([asset(1, 'app.apk', -1)])],
    [release([asset(1, 'app.apk', '7')])], [release([asset(1, 'app.apk', Number.MAX_SAFE_INTEGER), asset(2, 'old.apk', 1)])]]) {
    await assert.rejects(countDownloads(async () => response(data)));
  }
});

test('failed pagination leaves previously generated counter unchanged', async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'apk-counter-'));
  try {
    await writeFile(path.join(dir, 'downloads.svg'), 'previous badge');
    await writeFile(path.join(dir, 'downloads.json'), 'previous snapshot');
    let calls = 0;
    await assert.rejects(build(dir, async () => ++calls === 1
      ? response(Array.from({ length: 100 }, () => release([])))
      : new Response('', { status: 502 })));
    assert.equal(await readFile(path.join(dir, 'downloads.svg'), 'utf8'), 'previous badge');
    assert.equal(await readFile(path.join(dir, 'downloads.json'), 'utf8'), 'previous snapshot');
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('both localized badges use the same exact total and snapshot excludes tokens', async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'apk-counter-'));
  try {
    const snapshot = await build(dir, async () => response([release([asset(1, 'app.apk', 1234)])]), 'fixture-secret');
    const json = await readFile(path.join(dir, 'downloads.json'), 'utf8');
    assert.equal(JSON.parse(json).totalDownloads, 1234);
    assert.ok(Number.isFinite(Date.parse(snapshot.updatedAt)));
    assert.ok(!json.includes('fixture-secret'));
    assert.ok((await readFile(path.join(dir, 'downloads.svg'), 'utf8')).includes('Скачивания APK: 1234'));
    assert.ok((await readFile(path.join(dir, 'downloads-en.svg'), 'utf8')).includes('APK downloads: 1234'));
    assert.ok((await readFile(path.join(dir, '_headers'), 'utf8')).includes('max-age=300'));
    assert.throws(() => badge(-1));
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('the counter deploy stays separate from the public site and private API', async () => {
  const config = JSON.parse(await readFile(new URL('./wrangler.jsonc', import.meta.url), 'utf8'));
  assert.equal(config.name, 'bluetoothdisable-downloads');
  assert.equal(config.assets.directory, './public');
  for (const field of ['main', 'route', 'routes', 'services', 'kv_namespaces', 'vars']) {
    assert.equal(config[field], undefined);
  }
  const workflow = await readFile(new URL('../../.github/workflows/downloads-counter.yml', import.meta.url), 'utf8');
  assert.ok(workflow.includes("cron: '23 * * * *'"));
  assert.ok(workflow.includes("github.event_name != 'pull_request'"));
  assert.ok(workflow.includes('contents: read'));
  for (const filename of ['README.md', 'README_EN.md']) {
    const readme = await readFile(new URL(`../../${filename}`, import.meta.url), 'utf8');
    assert.equal((readme.match(/!\[[^\]]*\]\(https:\/\/bluetoothdisable-downloads\./g) || []).length, 1);
    assert.ok(!readme.includes('/latest/downloads'));
  }
});

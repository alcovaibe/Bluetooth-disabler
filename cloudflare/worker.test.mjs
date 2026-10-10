import assert from 'node:assert/strict';
import { createHash, webcrypto } from 'node:crypto';
import test from 'node:test';
import worker from './worker.mjs';

globalThis.crypto ??= webcrypto;
const token = 'a'.repeat(43); // Synthetic fixture, never a production credential.
const key = `token:${createHash('sha256').update(token).digest('hex')}`;
const request = (headers = { Authorization: `Bearer ${token}` }, method = 'GET', path = '/api/test-config') =>
  new Request(`https://bluetoothdisable.app${path}`, { method, headers });
function environment(config = { allowLauncherWithoutDeviceOwner: true, validForSeconds: 900 }, record = { enabled: true }) {
  const reads = [];
  return {
    reads,
    TEST_CONFIG: { async get(name) { reads.push(name); return name === key ? record : config; } },
    ASSETS: { async fetch() { return new Response('site'); } },
  };
}

test('unauthenticated, query-string, malformed and unknown tokens cannot read config', async () => {
  const env = environment();
  for (const headers of [{}, { Authorization: 'Bearer short' }, { Authorization: 'Basic ignored' }]) {
    assert.equal((await worker.fetch(request(headers), env)).status, 401);
  }
  assert.equal((await worker.fetch(request({}, 'GET', `/api/test-config?token=${token}`), env)).status, 401);
  assert.deepEqual(env.reads, []);
  const unknown = await worker.fetch(request({ Authorization: `Bearer ${'b'.repeat(43)}` }), environment(null, null));
  assert.equal(unknown.status, 401);
});

test('authorized response exposes only a bounded feature flag, with no caching', async () => {
  const env = environment();
  const response = await worker.fetch(request(), env);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('Cache-Control'), 'private, no-store');
  assert.equal(response.headers.get('X-Content-Type-Options'), 'nosniff');
  assert.deepEqual(await response.json(), { allowLauncherWithoutDeviceOwner: true, validForSeconds: 900 });
  assert.deepEqual(env.reads, [key, 'launcher-test']);
});

test('revoked and non-boolean token records are rejected before reading settings', async () => {
  for (const record of [null, { enabled: false }, { enabled: 'true' }]) {
    const env = environment(undefined, record);
    assert.equal((await worker.fetch(request(), env)).status, 401);
    assert.deepEqual(env.reads, [key]);
  }
});

test('missing, disabled and non-boolean settings deny access', async () => {
  for (const config of [null, {}, { allowLauncherWithoutDeviceOwner: false }, { allowLauncherWithoutDeviceOwner: 'true' }]) {
    const response = await worker.fetch(request(), environment(config));
    assert.deepEqual(await response.json(), { allowLauncherWithoutDeviceOwner: false, validForSeconds: 0 });
  }
});

test('out-of-range and malformed TTLs never grant access', async () => {
  for (const ttl of [undefined, null, 0, -1, 901, 1.5, '900']) {
    const response = await worker.fetch(request(), environment({ allowLauncherWithoutDeviceOwner: true, validForSeconds: ttl }));
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: 'invalid_configuration' });
  }
});

test('missing binding and KV failure deny access without leaking errors', async () => {
  assert.equal((await worker.fetch(request(), {})).status, 503);
  const response = await worker.fetch(request(), { TEST_CONFIG: { get() { throw new Error('sensitive'); } } });
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: 'unavailable' });
});

test('API rejects writes, unknown routes and encoded alternate paths', async () => {
  for (const method of ['POST', 'PUT', 'DELETE', 'HEAD', 'OPTIONS']) {
    const response = await worker.fetch(request(undefined, method), environment());
    assert.equal(response.status, 405);
    assert.equal(response.headers.get('Allow'), 'GET');
  }
  assert.equal((await worker.fetch(request({}, 'GET', '/api/other'), environment())).status, 404);
  assert.equal((await worker.fetch(request({}, 'GET', '/api/%74est-config'), environment())).status, 404);
});

test('site and public placeholder still use static assets', async () => {
  for (const path of ['/', '/assets/css/main.css', '/config/app-config.json']) {
    assert.equal(await (await worker.fetch(request({}, 'GET', path), environment())).text(), 'site');
  }
});

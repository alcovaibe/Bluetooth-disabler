import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { deployConfig } from './create-cloudflare-deploy-config.mjs';

test('deployment requires a real namespace ID, without placeholders or environment interpolation', () => {
  for (const id of [undefined, '', 'REPLACE_ME', '${KV_ID}', 'x'.repeat(32)]) {
    assert.throws(() => deployConfig({}, id), /CLOUDFLARE_TEST_CONFIG_KV_ID/);
  }
});

test('generated config preserves site and protected API routing', () => {
  const base = JSON.parse(readFileSync(new URL('../wrangler.jsonc', import.meta.url), 'utf8'));
  const config = deployConfig(base, '1'.repeat(32));
  assert.equal(config.main, 'cloudflare/worker.mjs');
  assert.equal(config.assets.directory, './docs/pages');
  assert.equal(config.assets.binding, 'ASSETS');
  assert.deepEqual(config.assets.run_worker_first, ['/api/*']);
  assert.deepEqual(config.kv_namespaces, [{ binding: 'TEST_CONFIG', id: '1'.repeat(32) }]);
  assert.equal(base.kv_namespaces, undefined);
});

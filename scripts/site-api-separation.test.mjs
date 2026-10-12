import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import test from 'node:test';

test('public site remains an assets-only Worker without API routing or storage', () => {
  const config = JSON.parse(readFileSync('wrangler.jsonc', 'utf8'));
  assert.equal(config.name, 'bluetoothdisable');
  assert.deepEqual(config.assets, { directory: './docs/pages' });
  for (const field of ['main', 'routes', 'route', 'kv_namespaces', 'vars', 'services']) {
    assert.equal(config[field], undefined, `Public site must not define ${field}`);
  }
});

test('site deploy does not read KV or generate a merged API Worker', () => {
  const workflow = readFileSync('.github/workflows/pages.yml', 'utf8');
  assert.ok(workflow.includes('command: deploy\n'));
  for (const marker of ['CLOUDFLARE_TEST_CONFIG_KV_ID', 'wrangler.deploy.json', 'create-cloudflare-deploy-config', 'cloudflare/worker.mjs']) {
    assert.ok(!workflow.includes(marker), `Obsolete combined deployment: ${marker}`);
  }
  assert.equal(existsSync('cloudflare/worker.mjs'), false);
  assert.equal(existsSync('scripts/create-cloudflare-deploy-config.mjs'), false);
});

test('already-installed internal APK endpoint stays unchanged', () => {
  const client = readFileSync('app/src/internal/java/com/pulse/bluetoothdisable/testaccess/TestConfigClient.kt', 'utf8');
  assert.ok(client.includes('https://bluetoothdisable.app/api/test-config'));
  assert.ok(client.includes('Authorization'));
  assert.ok(client.includes('allowLauncherWithoutDeviceOwner'));
  assert.ok(client.includes('validForSeconds'));
});

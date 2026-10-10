import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function deployConfig(base, namespaceId) {
  if (!/^[a-f0-9]{32}$/i.test(namespaceId ?? '')) {
    throw new Error('Set repository variable CLOUDFLARE_TEST_CONFIG_KV_ID to the 32-character KV namespace ID before deploying.');
  }
  return {
    ...base,
    kv_namespaces: [{ binding: 'TEST_CONFIG', id: namespaceId }],
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const base = JSON.parse(readFileSync('wrangler.jsonc', 'utf8'));
  writeFileSync('wrangler.deploy.json', JSON.stringify(deployConfig(base, process.env.CLOUDFLARE_TEST_CONFIG_KV_ID), null, 2) + '\n');
}

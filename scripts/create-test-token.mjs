import { createHash, randomBytes } from 'node:crypto';

// Run locally. Save the token privately; never commit or paste this output into a PR.
const token = randomBytes(32).toString('base64url');
console.log(JSON.stringify({
  token,
  kvKey: `token:${createHash('sha256').update(token).digest('hex')}`,
  kvValue: { enabled: true },
}, null, 2));

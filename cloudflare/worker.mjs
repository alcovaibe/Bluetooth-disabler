const CONFIG_PATH = '/api/test-config';
const MAX_VALID_SECONDS = 900;
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;

function json(body, status = 200) {
  return Response.json(body, {
    status,
    headers: {
      'Cache-Control': 'private, no-store',
      'X-Content-Type-Options': 'nosniff',
    },
  });
}

async function tokenKey(token) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token));
  const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  return `token:${hash}`;
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    if (pathname !== CONFIG_PATH) {
      // Do not turn unknown API paths into an HTML response or expose KV data.
      if (pathname.startsWith('/api/')) return json({ error: 'not_found' }, 404);
      return env.ASSETS.fetch(request);
    }
    if (request.method !== 'GET') {
      const response = json({ error: 'method_not_allowed' }, 405);
      response.headers.set('Allow', 'GET');
      return response;
    }
    const authorization = request.headers.get('Authorization') ?? '';
    const match = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(authorization);
    if (!match || !TOKEN_PATTERN.test(match[1])) return json({ error: 'unauthorized' }, 401);
    if (!env.TEST_CONFIG) return json({ error: 'unavailable' }, 503);

    try {
      const record = await env.TEST_CONFIG.get(await tokenKey(match[1]), { type: 'json', cacheTtl: 60 });
      if (record?.enabled !== true) return json({ error: 'unauthorized' }, 401);
      const config = await env.TEST_CONFIG.get('launcher-test', { type: 'json', cacheTtl: 60 });
      if (config?.allowLauncherWithoutDeviceOwner !== true) {
        return json({ allowLauncherWithoutDeviceOwner: false, validForSeconds: 0 });
      }
      // Malformed or excessive leases must never silently grant access.
      const ttl = config.validForSeconds;
      if (!Number.isInteger(ttl) || ttl < 1 || ttl > MAX_VALID_SECONDS) {
        return json({ error: 'invalid_configuration' }, 503);
      }
      return json({ allowLauncherWithoutDeviceOwner: true, validForSeconds: ttl });
    } catch {
      // Never log a request, Authorization header, token record or exception payload.
      return json({ error: 'unavailable' }, 503);
    }
  },
};

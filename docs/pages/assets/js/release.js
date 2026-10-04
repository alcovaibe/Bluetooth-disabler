(() => {
  'use strict';
  const api = 'https://api.github.com/repos/alcovaibe/Bluetooth-disabler/releases/latest';
  const releasesUrl = 'https://github.com/alcovaibe/Bluetooth-disabler/releases';
  let release = null;
  let failed = false;
  let pending = null;
  const status = document.getElementById('release');
  if (!status) return;
  function render() {
    const i18n = window.BluetoothDisableI18n;
    const translate = key => i18n.translate(key);
    const language = i18n.language === 'en' ? 'en-US' : 'ru-RU';
    const apk = release?.assets?.find(asset => /\.apk$/i.test(asset.name || '') && Number.isFinite(asset.size) && asset.size > 0);
    document.getElementById('release-version').textContent = release?.tag_name || translate('unavailable');
    const date = release?.published_at ? new Date(release.published_at) : null;
    document.getElementById('release-date').textContent = date && !Number.isNaN(date.getTime())
      ? new Intl.DateTimeFormat(language, { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' }).format(date) : translate('unavailable');
    document.getElementById('release-size').textContent = apk
      ? `${new Intl.NumberFormat(language, { maximumFractionDigits: 2 }).format(apk.size / 1024 / 1024)} MiB` : translate('unavailable');
    const link = document.getElementById('release-link');
    // Only accept canonical release pages for this repository.
    link.href = release?.html_url?.startsWith(`${releasesUrl}/tag/`) ? release.html_url : releasesUrl;
    const statusKey = failed ? 'releaseError' : release ? (apk ? null : 'releaseNoApk') : 'releaseStatus';
    status.textContent = statusKey ? translate(statusKey) : '';
    status.hidden = !statusKey;
  }
  document.addEventListener('languagechange', render);
  render();
  // Share in-flight requests with the QR dialog. Every dialog opening refreshes
  // release metadata so an already open website can discover a new release.
  function load() {
    if (pending) return pending;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    pending = fetch(api, { signal: controller.signal, cache: 'no-store', headers: { Accept: 'application/vnd.github+json' }, credentials: 'omit' })
      .then(response => { if (!response.ok) throw new Error(`HTTP ${response.status}`); return response.json(); })
      .then(data => {
        if (!data || typeof data.tag_name !== 'string' || !data.tag_name || !Array.isArray(data.assets)) throw new Error('Invalid release');
        release = data;
        failed = false;
        return data;
      })
      .catch(error => { failed = true; throw error; })
      .finally(() => { clearTimeout(timeout); pending = null; render(); });
    return pending;
  }
  window.BluetoothDisableRelease = { load };
  load().catch(() => { /* The release card displays the error; QR can retry. */ });
})();

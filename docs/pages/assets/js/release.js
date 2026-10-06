(() => {
  'use strict';

  const api = 'https://api.github.com/repos/alcovaibe/Bluetooth-disabler/releases?per_page=100';
  const releasesUrl = 'https://github.com/alcovaibe/Bluetooth-disabler/releases';
  let release = null;
  let previousReleases = [];
  let failed = false;
  let pending = null;

  const status = document.getElementById('release');
  const historyList = document.getElementById('release-history-list');
  const historyStatus = document.getElementById('release-history-status');
  if (!status) return;

  function isPublishedStable(item) {
    return item
      && item.draft === false
      && item.prerelease === false
      && typeof item.tag_name === 'string'
      && item.tag_name
      && Array.isArray(item.assets)
      && typeof item.html_url === 'string'
      && item.html_url.startsWith(`${releasesUrl}/tag/`);
  }

  function formatDate(value, language) {
    const date = value ? new Date(value) : null;
    if (!date || Number.isNaN(date.getTime())) return null;
    return {
      machine: date.toISOString().slice(0, 10),
      display: new Intl.DateTimeFormat(language, {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        timeZone: 'UTC'
      }).format(date)
    };
  }

  function renderHistory(translate, language) {
    if (!historyList || !historyStatus) return;

    historyList.replaceChildren();
    historyStatus.hidden = true;
    historyStatus.textContent = '';

    if (failed) {
      historyStatus.textContent = translate('releaseHistoryError');
      historyStatus.hidden = false;
      return;
    }

    if (!release) return;

    if (previousReleases.length === 0) {
      historyStatus.textContent = translate('releaseHistoryEmpty');
      historyStatus.hidden = false;
      return;
    }

    for (const item of previousReleases) {
      const card = document.createElement('article');
      card.className = 'card release-card';

      const top = document.createElement('div');
      top.className = 'release-card-top';

      const title = document.createElement('h3');
      title.textContent = item.tag_name;

      const dateInfo = formatDate(item.published_at, language);
      const time = document.createElement('time');
      if (dateInfo) {
        time.dateTime = dateInfo.machine;
        time.textContent = dateInfo.display;
      } else {
        time.textContent = translate('unavailable');
      }

      const link = document.createElement('a');
      link.className = 'inline-link';
      link.href = item.html_url;
      link.textContent = translate('releaseNotes');

      top.append(title, time);
      card.append(top, link);
      historyList.append(card);
    }
  }

  function render() {
    const i18n = window.BluetoothDisableI18n;
    const translate = key => i18n.translate(key);
    const language = i18n.language === 'en' ? 'en-US' : 'ru-RU';

    const apk = release?.assets?.find(asset =>
      /\.apk$/i.test(asset.name || '') &&
      Number.isFinite(asset.size) &&
      asset.size > 0
    );

    document.getElementById('release-version').textContent = release?.tag_name || translate('unavailable');

    const dateInfo = formatDate(release?.published_at, language);
    document.getElementById('release-date').textContent = dateInfo?.display || translate('unavailable');

    document.getElementById('release-size').textContent = apk
      ? `${new Intl.NumberFormat(language, { maximumFractionDigits: 2 }).format(apk.size / 1024 / 1024)} MiB`
      : translate('unavailable');

    const link = document.getElementById('release-link');
    link.href = release?.html_url?.startsWith(`${releasesUrl}/tag/`) ? release.html_url : releasesUrl;

    const statusKey = failed
      ? 'releaseError'
      : release
        ? (apk ? null : 'releaseNoApk')
        : 'releaseStatus';

    status.textContent = statusKey ? translate(statusKey) : '';
    status.hidden = !statusKey;

    renderHistory(translate, language);
  }

  document.addEventListener('languagechange', render);
  render();

  // One GitHub API request provides both the current stable release and the
  // complete previous stable release list. QR provisioning reuses the same
  // in-flight request and refreshes it whenever the QR dialog is opened.
  function load() {
    if (pending) return pending;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);

    pending = fetch(api, {
      signal: controller.signal,
      cache: 'no-store',
      headers: { Accept: 'application/vnd.github+json' },
      credentials: 'omit'
    })
      .then(response => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
      })
      .then(data => {
        if (!Array.isArray(data)) throw new Error('Invalid releases response');

        const stable = data.filter(isPublishedStable);
        if (stable.length === 0) throw new Error('No stable release');

        release = stable[0];
        previousReleases = stable.slice(1);
        failed = false;
        return release;
      })
      .catch(error => {
        failed = true;
        throw error;
      })
      .finally(() => {
        clearTimeout(timeout);
        pending = null;
        render();
      });

    return pending;
  }

  window.BluetoothDisableRelease = { load };
  load().catch(() => { /* Release cards display the error; QR can retry. */ });
})();

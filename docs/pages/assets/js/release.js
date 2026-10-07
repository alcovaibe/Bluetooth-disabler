(() => {
  'use strict';

  const latestApi = 'https://api.github.com/repos/alcovaibe/Bluetooth-disabler/releases/latest';
  const historyApi = 'https://api.github.com/repos/alcovaibe/Bluetooth-disabler/releases?per_page=100';
  const releasesUrl = 'https://github.com/alcovaibe/Bluetooth-disabler/releases';

  let release = null;
  let failed = false;
  let pending = null;

  let history = null;
  let historyFailed = false;
  let historyPending = null;

  const status = document.getElementById('release');
  const historyDetails = document.querySelector('.release-history');
  const historyList = document.getElementById('release-history-list');
  const historyStatus = document.getElementById('release-history-status');

  if (!status) return;

  function language() {
    return window.BluetoothDisableI18n.language === 'en' ? 'en-US' : 'ru-RU';
  }

  function formatDate(value) {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime())
      ? new Intl.DateTimeFormat(language(), {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          timeZone: 'UTC'
        }).format(date)
      : '';
  }

  function isCanonicalReleaseUrl(url) {
    return typeof url === 'string' && url.startsWith(`${releasesUrl}/tag/`);
  }

  function render() {
    const i18n = window.BluetoothDisableI18n;
    const translate = key => i18n.translate(key);
    const apk = release?.assets?.find(
      asset => /\.apk$/i.test(asset.name || '') && Number.isFinite(asset.size) && asset.size > 0
    );

    document.getElementById('release-version').textContent =
      release?.tag_name || translate('unavailable');

    document.getElementById('release-date').textContent =
      formatDate(release?.published_at) || translate('unavailable');

    document.getElementById('release-size').textContent = apk
      ? `${new Intl.NumberFormat(language(), { maximumFractionDigits: 2 }).format(apk.size / 1024 / 1024)} MiB`
      : translate('unavailable');

    const link = document.getElementById('release-link');
    link.href = isCanonicalReleaseUrl(release?.html_url) ? release.html_url : releasesUrl;

    const statusKey = failed
      ? 'releaseError'
      : release
        ? (apk ? null : 'releaseNoApk')
        : 'releaseStatus';

    status.textContent = statusKey ? translate(statusKey) : '';
    status.hidden = !statusKey;
  }

  function createHistoryCard(item) {
    const translate = key => window.BluetoothDisableI18n.translate(key);

    const article = document.createElement('article');
    article.className = 'card release-card';

    const top = document.createElement('div');
    top.className = 'release-card-top';

    const title = document.createElement('h3');
    title.textContent = item.tag_name;

    const time = document.createElement('time');
    const date = item.published_at ? new Date(item.published_at) : null;
    if (date && !Number.isNaN(date.getTime())) {
      time.dateTime = date.toISOString().slice(0, 10);
      time.textContent = formatDate(item.published_at);
    }

    const link = document.createElement('a');
    link.className = 'inline-link';
    link.textContent = translate('releaseNotes');
    link.href = isCanonicalReleaseUrl(item.html_url) ? item.html_url : releasesUrl;

    top.append(title, time);
    article.append(top, link);
    return article;
  }

  function renderHistoryLoading() {
    if (!historyList || !historyStatus) return;

    historyStatus.textContent = '';
    historyStatus.hidden = true;
    historyList.replaceChildren();

    for (let i = 0; i < 2; i += 1) {
      const article = document.createElement('article');
      article.className = 'card release-card release-card--loading';
      article.setAttribute('aria-hidden', 'true');

      const top = document.createElement('div');
      top.className = 'release-card-top';

      const title = document.createElement('span');
      title.className = 'release-skeleton release-skeleton--title';

      const date = document.createElement('span');
      date.className = 'release-skeleton release-skeleton--date';

      const link = document.createElement('span');
      link.className = 'release-skeleton release-skeleton--link';

      top.append(title, date);
      article.append(top, link);
      historyList.append(article);
    }
  }

  function renderHistory() {
    if (!historyList || !historyStatus) return;

    const translate = key => window.BluetoothDisableI18n.translate(key);

    if (historyPending && !history) {
      renderHistoryLoading();
      return;
    }

    historyList.replaceChildren();

    if (historyFailed) {
      historyStatus.textContent = translate('releaseHistoryError');
      historyStatus.hidden = false;
      return;
    }

    if (!history) {
      historyStatus.textContent = '';
      historyStatus.hidden = true;
      return;
    }

    if (history.length === 0) {
      historyStatus.textContent = translate('releaseHistoryEmpty');
      historyStatus.hidden = false;
      return;
    }

    historyStatus.textContent = '';
    historyStatus.hidden = true;
    history.forEach(item => historyList.append(createHistoryCard(item)));
  }

  document.addEventListener('languagechange', () => {
    render();
    renderHistory();
  });

  render();

  function load() {
    if (pending) return pending;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);

    pending = fetch(latestApi, {
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
        if (!data || typeof data.tag_name !== 'string' || !data.tag_name || !Array.isArray(data.assets)) {
          throw new Error('Invalid release');
        }
        release = data;
        failed = false;
        return data;
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

  function loadHistory() {
    if (history) return Promise.resolve(history);
    if (historyPending) return historyPending;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);

    historyFailed = false;
    renderHistoryLoading();

    historyPending = Promise.allSettled([
      load(),
      fetch(historyApi, {
        signal: controller.signal,
        cache: 'no-store',
        headers: { Accept: 'application/vnd.github+json' },
        credentials: 'omit'
      }).then(response => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
      })
    ])
      .then(results => {
        const listResult = results[1];
        if (listResult.status !== 'fulfilled' || !Array.isArray(listResult.value)) {
          throw new Error('Invalid release history');
        }

        const stable = listResult.value.filter(item =>
          item &&
          item.draft === false &&
          item.prerelease === false &&
          typeof item.tag_name === 'string' &&
          item.tag_name &&
          isCanonicalReleaseUrl(item.html_url)
        );

        const currentId = release?.id;
        const currentTag = release?.tag_name;

        if (currentId || currentTag) {
          history = stable.filter(item =>
            currentId ? item.id !== currentId : item.tag_name !== currentTag
          );
        } else {
          history = stable.slice(1);
        }

        historyFailed = false;
        return history;
      })
      .catch(error => {
        historyFailed = true;
        throw error;
      })
      .finally(() => {
        clearTimeout(timeout);
        historyPending = null;
        renderHistory();
      });

    return historyPending;
  }

  historyDetails?.addEventListener('toggle', () => {
    if (historyDetails.open) loadHistory().catch(() => {});
  });

  window.BluetoothDisableRelease = { load };
  load().catch(() => { /* The release card displays the error; QR can retry. */ });
})();

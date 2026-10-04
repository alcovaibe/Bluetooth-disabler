(() => {
  'use strict';
  const dialog = document.getElementById('qr-dialog');
  const button = document.getElementById('qr');
  if (!dialog || !button) return;
  const preview = document.getElementById('qr-preview');
  const image = document.getElementById('qr-image');
  const status = document.getElementById('qr-status');
  const version = document.getElementById('qr-version');
  const retry = document.getElementById('qr-retry');
  const downloadsUrl = 'https://github.com/alcovaibe/Bluetooth-disabler/releases/download/';
  let statusKey = 'qrLoading';
  let currentVersion = '';
  let requestId = 0;

  function render() {
    const translate = window.BluetoothDisableI18n.translate;
    status.textContent = translate(statusKey);
    version.textContent = `${translate('qrVersion')} ${currentVersion}`;
    image.firstElementChild?.setAttribute('aria-label', translate('qrImageAlt'));
    if (image.firstElementChild?.tagName === 'IMG') image.firstElementChild.alt = translate('qrImageAlt');
  }

  function assetUrl(asset, release) {
    const url = asset?.browser_download_url;
    // Use assets belonging to this exact release and repository only.
    return typeof url === 'string' && url.startsWith(`${downloadsUrl}${encodeURIComponent(release.tag_name)}/`) ? url : null;
  }

  function generate(release) {
    const apk = release.assets.find(asset => /^BluetoothDisable.*\.apk$/i.test(asset.name || '') && assetUrl(asset, release));
    if (!apk || !/^sha256:[a-f0-9]{64}$/i.test(apk.digest || '')) throw new Error('No APK checksum');
    const bytes = apk.digest.slice(7).match(/../g).map(byte => parseInt(byte, 16));
    const checksum = btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const payload = {
      'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME': 'com.pulse.bluetoothdisable/.admin.AppDeviceAdminReceiver',
      'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION': assetUrl(apk, release),
      'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM': checksum
    };
    const qr = window.qrcode(0, 'M');
    qr.addData(JSON.stringify(payload), 'Byte');
    qr.make();
    // Four white modules on every side are the scanner's quiet zone.
    // Render at an integer module size; never recolor QR for the dark theme.
    const cells = qr.getModuleCount();
    const scale = 8;
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = (cells + 8) * scale;
    canvas.setAttribute('role', 'img');
    const context = canvas.getContext('2d');
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = '#000';
    for (let row = 0; row < cells; row++) {
      for (let column = 0; column < cells; column++) {
        if (qr.isDark(row, column)) context.fillRect((column + 4) * scale, (row + 4) * scale, scale, scale);
      }
    }
    return canvas;
  }

  function loadImage(url) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      const timeout = setTimeout(() => { img.src = ''; reject(new Error('QR image timeout')); }, 8000);
      img.onload = () => { clearTimeout(timeout); resolve(img); };
      img.onerror = () => { clearTimeout(timeout); reject(new Error('QR image unavailable')); };
      img.src = url;
    });
  }

  async function loadQr() {
    const id = ++requestId;
    preview.hidden = true;
    image.replaceChildren();
    retry.hidden = true;
    currentVersion = '';
    statusKey = 'qrLoading';
    status.setAttribute('aria-busy', 'true');
    render();
    try {
      const release = await window.BluetoothDisableRelease.load();
      if (id !== requestId) return;
      const asset = release.assets.find(asset => asset.name === 'bluetooth-disable-device-owner-qr.png' && assetUrl(asset, release));
      let code;
      if (asset) {
        try { code = await loadImage(assetUrl(asset, release)); }
        catch { code = generate(release); }
      } else {
        // Older releases have only APK assets. GitHub's SHA-256 digest lets us
        // create the same provisioning payload without downloading the APK.
        code = generate(release);
      }
      if (id !== requestId) return;
      image.replaceChildren(code);
      currentVersion = release.tag_name;
      statusKey = 'qrReady';
      preview.hidden = false;
    } catch {
      if (id !== requestId) return;
      statusKey = 'qrError';
      retry.hidden = false;
    } finally {
      if (id === requestId) { status.setAttribute('aria-busy', 'false'); render(); }
    }
  }

  document.addEventListener('languagechange', render);
  retry.addEventListener('click', loadQr);
  button.addEventListener('click', () => {
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
    loadQr();
  });
  // Ignore results from requests belonging to an already closed dialog.
  dialog.addEventListener('close', () => { requestId++; });
  document.getElementById('qr-close')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => {
    const rect = dialog.getBoundingClientRect();
    if (event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) dialog.close();
  });
})();

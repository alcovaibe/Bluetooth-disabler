(() => {
  'use strict';
  let saved;
  try { saved = localStorage.getItem('lang'); } catch { /* Storage may be disabled. */ }
  let current = ['ru', 'en'].includes(saved) ? saved : 'ru';
  function translate(key) {
    return window.BluetoothDisableTranslations[current][key] || key;
  }
  function setLanguage(value) {
    current = value === 'en' ? 'en' : 'ru';
    document.documentElement.lang = current;
    document.querySelectorAll('[data-i18n]').forEach(element => {
      const text = window.BluetoothDisableTranslations[current][element.dataset.i18n];
      if (text !== undefined) element.textContent = text;
    });
    const select = document.getElementById('lang');
    if (select) { select.value = current; select.setAttribute('aria-label', translate('languageLabel')); }
    document.getElementById('theme')?.setAttribute('aria-label', translate('themeLabel'));
    document.getElementById('menu')?.setAttribute('aria-label', translate('menuOpen'));
    document.getElementById('menu-close')?.setAttribute('aria-label', translate('menuClose'));
    document.getElementById('navigation')?.setAttribute('aria-label', translate('navLabel'));
    document.getElementById('qr-close')?.setAttribute('aria-label', translate('dialogClose'));
    document.querySelectorAll('#privacy-link, #footer-privacy').forEach(link => {
      link.href = `privacy/${current}/privacy.html`;
    });
    document.title = translate('pageTitle');
    document.querySelector('meta[name="description"]')?.setAttribute('content', translate('pageDescription'));
    document.querySelector('meta[property="og:title"]')?.setAttribute('content', translate('pageTitle'));
    document.querySelector('meta[property="og:description"]')?.setAttribute('content', translate('pageDescription'));
    document.querySelector('meta[property="og:locale"]')?.setAttribute('content', current === 'ru' ? 'ru_RU' : 'en_US');
    document.querySelector('meta[property="og:locale:alternate"]')?.setAttribute('content', current === 'ru' ? 'en_US' : 'ru_RU');
    try { localStorage.setItem('lang', current); } catch { /* Keep the in-memory selection. */ }
    document.dispatchEvent(new CustomEvent('languagechange', { detail: { language: current } }));
  }
  window.BluetoothDisableI18n = { translate, get language() { return current; } };
  document.getElementById('lang')?.addEventListener('change', event => setLanguage(event.target.value));
  setLanguage(current);
})();

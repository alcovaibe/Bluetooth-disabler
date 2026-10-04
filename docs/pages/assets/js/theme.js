(() => {
  'use strict';
  const valid = ['system', 'light', 'dark'];
  let saved;
  try { saved = localStorage.getItem('theme'); } catch { /* Storage may be disabled. */ }
  let current = valid.includes(saved) ? saved : 'system';
  const preference = window.matchMedia('(prefers-color-scheme: dark)');
  function updateBrowserColor() {
    const dark = current === 'dark' || (current === 'system' && preference.matches);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#101620' : '#f7f9ff');
  }
  function applyTheme(theme) {
    current = valid.includes(theme) ? theme : 'system';
    document.documentElement.dataset.theme = current;
    document.querySelectorAll('[data-theme-choice]').forEach(button => {
      const active = button.dataset.themeChoice === current;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-pressed', String(active));
    });
    updateBrowserColor();
    try { localStorage.setItem('theme', current); } catch { /* Keep the in-memory selection. */ }
  }
  applyTheme(current); // Runs in the head to avoid a flash of the wrong theme.
  preference.addEventListener('change', updateBrowserColor);
  document.addEventListener('DOMContentLoaded', () => {
    applyTheme(current);
    document.querySelectorAll('[data-theme-choice]').forEach(button => {
      button.addEventListener('click', () => applyTheme(button.dataset.themeChoice));
    });
  });
})();

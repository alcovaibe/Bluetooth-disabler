(() => {
  'use strict';
  const valid = ['light', 'dark'];
  let saved;
  try { saved = localStorage.getItem('theme'); } catch { /* Storage may be disabled. */ }
  let current = valid.includes(saved) ? saved : 'light';
  function updateBrowserColor() {
    const dark = current === 'dark';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#101620' : '#f7f9ff');
  }
  function applyTheme(theme) {
    current = valid.includes(theme) ? theme : 'light';
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
  document.addEventListener('DOMContentLoaded', () => {
    applyTheme(current);
    document.querySelectorAll('[data-theme-choice]').forEach(button => {
      button.addEventListener('click', () => applyTheme(button.dataset.themeChoice));
    });
  });
})();

(() => {
  'use strict';
  const menu = document.getElementById('menu');
  const navigation = document.getElementById('navigation');
  const close = document.getElementById('menu-close');
  const backdrop = document.getElementById('nav-backdrop');
  const mobile = window.matchMedia('(max-width: 1100px)');
  if (!menu || !navigation || !close || !backdrop) return;
  menu.hidden = false;
  function setMenu(open, restoreFocus = true) {
    navigation.classList.toggle('open', open);
    menu.setAttribute('aria-expanded', String(open));
    backdrop.hidden = !open;
    document.body.classList.toggle('menu-open', open);
    if (mobile.matches) navigation.inert = !open;
    if (open) close.focus();
    else if (restoreFocus && mobile.matches) menu.focus();
  }
  function updateLayout() {
    setMenu(false, false);
    menu.hidden = !mobile.matches;
    navigation.inert = mobile.matches;
  }
  updateLayout();
  mobile.addEventListener('change', updateLayout);
  menu.addEventListener('click', () => setMenu(menu.getAttribute('aria-expanded') !== 'true'));
  close.addEventListener('click', () => setMenu(false));
  backdrop.addEventListener('click', () => setMenu(false));
  document.addEventListener('keydown', event => {
    if (!navigation.classList.contains('open')) return;
    if (event.key === 'Escape') setMenu(false);
    if (event.key === 'Tab') {
      const focusable = [...navigation.querySelectorAll('a[href], button')];
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
  navigation.querySelectorAll('a[href^="#"]').forEach(link => {
    link.addEventListener('click', () => {
      setMenu(false, false);
      const target = document.querySelector(link.getAttribute('href'));
      if (target && mobile.matches) { target.setAttribute('tabindex', '-1'); target.focus({ preventScroll: true }); }
    });
  });
})();

(() => {
  'use strict';
  const status = document.getElementById('adb-copy-status');
  document.querySelectorAll('[data-copy-target]').forEach(button => {
    button.addEventListener('click', async () => {
      const code = document.getElementById(button.dataset.copyTarget);
      if (!code || !status) return;
      button.disabled = true;
      delete status.dataset.i18n;
      status.textContent = '';
      let key = 'copySuccess';
      try {
        await navigator.clipboard.writeText(code.textContent.trim());
      } catch {
        key = 'copyFailed';
        const selection = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(code);
        selection?.removeAllRanges();
        selection?.addRange(range);
      } finally {
        button.disabled = false;
      }
      status.dataset.i18n = key;
      status.textContent = window.BluetoothDisableI18n.translate(key);
    });
  });
})();

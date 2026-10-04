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
  const track = document.getElementById('screenshot-track');
  const controls = document.querySelector('.carousel-dots');
  if (!track || !controls) return;
  const slides = [...track.querySelectorAll('.screenshot-card')];
  const dots = [...controls.querySelectorAll('.carousel-dot')];
  if (!slides.length || slides.length !== dots.length) return;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let active = 0;
  let frame = null;
  function update(index) {
    active = Math.max(0, Math.min(slides.length - 1, index));
    dots.forEach((dot, i) => {
      dot.classList.toggle('is-active', i === active);
      dot.setAttribute('aria-current', String(i === active));
    });
    slides.forEach((slide, i) => slide.setAttribute('aria-hidden', String(i !== active)));
  }
  function show(index, animate = true) {
    const next = (index + slides.length) % slides.length;
    track.scrollTo({ left: next * track.clientWidth, behavior: animate && !reducedMotion.matches ? 'smooth' : 'instant' });
    if (!animate || reducedMotion.matches) update(next);
  }
  dots.forEach((dot, i) => dot.addEventListener('click', () => show(i)));
  track.addEventListener('scroll', () => {
    if (frame !== null) return;
    frame = requestAnimationFrame(() => {
      update(Math.round(track.scrollLeft / track.clientWidth));
      frame = null;
    });
  }, { passive: true });
  track.addEventListener('keydown', event => {
    let next;
    if (event.key === 'ArrowRight') next = active + 1;
    else if (event.key === 'ArrowLeft') next = active - 1;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = slides.length - 1;
    else return;
    event.preventDefault();
    show(next);
  });
  // Touch swipes use native scrolling; mouse dragging provides the same navigation on desktop.
  let drag = null;
  track.addEventListener('pointerdown', event => {
    if (event.pointerType !== 'mouse' || event.button !== 0) return;
    drag = { id: event.pointerId, x: event.clientX, left: track.scrollLeft, index: active };
    track.setPointerCapture(event.pointerId);
    track.classList.add('is-dragging');
  });
  track.addEventListener('pointermove', event => {
    if (!drag || event.pointerId !== drag.id) return;
    track.scrollLeft = drag.left + drag.x - event.clientX;
  });
  function endDrag(event) {
    if (!drag || event.pointerId !== drag.id) return;
    const distance = drag.x - event.clientX;
    const next = event.type === 'pointercancel' ? Math.round(track.scrollLeft / track.clientWidth)
      : Math.abs(distance) >= 36 ? drag.index + Math.sign(distance) : drag.index;
    drag = null;
    track.classList.remove('is-dragging');
    show(next);
  }
  track.addEventListener('pointerup', endDrag);
  track.addEventListener('pointercancel', endDrag);
  track.addEventListener('dragstart', event => event.preventDefault());
  new ResizeObserver(() => show(active, false)).observe(track);
  update(0);
  controls.hidden = false;
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

(() => {
  'use strict';
  const menu = document.getElementById('menu');
  const navigation = document.getElementById('navigation');
  const close = document.getElementById('menu-close');
  const backdrop = document.getElementById('nav-backdrop');
  if (!menu || !navigation || !close || !backdrop) return;
  menu.closest('.topbar').classList.add('nav-ready');
  menu.hidden = false;
  function setMenu(open, restoreFocus = true) {
    navigation.classList.toggle('open', open);
    menu.setAttribute('aria-expanded', String(open));
    backdrop.hidden = !open;
    document.body.classList.toggle('menu-open', open);
    navigation.inert = !open;
    if (open) requestAnimationFrame(() => {
      if (navigation.classList.contains('open')) close.focus();
    });
    else if (restoreFocus) menu.focus();
  }
  setMenu(false, false);
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
      if (target) { target.setAttribute('tabindex', '-1'); target.focus({ preventScroll: true }); }
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
  const count = slides.length;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let active = 0;
  let frame = null;
  let settleTimer = null;
  let drag = null;
  // Boundary copies let native touch scrolling continue past either end.
  function boundaryCopy(slide) {
    const copy = slide.cloneNode(true);
    copy.removeAttribute('id');
    copy.dataset.carouselCopy = '';
    copy.setAttribute('aria-hidden', 'true');
    copy.inert = true;
    return copy;
  }
  track.prepend(boundaryCopy(slides[count - 1]));
  track.append(boundaryCopy(slides[0]));
  function update(index) {
    active = (index + count) % count;
    dots.forEach((dot, i) => {
      dot.classList.toggle('is-active', i === active);
      dot.setAttribute('aria-current', String(i === active));
    });
    slides.forEach((slide, i) => slide.setAttribute('aria-hidden', String(i !== active)));
  }
  // Slide widths can be fractional after responsive sizing.
  function slideWidth() { return slides[0].getBoundingClientRect().width; }
  function recenter() {
    if (drag || !slideWidth()) return;
    const position = Math.round(track.scrollLeft / slideWidth());
    if (Math.abs(track.scrollLeft - position * slideWidth()) > 2) return;
    if (position === 0 || position === count + 1) {
      const realPosition = position === 0 ? count : 1;
      track.scrollTo({ left: realPosition * slideWidth(), behavior: 'instant' });
      update(realPosition - 1);
    }
  }
  function show(index, animate = true) {
    const next = (index + count) % count;
    let position = next + 1;
    if (animate && active === count - 1 && next === 0) position = count + 1;
    else if (animate && active === 0 && next === count - 1) position = 0;
    const smooth = animate && !reducedMotion.matches;
    track.scrollTo({ left: position * slideWidth(), behavior: smooth ? 'smooth' : 'instant' });
    if (!smooth) { update(next); recenter(); }
  }
  dots.forEach((dot, i) => dot.addEventListener('click', () => show(i)));
  track.addEventListener('scroll', () => {
    clearTimeout(settleTimer);
    settleTimer = setTimeout(recenter, 120);
    if (frame !== null) return;
    frame = requestAnimationFrame(() => {
      if (slideWidth()) update(Math.round(track.scrollLeft / slideWidth()) - 1);
      frame = null;
    });
  }, { passive: true });
  track.addEventListener('scrollend', recenter);
  track.addEventListener('keydown', event => {
    let next;
    if (event.key === 'ArrowRight') next = active + 1;
    else if (event.key === 'ArrowLeft') next = active - 1;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = count - 1;
    else return;
    event.preventDefault();
    show(next);
  });
  // Touch uses native scrolling; desktop mouse dragging uses the same track.
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
    const next = event.type === 'pointercancel' ? Math.round(track.scrollLeft / slideWidth()) - 1
      : Math.abs(distance) >= 36 ? drag.index + Math.sign(distance) : drag.index;
    // Keep the starting index so crossing a boundary uses its adjacent copy.
    update(drag.index);
    drag = null;
    track.classList.remove('is-dragging');
    show(next);
  }
  track.addEventListener('pointerup', endDrag);
  track.addEventListener('pointercancel', endDrag);
  track.addEventListener('dragstart', event => event.preventDefault());
  const topbar = document.querySelector('.topbar');
  if (topbar) new ResizeObserver(() => {
    const offset = Math.max(0, parseFloat(getComputedStyle(topbar).top) || 0);
    track.closest('.screenshot-carousel').style.setProperty('--carousel-topbar-height', `${topbar.getBoundingClientRect().height + offset}px`);
  }).observe(topbar);
  new ResizeObserver(() => show(active, false)).observe(track);
  update(0);
  show(0, false);
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

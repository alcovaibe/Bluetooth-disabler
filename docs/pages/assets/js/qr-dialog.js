(() => {
  'use strict';
  const dialog = document.getElementById('qr-dialog');
  const button = document.getElementById('qr');
  if (!dialog || !button) return;
  button.addEventListener('click', () => {
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
  });
  document.getElementById('qr-close')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => {
    const rect = dialog.getBoundingClientRect();
    if (event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) dialog.close();
  });
})();

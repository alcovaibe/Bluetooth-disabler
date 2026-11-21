(() => {
  'use strict';

  const until = document.currentScript.dataset.until;
  const remaining = until ? Date.parse(until) - Date.now() : Infinity;
  if (!(remaining > 0)) return;

  const screen = document.createElement('dialog');
  screen.id = 'maintenance-screen';
  screen.setAttribute('aria-labelledby', 'maintenance-screen-title');
  screen.addEventListener('cancel', event => event.preventDefault());

  const message = document.createElement('div');
  const title = document.createElement('h1');
  const description = document.createElement('p');
  title.id = 'maintenance-screen-title';
  title.tabIndex = -1;
  title.setAttribute('autofocus', '');
  const translate = () => {
    const english = document.documentElement.lang === 'en';
    title.textContent = english ? 'Scheduled maintenance' : 'Технические работы';
    description.textContent = english
      ? 'We are moving the site. Please check back later.'
      : 'Мы переносим сайт. Загляните позже.';
  };
  translate();
  document.addEventListener('languagechange', translate);

  message.append(title, description);
  screen.append(message);
  document.body.append(screen);
  screen.showModal();
  document.documentElement.classList.add('maintenance-open');

  if (Number.isFinite(remaining)) {
    window.setTimeout(() => {
      screen.close();
      screen.remove();
      document.removeEventListener('languagechange', translate);
      document.documentElement.classList.remove('maintenance-open');
    }, remaining);
  }
})();

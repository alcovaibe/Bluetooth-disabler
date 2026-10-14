import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import { test } from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');
const contract = JSON.parse(read('config/site-urls.json'));

function browser(saved, storageThrows = false) {
  const links = [{ href: '' }, { href: '' }];
  const buttons = ['ru', 'en'].map(language => ({
    dataset: { languageChoice: language }, classList: { toggle() {} }, setAttribute() {},
    addEventListener(type, callback) { this.click = callback; },
  }));
  const document = {
    documentElement: {},
    querySelectorAll(selector) {
      if (selector === '#privacy-link, #footer-privacy') return links;
      if (selector === '[data-language-choice]') return buttons;
      return [];
    },
    getElementById() { return null; }, querySelector() { return null; }, dispatchEvent() {},
  };
  const window = { BluetoothDisableTranslations: { ru: {}, en: {} } };
  vm.runInNewContext(read('docs/pages/assets/js/language.js'), {
    window, document, CustomEvent: class {},
    localStorage: {
      getItem() { if (storageThrows) throw new Error('Storage unavailable'); return saved; },
      setItem() { if (storageThrows) throw new Error('Storage unavailable'); },
    },
  }, { filename: fileURLToPath(new URL('docs/pages/assets/js/language.js', root)) });
  return { links, buttons, window };
}

test('actual language switcher always uses primary URLs, including disabled storage', () => {
  for (const [saved, throws, language] of [['ru', false, 'ru'], ['en', false, 'en'], ['evil', false, 'ru'], [null, true, 'ru']]) {
    const state = browser(saved, throws);
    assert.equal(state.window.BluetoothDisableI18n.language, language);
    state.links.forEach(link => assert.equal(link.href, contract.origin + contract.privacy[language]));
    for (const button of state.buttons) {
      button.click();
      state.links.forEach(link => assert.equal(link.href, contract.origin + contract.privacy[button.dataset.languageChoice]));
    }
  }
});

test('GitHub Pages home and 404 fallback normalize old policy addresses directly', () => {
  for (const file of ['index.html', '404.html']) {
    const scripts = [...read(`docs/github-pages-redirect/${file}`).matchAll(/<script>([\s\S]*?)<\/script>/g)];
    assert.equal(scripts.length, 1);
    for (const [old, target] of [['/index.html', '/'], ...Object.values(contract.privacy).map(path => [path + '.html', path])]) {
      let destination;
      vm.runInNewContext(scripts[0][1], {
        window: { location: {
          pathname: '/Bluetooth-disabler' + old, search: '?source=old', hash: '#section',
          replace(value) { destination = value; },
        } },
      });
      assert.equal(destination, contract.origin + target + '?source=old#section');
    }
  }
});

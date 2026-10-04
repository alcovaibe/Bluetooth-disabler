const languages={ru:{},en:{}};
let currentLanguage=localStorage.getItem('lang')||'ru';

function setLanguage(value){
 currentLanguage=value;
 localStorage.setItem('lang',value);
 document.documentElement.lang=value;
 document.querySelectorAll('[data-i18n]').forEach(el=>{
  const key=el.dataset.i18n;
  if(languages[value][key]) el.textContent=languages[value][key];
 });
}

function toggleLanguage(){
 setLanguage(currentLanguage==='ru'?'en':'ru');
}

document.addEventListener('DOMContentLoaded',()=>{
 document.getElementById('lang')?.addEventListener('click',toggleLanguage);
 setLanguage(currentLanguage);
});
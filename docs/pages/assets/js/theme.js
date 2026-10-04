const saved=localStorage.getItem('theme');

function applyTheme(theme){
 if(theme==='system'){
  document.documentElement.removeAttribute('data-theme');
 }else{
  document.documentElement.dataset.theme=theme;
 }
 localStorage.setItem('theme',theme);
}

applyTheme(saved||'system');

document.getElementById('theme')?.addEventListener('click',()=>{
 const current=localStorage.getItem('theme')||'system';
 const next=current==='system'?'light':current==='light'?'dark':'system';
 applyTheme(next);
});
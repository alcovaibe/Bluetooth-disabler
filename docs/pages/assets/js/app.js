const menuButton=document.getElementById('menu');
const navigation=document.querySelector('nav');

menuButton?.addEventListener('click',()=>{
 navigation?.classList.toggle('open');
});

document.querySelectorAll('a[href^="#"]').forEach(link=>{
 link.addEventListener('click',()=>navigation?.classList.remove('open'));
});

window.BluetoothDisableTranslations={
 ru:{
  about:'О приложении',
  features:'Возможности'
 },
 en:{
  about:'About application',
  features:'Features'
 }
};
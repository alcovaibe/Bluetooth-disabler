const menuButton=document.getElementById('menu');
const navigation=document.querySelector('nav');
if(menuButton&&navigation){menuButton.addEventListener('click',()=>navigation.classList.toggle('open'));}

document.querySelectorAll('a[href^="#"]').forEach(link=>{
 link.addEventListener('click',()=>navigation?.classList.remove('open'));
});

const translations={
 ru:{},
 en:{}
};

window.BluetoothDisableTranslations=translations;

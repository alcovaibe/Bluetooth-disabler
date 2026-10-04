const releaseElement=document.getElementById('release');
const api='https://api.github.com/repos/alcovaibe/Bluetooth-disabler/releases/latest';
fetch(api)
.then(response=>response.ok?response.json():Promise.reject())
.then(data=>{
 if(!releaseElement)return;
 const size=data.assets?.[0]?.size;
 const mb=size?Math.round(size/1024/1024*100)/100+' MB':'unknown';
 releaseElement.textContent=`${data.tag_name||'Latest'} · ${data.published_at?.slice(0,10)||''} · ${mb}`;
})
.catch(()=>{
 if(releaseElement) releaseElement.textContent='';
});

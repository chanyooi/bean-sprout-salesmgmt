(() => {
'use strict';
const rows=(window.regionSalesRows||[]).map(r=>({...r,current:Number(r.current),previous:r.previous==null?null:Number(r.previous)}));
const $=id=>document.getElementById(id),number=n=>n==null?'—':Math.round(n).toLocaleString('ko-KR')+'원',delta=r=>r.previous==null?null:r.current-r.previous,tone=n=>n==null||n===0?'flat':n>0?'up':'down',signed=n=>n==null?'—':(n>0?'+':'')+number(n),change=r=>r.previous==null?'—':r.previous===0?(r.current>0?'신규 매출':'—'):r.previous<0?'—':((r.current-r.previous)/r.previous*100).toFixed(1)+'%',valid=r=>r.lat!=null&&r.lng!=null&&Number(r.lat)>=33&&Number(r.lat)<=39&&Number(r.lng)>=124&&Number(r.lng)<=132;
function parse(address){const a=(address||'').trim();if(!a)return '지역 미등록';const m=a.match(/(?:^|\s|\()([가-힣0-9]+(?:읍|면|동))(?=\s|\(|\)|,|$)/);return m?m[1]:'지역 미확인';}
rows.forEach(r=>{r.region=r.savedRegion||parse(r.address);r.status=r.savedRegion?'저장됨':'';});
let selected=null,mode='region',map=null,markers=[];
const make=(tag,text,cls)=>{const e=document.createElement(tag);e.textContent=text;if(cls)e.className=cls;return e;};
function groups(){const g=new Map();rows.forEach(r=>{if(!g.has(r.region))g.set(r.region,{name:r.region,current:0,previous:r.previous==null?null:0,rows:[]});const a=g.get(r.region);a.current+=r.current;if(a.previous!==null)a.previous+=r.previous||0;a.rows.push(r);});return [...g.values()].sort((a,b)=>b.current-a.current);}
function render(){
const list=groups(),total=rows.reduce((a,r)=>a+r.current,0),comparable=rows.some(r=>r.previous!==null),actual=list.filter(g=>!g.name.startsWith('지역 미'));
const kpis=[['매출',number(total)],['증가 지역',comparable?actual.filter(r=>delta(r)>0).length+'곳':'—'],['감소 지역',comparable?actual.filter(r=>delta(r)<0).length+'곳':'—'],['지역 미확인',rows.filter(r=>r.region.startsWith('지역 미')).length+'곳']];
$('region-kpis').replaceChildren(...kpis.map(([label,value])=>{const card=make('div','','region-kpi');card.append(make('span',label),make('strong',value));return card;}));
const tbody=$('region-groups');tbody.replaceChildren();
list.forEach(g=>{const tr=document.createElement('tr');tr.dataset.region=g.name;if(g.name===selected)tr.className='selected';const button=make('button',g.name,'secondary-button');button.type='button';const first=document.createElement('td');first.append(button);tr.append(first);[number(g.current),number(g.previous),signed(delta(g)),change(g),g.rows.length+'곳'].forEach((v,i)=>tr.append(make('td',v,i===2||i===3?tone(delta(g)):'')));tr.onclick=()=>{selected=g.name;render();};tbody.append(tr);});
const filtered=rows.filter(r=>!selected||r.region===selected),sort=$('region-sort').value;
filtered.sort((a,b)=>sort==='sales'?b.current-a.current:sort==='down'?(delta(a)??0)-(delta(b)??0):(delta(b)??0)-(delta(a)??0));$('region-title').textContent=selected?selected+' 거래처':'거래처별 매출';
const body=$('region-vendors');body.replaceChildren();filtered.forEach(r=>{const tr=document.createElement('tr');[r.name,r.region,number(r.current),number(r.previous),signed(delta(r)),change(r),String(r.orders)].forEach((v,i)=>tr.append(make('td',v,i===4||i===5?tone(delta(r)):'')));const cell=make('td','','region-address-cell');cell.append(make('div',r.address||'주소 미등록'));if(r.status)cell.append(make('div',r.status,'region-row-status'));const edit=make('button','지역 설정','secondary-button');edit.type='button';edit.onclick=()=>editRegion(r);cell.append(edit);tr.append(cell);body.append(tr);});
if(!filtered.length){const tr=document.createElement('tr'),td=make('td','매출 데이터 없음');td.colSpan=8;tr.append(td);body.append(tr);}try{draw(list);}catch(e){$('region-map-error').hidden=false;}
}
function draw(list){
$('region-map-count').textContent='지도 '+rows.filter(valid).length+'/'+rows.length+'곳';if(!map)return;markers.forEach(m=>m.setMap(null));markers=[];
const points=mode==='vendor'?rows.filter(valid).map(r=>({...r,label:r.name})):list.filter(g=>!g.name.startsWith('지역 미')).map(g=>{const p=g.rows.filter(valid);return {...g,region:g.name,label:g.name,lat:p.length?p.reduce((a,r)=>a+Number(r.lat),0)/p.length:null,lng:p.length?p.reduce((a,r)=>a+Number(r.lng),0)/p.length:null};}).filter(valid);
points.filter(p=>!selected||p.region===selected).forEach(p=>{const label=make('span',p.label+' '+(delta(p)==null?number(p.current):signed(delta(p))),'region-map-pin '+tone(delta(p)));const marker=new naver.maps.Marker({map,position:new naver.maps.LatLng(Number(p.lat),Number(p.lng)),icon:{content:label.outerHTML,anchor:new naver.maps.Point(30,15)}});naver.maps.Event.addListener(marker,'click',()=>{selected=p.region;render();});markers.push(marker);});
}
$('region-clear').onclick=()=>{selected=null;render();};$('region-sort').onchange=render;document.querySelectorAll('[data-mode]').forEach(b=>b.onclick=()=>{mode=b.dataset.mode;document.querySelectorAll('[data-mode]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));render();});
function fitLocated(){
 const located=rows.filter(valid);if(!map||!located.length)return;
 if(located.length===1){map.setCenter(new naver.maps.LatLng(Number(located[0].lat),Number(located[0].lng)));return;}
 const first=new naver.maps.LatLng(Number(located[0].lat),Number(located[0].lng));
 const bounds=new naver.maps.LatLngBounds(first,first);
 located.forEach(r=>bounds.extend(new naver.maps.LatLng(Number(r.lat),Number(r.lng))));map.fitBounds(bounds);
}
function lookup(request,callback){
 let done=false;const finish=(status,response)=>{if(done)return;done=true;clearTimeout(timer);callback(status,response);};
 const timer=setTimeout(()=>finish(null,null),6000);
 try{request(finish);}catch(e){finish(null,null);}
}
let resolving=false,editing=null;
const tokenForm=$('region-location-token');
async function saveLocation(r,extra={}){
 const data=new URLSearchParams(new FormData(tokenForm));data.set('vendorId',String(r.id));if(r.address)data.set('address',r.address);Object.entries(extra).forEach(([k,v])=>{if(v!=null)data.set(k,String(v));});
 const response=await fetch(tokenForm.action,{method:'POST',body:data,credentials:'same-origin',headers:{Accept:'application/json'}});
 if(!response.ok)throw new Error('저장 요청 실패 (HTTP '+response.status+')');return response.json();
}
function applyResult(r,result){
 if(result.ok){r.region=result.region;r.savedRegion=result.region;if(result.lat!=null&&result.lng!=null){r.lat=Number(result.lat);r.lng=Number(result.lng);}r.status=result.message||'저장됨';return true;}
 r.status=result.message||'지역 확인 실패';return false;
}
function browserLocation(r){
 return new Promise(resolve=>{
   const service=window.naver?.maps?.Service;
   if(!service){resolve(null);return;}
   const reverse=()=>{
     if(!valid(r)||!service.reverseGeocode){resolve(null);return;}
     lookup(cb=>service.reverseGeocode({coords:new naver.maps.LatLng(Number(r.lat),Number(r.lng)),orders:'legalcode,admcode'},cb),(s,response)=>{
       const name=response?.v2?.results?.[0]?.region?.area3?.name;
       resolve(s===service.Status.OK&&name?{region:name,lat:r.lat,lng:r.lng}:null);
     });
   };
   if(valid(r)){const name=parse(r.address);if(!name.startsWith('지역 미')){resolve({region:name,lat:r.lat,lng:r.lng});return;}reverse();return;}
   if(!r.address||!service.geocode){resolve(null);return;}
   lookup(cb=>service.geocode({query:r.address},cb),(s,response)=>{
     const points=response?.v2?.addresses||[];
     if(s!==service.Status.OK||points.length!==1){resolve(null);return;}
     const p=points[0];r.lat=Number(p.y);r.lng=Number(p.x);if(!valid(r)){resolve(null);return;}
     const name=parse(p.jibunAddress||p.roadAddress||r.address);
     if(!name.startsWith('지역 미')){resolve({region:name,lat:r.lat,lng:r.lng});return;}
     reverse();
   });
 });
}
async function resolveLocations(){
 if(resolving)return;
 const pending=rows.filter(r=>!r.savedRegion&&(r.address?.trim()||valid(r)));
 if(!pending.length)return;
 resolving=true;$('region-retry').disabled=true;let index=0,done=0;
 const status=$('region-location-status');
 async function worker(){
   while(index<pending.length){
     const r=pending[index++];
     try{
       const result=await saveLocation(r);
       if(!applyResult(r,result)){
         const fallback=await browserLocation(r);
         if(fallback)applyResult(r,await saveLocation(r,fallback));
       }
     }catch(e){r.status=e.message||'지역 검색 실패';}
     done++;status.textContent='지역 확인 '+done+'/'+pending.length;
     render();
   }
 }
 try{await Promise.all(Array.from({length:Math.min(4,pending.length)},worker));}
 finally{resolving=false;$('region-retry').disabled=false;const remaining=rows.filter(r=>r.region.startsWith('지역 미')).length;status.textContent=remaining?'지역 확인 필요 '+remaining+'곳':'지역 확인 완료';try{fitLocated();}catch(e){}}
}
function editRegion(r){
 editing=r;$('region-edit-name').textContent=r.name;$('region-edit-address').textContent=r.address||'주소 미등록';$('region-edit-input').value=r.region.startsWith('지역 미')?'':r.region;$('region-edit-error').textContent='';$('region-dialog').showModal();
}
$('region-edit-close').onclick=()=>$('region-dialog').close();
$('region-edit-form').onsubmit=async e=>{
 e.preventDefault();if(!editing)return;
 const button=e.submitter;if(button)button.disabled=true;
 try{const result=await saveLocation(editing,{region:$('region-edit-input').value.trim(),manual:true});
 if(!applyResult(editing,result)){$('region-edit-error').textContent=editing.status;return;}
 $('region-dialog').close();selected=null;render();}
 catch(err){$('region-edit-error').textContent=err.message;}finally{if(button)button.disabled=false;}
};
$('region-retry').onclick=resolveLocations;
function initMap(){
 if(!window.naver?.maps){$('region-map-error').hidden=false;$('region-map').hidden=true;return;}
 try{map=new naver.maps.Map('region-map',{center:new naver.maps.LatLng(36.1195,128.3446),zoom:12});}
 catch(e){map=null;$('region-map-error').hidden=false;return;}
 try{fitLocated();}catch(e){}
}
initMap();render();resolveLocations();
})();
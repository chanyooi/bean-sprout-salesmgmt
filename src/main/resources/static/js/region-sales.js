(() => {
'use strict';
const rows=(window.regionSalesRows||[]).map(r=>({...r,current:Number(r.current),previous:r.previous==null?null:Number(r.previous)}));
const $=id=>document.getElementById(id),number=n=>n==null?'—':Math.round(n).toLocaleString('ko-KR')+'원',delta=r=>r.previous==null?null:r.current-r.previous,tone=n=>n==null||n===0?'flat':n>0?'up':'down',signed=n=>n==null?'—':(n>0?'+':'')+number(n),change=r=>r.previous==null?'—':r.previous===0?(r.current>0?'신규 매출':'—'):r.previous<0?'—':((r.current-r.previous)/r.previous*100).toFixed(1)+'%',valid=r=>r.lat!=null&&r.lng!=null&&Number(r.lat)>=33&&Number(r.lat)<=39&&Number(r.lng)>=124&&Number(r.lng)<=132;
function parse(address){const a=(address||'').trim();if(!a)return '지역 미등록';const m=a.match(/(?:^|\s|\()([가-힣0-9]+(?:읍|면|동))(?=\s|\(|\)|,|$)/g);return m?.length?m[m.length-1].replace(/^[\s(]+/,''):'지역 미확인';}
rows.forEach(r=>r.region=parse(r.address));
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
const body=$('region-vendors');body.replaceChildren();filtered.forEach(r=>{const tr=document.createElement('tr');[r.name,r.region,number(r.current),number(r.previous),signed(delta(r)),change(r),String(r.orders)].forEach((v,i)=>tr.append(make('td',v,i===4||i===5?tone(delta(r)):'')));body.append(tr);});
if(!filtered.length){const tr=document.createElement('tr'),td=make('td','매출 데이터 없음');td.colSpan=7;tr.append(td);body.append(tr);}draw(list);
}
function draw(list){
$('region-map-count').textContent='지도 '+rows.filter(valid).length+'/'+rows.length+'곳';if(!map)return;markers.forEach(m=>m.setMap(null));markers=[];
const points=mode==='vendor'?rows.filter(valid).map(r=>({...r,label:r.name})):list.filter(g=>!g.name.startsWith('지역 미')).map(g=>{const p=g.rows.filter(valid);return {...g,region:g.name,label:g.name,lat:p.length?p.reduce((a,r)=>a+Number(r.lat),0)/p.length:null,lng:p.length?p.reduce((a,r)=>a+Number(r.lng),0)/p.length:null};}).filter(valid);
points.filter(p=>!selected||p.region===selected).forEach(p=>{const label=make('span',p.label+' '+(delta(p)==null?number(p.current):signed(delta(p))),'region-map-pin '+tone(delta(p)));const marker=new naver.maps.Marker({map,position:new naver.maps.LatLng(Number(p.lat),Number(p.lng)),icon:{content:label.outerHTML,anchor:new naver.maps.Point(30,15)}});naver.maps.Event.addListener(marker,'click',()=>{selected=p.region;render();});markers.push(marker);});
}
$('region-clear').onclick=()=>{selected=null;render();};$('region-sort').onchange=render;document.querySelectorAll('[data-mode]').forEach(b=>b.onclick=()=>{mode=b.dataset.mode;document.querySelectorAll('[data-mode]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));render();});
function initMap(){if(!window.naver?.maps){$('region-map-error').hidden=false;$('region-map').hidden=true;return;}
try{map=new naver.maps.Map('region-map',{center:new naver.maps.LatLng(36.1195,128.3446),zoom:12});const located=rows.filter(valid);
if(located.length>1){const bounds=new naver.maps.LatLngBounds(new naver.maps.LatLng(Number(located[0].lat),Number(located[0].lng)),new naver.maps.LatLng(Number(located[0].lat),Number(located[0].lng)));located.forEach(r=>bounds.extend(new naver.maps.LatLng(Number(r.lat),Number(r.lng))));map.fitBounds(bounds);}if(located.length===1)map.setCenter(new naver.maps.LatLng(Number(located[0].lat),Number(located[0].lng)));
if(naver.maps.Service?.reverseGeocode){const pending=located.filter(r=>r.region.startsWith('지역 미'));let i=0;const next=()=>{if(i>=pending.length)return;const r=pending[i++];naver.maps.Service.reverseGeocode({coords:new naver.maps.LatLng(Number(r.lat),Number(r.lng)),orders:'admcode'},(status,response)=>{if(status===naver.maps.Service.Status.OK){const name=response?.v2?.results?.[0]?.region?.area3?.name;if(name)r.region=name;}render();next();});};next();}
}catch(e){map=null;$('region-map-error').hidden=false;}}
initMap();render();
})();
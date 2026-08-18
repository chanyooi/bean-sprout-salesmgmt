(function(){
  function won(n){ return new Intl.NumberFormat('ko-KR').format(Math.round(Number(n||0)))+'원'; }
  function num(n){ return new Intl.NumberFormat('ko-KR').format(Number(n||0)); }
  function month(){
    var q=new URLSearchParams(location.search).get('month'); if(q) return q;
    var i=document.querySelector('input[type="month"]'); if(i&&i.value) return i.value;
    var d=new Date(); return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0');
  }
  function rowText(tr){ return (tr.textContent||'').replace(/\s+/g,' ').trim(); }
  function hideSpecialRows(){
    document.querySelectorAll('table tbody tr').forEach(function(tr){
      var t=rowText(tr);
      if(t.indexOf('손두부')>=0 || t.indexOf('두부판')>=0 || t.indexOf('회수통')>=0){
        // 품목별 판매 집계표에서만 숨김. 판매내역 상세표는 그대로 둔다.
        var table=tr.closest('table');
        var head=table ? rowText(table.querySelector('thead')||table) : '';
        if(head.indexOf('판매 수량')>=0 && head.indexOf('매출')>=0) tr.classList.add('special-hidden-row');
      }
    });
  }
  function addCards(r){
    var target=null;
    document.querySelectorAll('table').forEach(function(t){
      var h=rowText(t.querySelector('thead')||t);
      if(!target && h.indexOf('판매 수량')>=0 && h.indexOf('매출')>=0) target=t;
    });
    if(!target || document.querySelector('.special-accounting-section')) return;
    var s=document.createElement('section'); s.className='special-accounting-section';
    s.innerHTML='<div class="special-accounting-grid">'+
      '<article class="special-accounting-card"><h3>매입·재판매 품목</h3><p>손두부는 팔공 매입과 아포농협 판매를 분리합니다.</p><div class="special-accounting-metrics">'+
      '<div class="special-accounting-metric"><span>아포농협 판매매출</span><strong>'+won(r.tofuResaleSales)+'</strong></div>'+
      '<div class="special-accounting-metric"><span>팔공 매입원가</span><strong>'+won(r.tofuPurchaseCost)+'</strong></div>'+
      '<div class="special-accounting-metric"><span>손두부 이익</span><strong class="special-accounting-profit">'+won(r.tofuProfit)+'</strong></div></div>'+ 
      '<div class="special-accounting-note">판매 '+num(r.tofuResaleQty)+' · 매입 '+num(r.tofuPurchaseQty)+'</div></article>'+
      '<article class="special-accounting-card"><h3>보증금·회수 항목</h3><p>제품 매출과 분리해 관리합니다.</p><div class="special-accounting-metrics">'+
      '<div class="special-accounting-metric"><span>두부판</span><strong>'+won(r.tofuTrayAmount)+'</strong></div>'+
      '<div class="special-accounting-metric"><span>회수통</span><strong>'+won(r.returnContainerAmount)+'</strong></div>'+
      '<div class="special-accounting-metric"><span>일반 매출 반영</span><strong>제외</strong></div></div></article></div>';
    target.parentElement.insertBefore(s,target.nextSibling);
  }
  function parseWon(t){ var m=(t||'').replace(/,/g,'').match(/-?\d+(?:\.\d+)?/); return m?Number(m[0]):null; }
  function patchKpis(r){
    document.querySelectorAll('*').forEach(function(el){
      if(el.children.length) return;
      var label=(el.textContent||'').trim();
      if(label==='월매출'){
        var card=el.closest('.metric-card,.sc-kpi-card,.card,article');
        if(card){
          var strong=card.querySelector('strong'); if(strong){ strong.textContent=won(r.adjustedSales); if(!card.querySelector('.special-adjusted-badge')){ var b=document.createElement('span'); b.className='special-adjusted-badge'; b.textContent='특수품목 분리'; el.appendChild(b); } }
        }
      }
      if(label==='예상이익'){
        var c=el.closest('.metric-card,.sc-kpi-card,.card,article');
        if(c){ var s=c.querySelector('strong'); if(s){ var old=parseWon(s.textContent); if(old!==null){ s.textContent=won(old+Number(r.profitAdjustment||0)); } } }
      }
    });
  }
  fetch('/api/special-item-accounting?month='+encodeURIComponent(month()),{credentials:'same-origin'})
    .then(function(x){ if(!x.ok) throw new Error('accounting api '+x.status); return x.json(); })
    .then(function(r){ hideSpecialRows(); addCards(r); patchKpis(r); })
    .catch(function(e){ console.warn('[special-item-accounting]',e); });
})();

(function(){
  function money(value){return new Intl.NumberFormat('ko-KR',{maximumFractionDigits:0}).format(value)+'원';}
  function svgEl(name,attrs){var n=document.createElementNS('http://www.w3.org/2000/svg',name);Object.keys(attrs||{}).forEach(function(k){n.setAttribute(k,attrs[k]);});return n;}
  document.querySelectorAll('[data-sales-trend]').forEach(function(container){
    var points=Array.from(container.querySelectorAll('.sales-trend-data')).map(function(e){
      return {label:e.dataset.label,amount:Number(e.dataset.amount)||0};
    });
    if(!points.length){container.textContent='매출 데이터가 없습니다.';return;}
    var max=Math.max(1,...points.map(function(p){return p.amount;}));
    var svg=svgEl('svg',{viewBox:'0 0 760 270',role:'img','aria-label':'월별 매출 추이',preserveAspectRatio:'xMidYMid meet'});
    var base=212,top=24,left=48,right=748,step=(right-left)/points.length;
    for(var t=0;t<=4;t++){var y=base-(base-top)*t/4;svg.appendChild(svgEl('line',{x1:left,y1:y,x2:right,y2:y,stroke:'#e7edf4'}));var text=svgEl('text',{x:40,y:y+4,'text-anchor':'end',fill:'#8792a2','font-size':'10'});text.textContent=(max*t/4/10000).toFixed(0)+'만';svg.appendChild(text);}
    var poly=[];
    points.forEach(function(p,i){
      var x=left+step*(i+.5),y=base-(base-top)*p.amount/max;
      if(container.dataset.salesTrend==='bar'){var bar=svgEl('rect',{x:x-step*.31,y:y,width:step*.62,height:Math.max(0,base-y),rx:4,fill:'#428bfa'});var tip=svgEl('title');tip.textContent=p.label+' '+money(p.amount);bar.appendChild(tip);svg.appendChild(bar);}
      else {poly.push(x+','+y);}
      var label=svgEl('text',{x:x,y:239,'text-anchor':'middle',fill:'#66758a','font-size':'11'});label.textContent=p.label.slice(5)+'월';svg.appendChild(label);
    });
    if(container.dataset.salesTrend==='line'){
      svg.appendChild(svgEl('polyline',{points:poly.join(' '),fill:'none',stroke:'#428bfa','stroke-width':3,'stroke-linecap':'round','stroke-linejoin':'round'}));
      points.forEach(function(p,i){var x=left+step*(i+.5),y=base-(base-top)*p.amount/max;var circle=svgEl('circle',{cx:x,cy:y,r:5,fill:'#fff',stroke:'#428bfa','stroke-width':3});var tip=svgEl('title');tip.textContent=p.label+' '+money(p.amount);circle.appendChild(tip);svg.appendChild(circle);});
    }
    container.replaceChildren(svg);
  });
})();
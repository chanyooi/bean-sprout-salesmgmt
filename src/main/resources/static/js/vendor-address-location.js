(() => {
'use strict';
// Match both nested controls and controls associated via the HTML form attribute.
document.querySelectorAll('input[name="address"]').forEach(address => {
    const form=address.form;
    if(!form)return;
    const lat=form.elements.namedItem('latitude'),lng=form.elements.namedItem('longitude');
    if(!lat||!lng)return;
    const initial=address.value.trim();
    let submitting=false;
    form.addEventListener('submit',event=>{
        if(submitting)return;
        const query=address.value.trim();
        if(query===initial&&lat.value&&lng.value)return;
        if(!query){lat.value='';lng.value='';return;}
        if(!window.naver?.maps?.Service?.geocode){lat.value='';lng.value='';return;}
        event.preventDefault();
        const submitter=event.submitter;
        const original=submitter?.textContent;
        if(submitter){submitter.disabled=true;submitter.textContent='위치 확인 중';}
        let finished=false;
        const finish=(point)=>{
            if(finished)return;finished=true;
            // Never reuse a coordinate for an address edited during lookup.
            const matched=point&&address.value.trim()===query;
            lat.value=matched?String(point.y):'';lng.value=matched?String(point.x):'';
            if(submitter){submitter.disabled=false;submitter.textContent=original;}
            submitting=true;form.requestSubmit(submitter||undefined);submitting=false;
        };
        const timer=setTimeout(()=>finish(null),8000);
        try{naver.maps.Service.geocode({query},(status,response)=>{
            clearTimeout(timer);
            const p=response?.v2?.addresses?.[0];
            finish(status===naver.maps.Service.Status.OK&&p&&Number.isFinite(Number(p.x))&&Number.isFinite(Number(p.y))?p:null);
        });}catch(e){clearTimeout(timer);finish(null);}
    });
});
})();
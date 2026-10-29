(function () {
    "use strict";
    const won = value => {
        const number = Number(value);
        return Number.isFinite(number) ? new Intl.NumberFormat('ko-KR').format(number) + '원' : String(value) + '원';
    };
    const summary = document.getElementById('personal-breakdown');
    function showBreakdown(items) {
        if (!summary) return;
        summary.replaceChildren();
        const entries=Object.entries(items || {}).filter(([, amount]) => Number(amount) > 0)
            .sort((a,b) => Number(b[1])-Number(a[1]));
        if (!entries.length) {
            summary.textContent = '아직 분류된 개인 지출이 없습니다.';
            return;
        }
        const list=document.createElement('div');
        list.className='personal-breakdown-list';
        entries.forEach(([name,amount])=>{
            const item=document.createElement('div');
            item.className='personal-breakdown-item';
            const title=document.createElement('span');
            const value=document.createElement('strong');
            title.textContent=name;
            value.textContent=won(amount);
            item.append(title,value);
            list.append(item);
        });
        summary.append(list);
    }
    document.querySelectorAll('form[data-classify-url]').forEach(form=>{
        const type=form.querySelector('select[name="type"]');
        const detail=form.querySelector('select[name="detail"]');
        const status=form.querySelector('[data-classify-status]');
        if(!type||!detail||!status)return;
        let savedType=type.value;
        let savedDetail=detail.value;
        let saving=false;

        function toggleDetail(){ detail.style.display=type.value==='personal'?'':'none'; }
        function statusText(message,isError){
            status.textContent=message;
            status.classList.toggle('fail',Boolean(isError));
        }
        async function save(){
            if(saving)return;
            const newType=type.value;
            const newDetail=detail.value;
            toggleDetail();
            saving=true;
            type.disabled=true;detail.disabled=true;
            statusText('저장 중…',false);
            try{
                const data=new FormData(form);
                data.set('type',newType);
                data.set('detail',newDetail);
                const response=await fetch(form.dataset.classifyUrl,{
                    method:'POST',
                    credentials:'same-origin',
                    headers:{'Accept':'application/json'},
                    body:data
                });
                if(!response.ok)throw new Error('Failed ('+response.status+')');
                const result=await response.json();
                if(result.status!=='saved')throw new Error('Unexpected response');
                savedType=newType;savedDetail=newDetail;
                statusText('✓ 저장됨',false);
                ['personalExpense','businessExpense','pendingExpense','transferExpense'].forEach(name=>{
                    const target=document.querySelector('[data-stat="'+name+'"]');
                    if(target&&result[name]!==undefined) target.textContent=won(result[name]);
                });
                showBreakdown(result.personalTotals);
            }catch(err){
                type.value=savedType;
                detail.value=savedDetail;
                toggleDetail();
                statusText('저장 실패 · 다시 선택해주세요',true);
            }finally{
                type.disabled=false;detail.disabled=false;saving=false;
            }
        }
        type.addEventListener('change',save);
        detail.addEventListener('change',()=>{if(type.value==='personal')save();});
        form.addEventListener('submit',e=>{e.preventDefault();save();});
        toggleDetail();
    });
})();

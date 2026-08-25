(function(){
  var modal=document.getElementById('beanUsageModal');
  var title=document.getElementById('beanModalTitle');
  var dateInput=document.getElementById('beanUsageDate');
  var existing=document.getElementById('beanExisting');
  var form=document.getElementById('beanUsageForm');
  var deleteForm=document.getElementById('beanUsageDeleteForm');
  var deleteDateInput=document.getElementById('beanUsageDeleteDate');

  function number(value){
    var parsed=Number(value||0);
    return Number.isFinite(parsed)?parsed:0;
  }

  function priceForOrigin(select){
    if(!select)return '';
    if(select.value==='CANADA')return select.dataset.priceCanada||'';
    return select.dataset.priceChina||'';
  }

  function syncPrice(selectId,priceInputId){
    var select=document.getElementById(selectId);
    var priceInput=document.getElementById(priceInputId);
    if(!select||!priceInput)return;
    priceInput.value=priceForOrigin(select);
  }

  function syncAllPrices(){
    syncPrice('largeOrigin','largeUnitPricePerKg');
    syncPrice('mediumOrigin','mediumUnitPricePerKg');
    syncPrice('smallOrigin','smallUnitPricePerKg');
  }

  function openModal(button){
    if(!modal||!button)return;
    var date=button.dataset.date||'';
    var large=number(button.dataset.large);
    var medium=number(button.dataset.medium);
    var small=number(button.dataset.small);
    var hasUsage=large>0||medium>0||small>0;

    title.textContent=date;
    dateInput.value=date;
    existing.textContent='현재 사용량  ·  대 '+large+' / 중 '+medium+' / 소 '+small+'포';
    ['largeBags','mediumBags','smallBags'].forEach(function(id){var input=document.getElementById(id);if(input)input.value='0';});
    syncAllPrices();

    if(deleteForm&&deleteDateInput){
      deleteDateInput.value=date;
      deleteForm.hidden=!hasUsage;
    }

    modal.hidden=false;
    document.body.style.overflow='hidden';
  }

  function closeModal(){
    if(!modal)return;
    modal.hidden=true;
    document.body.style.overflow='';
  }

  document.querySelectorAll('.bean-day:not(.other-month)').forEach(function(button){
    button.addEventListener('click',function(){openModal(button);});
  });

  document.querySelectorAll('[data-close-modal]').forEach(function(button){
    button.addEventListener('click',closeModal);
  });

  document.querySelectorAll('[data-step]').forEach(function(button){
    button.addEventListener('click',function(){
      var input=document.getElementById(button.dataset.target);
      if(!input)return;
      var current=number(input.value);
      var next=Math.max(0,current+number(button.dataset.step));
      input.value=String(next);
    });
  });

  [
    ['largeOrigin','largeUnitPricePerKg'],
    ['mediumOrigin','mediumUnitPricePerKg'],
    ['smallOrigin','smallUnitPricePerKg']
  ].forEach(function(pair){
    var select=document.getElementById(pair[0]);
    if(select){
      select.addEventListener('change',function(){syncPrice(pair[0],pair[1]);});
    }
  });

  document.addEventListener('keydown',function(event){if(event.key==='Escape')closeModal();});

  if(form){
    form.addEventListener('submit',function(event){
      var rows=[
        {label:'대립',bags:'largeBags',price:'largeUnitPricePerKg'},
        {label:'중립',bags:'mediumBags',price:'mediumUnitPricePerKg'},
        {label:'소립',bags:'smallBags',price:'smallUnitPricePerKg'}
      ];
      var total=rows.reduce(function(sum,row){var input=document.getElementById(row.bags);return sum+number(input&&input.value);},0);

      if(total<=0){
        event.preventDefault();
        alert('대립·중립·소립 중 하나 이상 수량을 입력해주세요.');
        return;
      }

      for(var i=0;i<rows.length;i++){
        var row=rows[i];
        var bagsInput=document.getElementById(row.bags);
        var priceInput=document.getElementById(row.price);
        if(number(bagsInput&&bagsInput.value)>0&&number(priceInput&&priceInput.value)<=0){
          event.preventDefault();
          alert(row.label+' kg당 단가를 입력해주세요.');
          if(priceInput)priceInput.focus();
          return;
        }
      }
    });
  }

  if(deleteForm){
    deleteForm.addEventListener('submit',function(event){
      var date=deleteDateInput?deleteDateInput.value:'';
      if(!window.confirm(date+'의 대립·중립·소립 사용 기록을 모두 삭제할까요?')){
        event.preventDefault();
      }
    });
  }
})();

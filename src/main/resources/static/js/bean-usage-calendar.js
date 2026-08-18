(function(){
  var modal=document.getElementById('beanUsageModal');
  var title=document.getElementById('beanModalTitle');
  var dateInput=document.getElementById('beanUsageDate');
  var existing=document.getElementById('beanExisting');
  var form=document.getElementById('beanUsageForm');

  function openModal(button){
    if(!modal||!button)return;
    var date=button.dataset.date||'';
    title.textContent=date;
    dateInput.value=date;
    existing.textContent='현재 사용량  ·  대 '+(button.dataset.large||'0')+' / 중 '+(button.dataset.medium||'0')+' / 소 '+(button.dataset.small||'0')+'포';
    ['largeBags','mediumBags','smallBags'].forEach(function(id){var input=document.getElementById(id);if(input)input.value='0';});
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
      var current=Number(input.value||0);
      var next=Math.max(0,current+Number(button.dataset.step||0));
      input.value=String(next);
    });
  });

  document.addEventListener('keydown',function(event){if(event.key==='Escape')closeModal();});

  if(form){
    form.addEventListener('submit',function(event){
      var total=['largeBags','mediumBags','smallBags'].reduce(function(sum,id){var input=document.getElementById(id);return sum+Number(input&&input.value||0);},0);
      if(total<=0){event.preventDefault();alert('대립·중립·소립 중 하나 이상 수량을 입력해주세요.');}
    });
  }
})();

(function () {
    if (window.location.pathname !== '/profit') return;

    var form = document.querySelector('form.expense-form');
    if (!form || form.dataset.customExpenseReady === 'true') return;
    form.dataset.customExpenseReady = 'true';

    var monthInput = form.querySelector('input[name="month"]');
    var submitButton = form.querySelector('button[type="submit"]');
    if (!monthInput || !submitButton) return;

    injectStyles();
    updateBeanCostDescriptions();

    var panel = form.closest('.panel');
    var description = panel ? panel.querySelector('.panel-heading p') : null;
    if (description) {
        description.textContent = '기본 비용과 함께 전기세·땅 대출이자·유류비·수도세 등 필요한 비용 항목을 자유롭게 추가할 수 있습니다.';
    }

    var editor = document.createElement('section');
    editor.className = 'custom-expense-editor';
    editor.innerHTML =
        '<div class="custom-expense-head">' +
            '<div><strong>추가 비용 항목</strong><small>전기세, 대출이자처럼 달마다 달라지는 비용을 필요한 만큼 추가하세요.</small></div>' +
            '<button type="button" class="custom-expense-add">+ 비용 항목 추가</button>' +
        '</div>' +
        '<div class="custom-expense-rows"></div>';

    form.insertBefore(editor, submitButton);

    var rows = editor.querySelector('.custom-expense-rows');
    var addButton = editor.querySelector('.custom-expense-add');

    addButton.addEventListener('click', function () {
        addRow('', 0, true);
    });

    loadExisting();

    function updateBeanCostDescriptions() {
        document.querySelectorAll('.report-panel .helper').forEach(function (helper) {
            if ((helper.textContent || '').indexOf('매입') >= 0) {
                helper.textContent = '날짜별 kg당 실제 단가를 입력한 기록은 그 단가를 우선 적용하고, 비워둔 기존 기록은 해당 월 매입기록의 가중평균 단가를 사용합니다.';
            }
        });

        document.querySelectorAll('.report-notice.warning').forEach(function (notice) {
            if ((notice.textContent || '').indexOf('콩 사용 기록') >= 0) {
                var strong = notice.querySelector('strong');
                var count = strong ? strong.textContent : '';
                notice.textContent = 'kg당 실제 단가도 없고 해당 월 매입단가도 없는 콩 사용 기록이 ';
                if (count) {
                    var countStrong = document.createElement('strong');
                    countStrong.textContent = count;
                    notice.appendChild(countStrong);
                }
                notice.appendChild(document.createTextNode(' 있습니다. 이 사용량은 콩 원가에서 제외되므로 사용 기록에 kg당 단가를 입력하거나 매입 기록을 등록해주세요.'));
            }
        });
    }

    function loadExisting() {
        var month = encodeURIComponent(monthInput.value || '');
        fetch('/profit/custom-expenses?month=' + month, {
            headers: { 'Accept': 'application/json' }
        })
            .then(function (response) {
                if (!response.ok) throw new Error('load failed');
                return response.json();
            })
            .then(function (items) {
                if (Array.isArray(items) && items.length > 0) {
                    items.forEach(function (item) {
                        addRow(item.name || '', item.amount == null ? 0 : item.amount, false);
                    });
                } else {
                    addRecommendedRows();
                }
            })
            .catch(function () {
                addRecommendedRows();
            });
    }

    function addRecommendedRows() {
        addRow('전기세', 0, false);
        addRow('땅 대출이자', 0, false);
        addRow('배송 유류비', 0, false);
        addRow('수도세', 0, false);
    }

    function addRow(name, amount, focusName) {
        if (rows.children.length >= 30) {
            window.alert('추가 비용 항목은 최대 30개까지 등록할 수 있습니다.');
            return;
        }

        var row = document.createElement('div');
        row.className = 'custom-expense-row';

        var nameInput = document.createElement('input');
        nameInput.type = 'text';
        nameInput.name = 'customExpenseName';
        nameInput.maxLength = 80;
        nameInput.placeholder = '예: 전기세, 땅 대출이자, 차량 수리비';
        nameInput.value = String(name || '');
        nameInput.setAttribute('aria-label', '추가 비용 항목명');

        var amountInput = document.createElement('input');
        amountInput.type = 'number';
        amountInput.name = 'customExpenseAmount';
        amountInput.min = '0';
        amountInput.step = '1';
        amountInput.inputMode = 'numeric';
        amountInput.placeholder = '금액';
        amountInput.value = normalizeAmount(amount);
        amountInput.setAttribute('aria-label', '추가 비용 금액');

        var removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.className = 'custom-expense-remove';
        removeButton.textContent = '삭제';
        removeButton.addEventListener('click', function () {
            row.remove();
        });

        row.appendChild(nameInput);
        row.appendChild(amountInput);
        row.appendChild(removeButton);
        rows.appendChild(row);

        if (focusName) nameInput.focus();
    }

    function normalizeAmount(value) {
        var number = Number(value == null ? 0 : value);
        return Number.isFinite(number) && number >= 0 ? String(Math.round(number)) : '0';
    }

    function injectStyles() {
        if (document.getElementById('customExpenseEditorStyles')) return;
        var style = document.createElement('style');
        style.id = 'customExpenseEditorStyles';
        style.textContent =
            '.custom-expense-editor{grid-column:1/-1;margin-top:4px;padding-top:18px;border-top:1px solid #edf0f2}' +
            '.custom-expense-head{display:flex;align-items:flex-end;justify-content:space-between;gap:14px;margin-bottom:12px}' +
            '.custom-expense-head strong{display:block;color:#191f28;font-size:15px}' +
            '.custom-expense-head small{display:block;margin-top:4px;color:#8b95a1;font-size:12px;line-height:1.45}' +
            '.custom-expense-add{min-height:38px!important;padding:0 13px!important;white-space:nowrap}' +
            '.custom-expense-rows{display:grid;gap:9px}' +
            '.custom-expense-row{display:grid;grid-template-columns:minmax(180px,1fr) minmax(150px,.65fr) 72px;gap:9px;align-items:center}' +
            '.custom-expense-row input{width:100%;min-height:44px}' +
            '.custom-expense-remove{min-height:44px!important;border:1px solid #ffd7da!important;background:#fff5f6!important;color:#d92d3d!important;box-shadow:none!important}' +
            '@media(max-width:640px){.custom-expense-head{align-items:stretch;flex-direction:column}.custom-expense-add{width:100%}.custom-expense-row{grid-template-columns:1fr 1fr}.custom-expense-remove{grid-column:1/-1;min-height:38px!important}}';
        document.head.appendChild(style);
    }
})();

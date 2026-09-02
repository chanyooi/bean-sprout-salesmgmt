(function () {
    function text(el) {
        return el ? (el.textContent || '').trim() : '';
    }

    function moneyToNumber(value) {
        var digits = String(value || '').replace(/[^0-9-]/g, '');
        var parsed = Number(digits || 0);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function formatMoney(value) {
        return Number(value || 0).toLocaleString('ko-KR') + '원';
    }

    function safe(value, fallback) {
        return value == null || String(value).trim() === '' ? (fallback || '-') : String(value);
    }

    function injectVendorBasicInfo() {
        var match = /^\/vendor-management\/(\d+)(?:\/)?$/.exec(window.location.pathname || '');
        if (!match || document.querySelector('.vendor-detail-basic-info')) return;

        var vendorId = match[1];
        fetch('/vendor-management/' + encodeURIComponent(vendorId) + '/profile.json', {
            credentials: 'same-origin',
            cache: 'no-store'
        })
            .then(function (response) {
                if (!response.ok) throw new Error('기본정보를 불러오지 못했습니다.');
                return response.json();
            })
            .then(function (profile) {
                var main = document.querySelector('.vendor-v2-shell');
                if (!main) return;

                var firstPanel = main.querySelector('.v2-panel');
                if (!firstPanel) return;

                var routeText = profile.routeCode === 'NONE'
                    ? '미지정'
                    : safe(profile.routeLabel) + (profile.routeOrder ? ' · ' + profile.routeOrder + '번' : '');

                var section = document.createElement('section');
                section.className = 'v2-panel vendor-detail-basic-info';
                section.innerHTML =
                    '<div class="vendor-basic-head">' +
                        '<div><h2>거래처 기본정보</h2><p style="margin:4px 0 0;color:#8b95a1;font-size:12px">거래처 관리에 저장된 기본정보입니다.</p></div>' +
                        '<a class="vendor-basic-edit-link" href="/vendor-management">전체 거래처 정보 보기</a>' +
                    '</div>' +
                    '<div class="vendor-basic-grid">' +
                        basicItem('상태', profile.active ? '활성' : '비활성') +
                        basicItem('배송코스', routeText) +
                        basicItem('입금주기', safe(profile.paymentCycleLabel)) +
                        basicItem('전화번호', safe(profile.phone)) +
                        basicItem('주소', safe(profile.address)) +
                        '<div class="vendor-basic-item vendor-basic-memo"><span>메모</span><strong>' + escapeHtml(safe(profile.memo)) + '</strong></div>' +
                    '</div>';

                firstPanel.parentNode.insertBefore(section, firstPanel);
            })
            .catch(function () {
                // 기본정보 표시 실패가 주문/단가 관리 기능을 막지 않도록 조용히 종료한다.
            });
    }

    function basicItem(label, value) {
        return '<div class="vendor-basic-item"><span>' + escapeHtml(label) + '</span><strong>' + escapeHtml(safe(value)) + '</strong></div>';
    }

    function load() {
        if (!window.location.pathname.startsWith('/vendor-management/')) return;

        injectVendorBasicInfo();

        var table = document.querySelector('.order-table');
        if (!table || table.dataset.calendarReady === 'true') return;

        var rows = Array.from(table.querySelectorAll('tbody tr'));
        if (!rows.length) return;

        var firstMonthInput = table.querySelector('input[name="month"]');
        var monthValue = firstMonthInput ? firstMonthInput.value : '';
        var match = /^(\d{4})-(\d{2})$/.exec(monthValue);
        if (!match) return;

        var year = Number(match[1]);
        var month = Number(match[2]);
        var daysInMonth = new Date(year, month, 0).getDate();
        var firstWeekday = new Date(year, month - 1, 1).getDay();
        var byDay = new Map();

        rows.forEach(function (row) {
            var cells = row.querySelectorAll('td');
            if (cells.length < 6) return;

            var dateText = text(cells[0]);
            var dateMatch = /\d{2}\/(\d{2})/.exec(dateText);
            if (!dateMatch) return;

            var day = Number(dateMatch[1]);
            var form = cells[4].querySelector('form');
            var priceInput = form ? form.querySelector('input[name="unitPrice"]') : null;

            var record = {
                day: day,
                dateText: dateText,
                orderNumber: text(cells[1]),
                itemName: text(cells[2]),
                quantity: text(cells[3]),
                amountText: text(cells[5]),
                amount: moneyToNumber(text(cells[5])),
                form: form,
                unitPrice: priceInput ? priceInput.value : ''
            };

            if (!byDay.has(day)) byDay.set(day, []);
            byDay.get(day).push(record);
        });

        table.dataset.calendarReady = 'true';
        var tableWrap = table.parentElement;
        if (tableWrap) tableWrap.classList.add('vendor-order-source-table');

        var calendar = document.createElement('div');
        calendar.className = 'vendor-order-calendar-wrap';
        calendar.innerHTML =
            '<div class="vendor-order-calendar-weekdays">' +
                '<span>일</span><span>월</span><span>화</span><span>수</span><span>목</span><span>금</span><span>토</span>' +
            '</div>' +
            '<div class="vendor-order-calendar-grid"></div>' +
            '<p class="vendor-order-calendar-help">주문이 있는 날짜를 누르면 그날 주문 품목과 적용단가를 확인하고 바로 수정할 수 있습니다.</p>';

        var grid = calendar.querySelector('.vendor-order-calendar-grid');

        for (var blank = 0; blank < firstWeekday; blank++) {
            var blankCell = document.createElement('div');
            blankCell.className = 'vendor-order-day is-empty-slot';
            grid.appendChild(blankCell);
        }

        for (var day = 1; day <= daysInMonth; day++) {
            var records = byDay.get(day) || [];
            var weekday = new Date(year, month - 1, day).getDay();
            var cell = document.createElement(records.length ? 'button' : 'div');
            cell.className = 'vendor-order-day' +
                (records.length ? ' has-order' : '') +
                (weekday === 0 ? ' sunday' : '') +
                (weekday === 6 ? ' saturday' : '');

            if (records.length) {
                cell.type = 'button';
                cell.dataset.day = String(day);
            }

            var itemNames = Array.from(new Set(records.map(function (r) { return r.itemName; })));
            var total = records.reduce(function (sum, r) { return sum + r.amount; }, 0);
            var summary = '';

            if (records.length) {
                summary =
                    '<div class="vendor-order-day-summary">' +
                        '<strong>' + escapeHtml(itemNames.slice(0, 2).join(' · ')) + '</strong>' +
                        '<small>' + records.length + '개 품목' + (itemNames.length > 2 ? ' 외 ' + (itemNames.length - 2) + '개' : '') + '</small>' +
                    '</div>' +
                    '<div class="vendor-order-day-total">' + formatMoney(total) + '</div>';
            }

            cell.innerHTML =
                '<span class="vendor-order-day-number">' + day + '</span>' + summary;

            if (records.length) {
                cell.addEventListener('click', function () {
                    openModal(Number(this.dataset.day));
                });
            }

            grid.appendChild(cell);
        }

        if (tableWrap) {
            tableWrap.parentNode.insertBefore(calendar, tableWrap);
        }

        var modalBackdrop = document.createElement('div');
        modalBackdrop.className = 'vendor-order-modal-backdrop';
        modalBackdrop.innerHTML =
            '<section class="vendor-order-modal" role="dialog" aria-modal="true" aria-label="일별 주문 상세">' +
                '<div class="vendor-order-modal-head">' +
                    '<div><h3 data-order-modal-title>주문 상세</h3><p>수정한 단가는 선택한 날짜의 해당 주문에만 적용됩니다.</p></div>' +
                    '<button type="button" class="vendor-order-modal-close" aria-label="닫기">×</button>' +
                '</div>' +
                '<div class="vendor-order-modal-body" data-order-modal-body></div>' +
            '</section>';
        document.body.appendChild(modalBackdrop);

        var modalBody = modalBackdrop.querySelector('[data-order-modal-body]');
        var modalTitle = modalBackdrop.querySelector('[data-order-modal-title]');
        var closeButton = modalBackdrop.querySelector('.vendor-order-modal-close');

        function openModal(dayNumber) {
            var records = byDay.get(dayNumber) || [];
            modalTitle.textContent = year + '년 ' + month + '월 ' + dayNumber + '일 주문';
            modalBody.innerHTML = '';

            if (!records.length) {
                modalBody.innerHTML = '<div class="vendor-order-modal-empty">이 날짜에는 주문이 없습니다.</div>';
            } else {
                records.forEach(function (record) {
                    var card = document.createElement('div');
                    card.className = 'vendor-order-edit-card';

                    var info = document.createElement('div');
                    info.className = 'vendor-order-edit-info';
                    info.innerHTML =
                        '<strong>' + escapeHtml(record.itemName) + '</strong>' +
                        '<div class="vendor-order-edit-meta">' +
                            '<span>수량 ' + escapeHtml(record.quantity) + '</span>' +
                            '<span>주문번호 ' + escapeHtml(record.orderNumber) + '</span>' +
                        '</div>' +
                        '<div class="vendor-order-edit-amount">현재 금액 ' + escapeHtml(record.amountText || '-') + '</div>';

                    card.appendChild(info);

                    if (record.form) {
                        var formClone = record.form.cloneNode(true);
                        formClone.className = 'vendor-order-edit-form';
                        var input = formClone.querySelector('input[name="unitPrice"]');
                        if (input) {
                            input.value = record.unitPrice;
                            input.setAttribute('aria-label', record.itemName + ' 단가');
                        }
                        var submit = formClone.querySelector('button[type="submit"]');
                        if (submit) submit.textContent = '단가 변경';
                        card.appendChild(formClone);
                    }

                    modalBody.appendChild(card);
                });
            }

            modalBackdrop.classList.add('open');
            document.body.style.overflow = 'hidden';
            closeButton.focus();
        }

        function closeModal() {
            modalBackdrop.classList.remove('open');
            document.body.style.overflow = '';
        }

        closeButton.addEventListener('click', closeModal);
        modalBackdrop.addEventListener('click', function (event) {
            if (event.target === modalBackdrop) closeModal();
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && modalBackdrop.classList.contains('open')) closeModal();
        });
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', load);
    } else {
        load();
    }
})();
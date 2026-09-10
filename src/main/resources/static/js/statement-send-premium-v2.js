(function () {
    function clean(value) {
        return String(value || '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function findByText(selector, needle) {
        var nodes = document.querySelectorAll(selector);

        for (var i = 0; i < nodes.length; i++) {
            if (clean(nodes[i].textContent).indexOf(needle) >= 0) {
                return nodes[i];
            }
        }

        return null;
    }

    function nearestContainer(nodes) {
        if (!nodes || !nodes.length) return null;

        var current = nodes[0];

        while (current) {
            var ok = true;

            for (var i = 1; i < nodes.length; i++) {
                if (!current.contains(nodes[i])) {
                    ok = false;
                    break;
                }
            }

            if (ok) return current;

            current = current.parentElement;
        }

        return null;
    }

    function findPreview() {
        var capture = document.getElementById('statementCapture');

        if (capture) return capture;

        var tables = document.querySelectorAll('table');

        for (var i = 0; i < tables.length; i++) {
            var header = clean(
                tables[i].querySelector('thead')
                ? tables[i].querySelector('thead').textContent
                : ''
            );

            if (
                header.indexOf('날짜') >= 0
                && header.indexOf('일계') >= 0
            ) {
                var node = tables[i];

                for (var depth = 0; depth < 4 && node.parentElement; depth++) {
                    node = node.parentElement;
                }

                return node;
            }
        }

        return null;
    }

    document.addEventListener('DOMContentLoaded', function () {
        var shareButton =
            document.getElementById('shareStatementBtn')
            || findByText('button, a', '이미지로 바로 공유');

        var pngButton =
            findByText('button, a', 'PNG 다운로드');

        var pdfButton =
            findByText('button, a', 'PDF 다운로드');

        var viewButton =
            findByText('button, a', '명세서 보기');

        var monthInput =
            document.querySelector('input[type="month"]');

        var vendorSelect =
            document.querySelector('select');

        if (!shareButton || !viewButton || !monthInput || !vendorSelect) {
            return;
        }

        document.body.classList.add('statement-send-premium');

        shareButton.classList.add('statement-ui-primary');
        viewButton.classList.add('statement-ui-primary');

        if (pngButton) {
            pngButton.classList.add('statement-ui-secondary');
        }

        if (pdfButton) {
            pdfButton.classList.add('statement-ui-outline');
        }

        var filterCard =
            nearestContainer(
                [monthInput, vendorSelect, viewButton]
            );

        if (filterCard) {
            filterCard.classList.add('statement-filter-card');
        }

        var actions = [shareButton];

        if (pngButton) actions.push(pngButton);
        if (pdfButton) actions.push(pdfButton);

        var actionRow =
            nearestContainer(actions);

        if (actionRow) {
            actionRow.classList.add('statement-action-row');
        }

        var h1 =
            document.querySelector('h1');

        if (h1) {
            h1.classList.add('statement-page-title');

            if (
                !h1.previousElementSibling
                || !h1.previousElementSibling.classList.contains(
                    'statement-page-kicker'
                )
            ) {
                var kicker =
                    document.createElement('div');

                kicker.className =
                    'statement-page-kicker';

                kicker.textContent =
                    '거래처 명세서';

                h1.parentNode.insertBefore(
                    kicker,
                    h1
                );
            }

            if (h1.nextElementSibling) {
                h1.nextElementSibling.classList.add(
                    'statement-page-desc'
                );
            }
        }

        var preview =
            findPreview();

        if (
            preview
            && !preview.classList.contains(
                'statement-preview-card'
            )
        ) {
            preview.classList.add(
                'statement-preview-card'
            );

            if (
                !preview.querySelector(
                    ':scope > .statement-paper'
                )
            ) {
                var paper =
                    document.createElement('div');

                paper.className =
                    'statement-paper';

                while (preview.firstChild) {
                    paper.appendChild(
                        preview.firstChild
                    );
                }

                preview.appendChild(paper);
            }
        }

        var history =
            document.querySelector(
                '.sms-inline-history'
            );

        if (history) {
            history.classList.add(
                'statement-history-card'
            );
        }
    });
})();

(function () {
    function number(text) {
        var cleaned = String(text || '').replace(/[^0-9.-]/g, '');
        return cleaned ? Number(cleaned) : 0;
    }

    function money(value) {
        return Math.round(value || 0).toLocaleString('ko-KR') + '원';
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function injectStyles() {
        if (document.getElementById('dashboardAuditStyles')) return;
        var style = document.createElement('style');
        style.id = 'dashboardAuditStyles';
        style.textContent = `
            .dashboard-audit-note{margin:4px 0 14px;color:#6b7684;font-size:13px}
            .dashboard-audit-summary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:12px 0 16px}
            .dashboard-audit-summary>div{padding:14px 16px;border:1px solid #e8edf3;border-radius:13px;background:#f8fafc}
            .dashboard-audit-summary span{display:block;color:#6b7684;font-size:12px;font-weight:700}
            .dashboard-audit-summary strong{display:block;margin-top:5px;color:#191f28;font-size:20px;font-weight:800}
            .dashboard-audit-summary small{display:block;margin-top:4px;color:#8b95a1;font-size:11px}
            .dashboard-audit-summary .is-anomaly{background:#fff7e8;border-color:#ffd88a}
            .dashboard-audit-alert{display:flex;gap:8px;align-items:center;margin:0 0 14px;padding:11px 14px;border-radius:11px;background:#fff7e8;color:#8a5a00;font-size:13px;font-weight:700}
            .dashboard-vendor-audit{display:flex;flex-direction:column;gap:7px}
            .dashboard-vendor-row{width:100%;display:grid;grid-template-columns:minmax(0,1fr) auto 24px;align-items:center;gap:12px;padding:13px 15px;border:1px solid #e5e8eb!important;border-radius:12px!important;background:#fff!important;color:#191f28!important;text-align:left;cursor:pointer;box-shadow:none!important}
            .dashboard-vendor-row:hover{background:#f6f8fa!important;border-color:#b9c6d8!important}
            .dashboard-vendor-name strong{display:block;font-size:14px;font-weight:800}.dashboard-vendor-name small{display:block;margin-top:3px;color:#8b95a1;font-size:11px}
            .dashboard-vendor-total{font-size:15px;font-weight:900;white-space:nowrap}.dashboard-vendor-chevron{font-size:24px;color:#8b95a1}
            .dashboard-vendor-missing{display:inline-block;margin-left:7px;padding:2px 6px;border-radius:999px;background:#fff0df;color:#9d5d15;font-size:10px;font-weight:800}
            .dashboard-vendor-dialog{border:0;padding:0;background:transparent;max-width:min(560px,calc(100vw - 28px));width:100%}.dashboard-vendor-dialog::backdrop{background:rgba(15,23,42,.35);backdrop-filter:blur(2px)}
            .dashboard-vendor-dialog-card{background:#fff;border-radius:20px;padding:20px;box-shadow:0 20px 60px rgba(15,23,42,.22)}
            .dashboard-vendor-dialog-head{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:15px}.dashboard-vendor-dialog-head p{margin:0;color:#8b95a1;font-size:11px}.dashboard-vendor-dialog-head h3{margin:3px 0 0;font-size:22px}.dashboard-vendor-dialog-close{width:38px;height:38px;border:0!important;border-radius:12px!important;background:#f2f4f6!important;color:#4e5968!important;font-size:22px;cursor:pointer}
            .dashboard-vendor-dialog-total{display:flex;justify-content:space-between;align-items:center;padding:13px 14px;border-radius:12px;background:#f2f7ff;margin-bottom:12px}.dashboard-vendor-dialog-total span{color:#6b7684;font-size:12px}.dashboard-vendor-dialog-total strong{font-size:20px;color:#1b64da}
            .dashboard-vendor-item{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px;padding:11px 4px;border-bottom:1px solid #f0f2f4}.dashboard-vendor-item:last-child{border-bottom:0}.dashboard-vendor-item strong{display:block;font-size:13px}.dashboard-vendor-item span,.dashboard-vendor-item small{display:block;margin-top:2px;color:#6b7684;font-size:11px}.dashboard-vendor-item-money{text-align:right}.dashboard-vendor-item-money strong{font-size:13px}
            @media(max-width:800px){.dashboard-audit-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.dashboard-vendor-row{grid-template-columns:minmax(0,1fr) auto 18px}.dashboard-vendor-total{font-size:13px}}
        `;
        document.head.appendChild(style);
    }

    function selectedDate(detail) {
        var heading = detail.querySelector('h2');
        if (!heading) return null;
        var match = heading.textContent.match(/(20\d{2})-(\d{2})-(\d{2})/);
        return match ? new Date(match[1] + '-' + match[2] + '-' + match[3] + 'T12:00:00') : null;
    }

    function comparableAverage(detail, currentSales) {
        var date = selectedDate(detail);
        if (!date) return null;
        var isWeekend = date.getDay() === 0 || date.getDay() === 6;
        var values = [];
        document.querySelectorAll('.sales-calendar-day.has-sales').forEach(function (cell) {
            if (cell.classList.contains('selected-calendar-day')) return;
            var dayNode = cell.querySelector('.calendar-day-top strong');
            var salesNode = cell.querySelector('.calendar-day-sales strong');
            if (!dayNode || !salesNode) return;
            var day = Number(dayNode.textContent.trim());
            if (!day) return;
            var candidate = new Date(date.getFullYear(), date.getMonth(), day, 12, 0, 0);
            var candidateWeekend = candidate.getDay() === 0 || candidate.getDay() === 6;
            if (candidateWeekend === isWeekend) values.push(number(salesNode.textContent));
        });
        if (!values.length) return null;
        var avg = values.reduce(function (a, b) { return a + b; }, 0) / values.length;
        var deviation = avg === 0 ? 0 : ((currentSales - avg) / avg) * 100;
        return { average: avg, deviation: deviation, count: values.length, label: isWeekend ? '주말' : '평일' };
    }

    function upgradeDashboard() {
        if (window.location.pathname !== '/') return;
        var detail = document.querySelector('.daily-sales-detail');
        var table = detail && detail.querySelector('.dashboard-daily-table');
        if (!detail || !table || detail.dataset.vendorAuditUpgraded === 'true') return;

        var bodyRows = Array.from(table.querySelectorAll('tbody tr')).filter(function (row) {
            return !row.querySelector('.empty-cell') && row.children.length >= 6;
        });
        if (!bodyRows.length) return;

        injectStyles();
        var vendors = new Map();
        var total = 0;
        bodyRows.forEach(function (row) {
            var cells = row.children;
            var vendor = cells[1].textContent.trim();
            var item = cells[2].textContent.trim();
            var qty = cells[3].textContent.trim();
            var unitText = cells[4].textContent.trim();
            var amountText = cells[5].textContent.trim();
            var amount = amountText === '-' ? 0 : number(amountText);
            var missing = unitText === '-' || unitText.indexOf('미등록') >= 0;
            total += amount;
            if (!vendors.has(vendor)) vendors.set(vendor, { name: vendor, total: 0, orders: new Set(), items: [], missing: 0 });
            var data = vendors.get(vendor);
            data.total += amount;
            data.orders.add(cells[0].textContent.trim());
            data.items.push({ item: item, qty: qty, unit: unitText, amount: amountText });
            if (missing) data.missing += 1;
        });

        var list = Array.from(vendors.values()).sort(function (a, b) { return b.total - a.total; });
        var comp = comparableAverage(detail, total);
        var summary = detail.querySelector('.daily-detail-summary');
        if (summary) {
            var anomaly = comp && Math.abs(comp.deviation) >= 35;
            summary.className = 'dashboard-audit-summary';
            summary.innerHTML = `
                <div><span>일매출</span><strong>${money(total)}</strong><small>거래처 ${list.length}곳</small></div>
                <div><span>거래처</span><strong>${list.length}곳</strong><small>금액 큰 순서로 확인</small></div>
                <div><span>${comp ? comp.label : '동일 유형'} 평균</span><strong>${comp ? money(comp.average) : '-'}</strong><small>${comp ? '다른 ' + comp.label + ' ' + comp.count + '일 기준' : '비교자료 없음'}</small></div>
                <div class="${anomaly ? 'is-anomaly' : ''}"><span>평균 대비</span><strong>${comp ? (comp.deviation > 0 ? '+' : '') + Math.round(comp.deviation) + '%' : '-'}</strong><small>${anomaly ? '입력 확인 권장' : (comp ? '평소 범위' : '')}</small></div>`;
            if (anomaly) {
                var alert = document.createElement('div');
                alert.className = 'dashboard-audit-alert';
                alert.textContent = comp.deviation > 0
                    ? '⚠ 같은 ' + comp.label + ' 평균보다 매출이 크게 높습니다. 수량 중복이나 단가를 확인해보세요.'
                    : '⚠ 같은 ' + comp.label + ' 평균보다 매출이 크게 낮습니다. 빠진 거래처나 수량이 없는지 확인해보세요.';
                summary.insertAdjacentElement('afterend', alert);
            }
        }

        var heading = detail.querySelector('.panel-heading');
        if (heading && !heading.querySelector('.dashboard-audit-note')) {
            var note = document.createElement('p');
            note.className = 'dashboard-audit-note';
            note.textContent = '거래처별 총금액을 먼저 확인하고, 거래처를 누르면 품목·수량을 상세 확인할 수 있습니다.';
            heading.appendChild(note);
        }

        var wrapper = table.closest('.table-wrap');
        var listBox = document.createElement('div');
        listBox.className = 'dashboard-vendor-audit';
        list.forEach(function (vendor, index) {
            var id = 'dashboardVendorDialog' + index;
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'dashboard-vendor-row';
            button.innerHTML = `<span class="dashboard-vendor-name"><strong>${escapeHtml(vendor.name)}${vendor.missing ? '<em class="dashboard-vendor-missing">단가미등록 ' + vendor.missing + '</em>' : ''}</strong><small>주문 ${vendor.orders.size}건 · 품목 ${vendor.items.length}개</small></span><span class="dashboard-vendor-total">${money(vendor.total)}</span><span class="dashboard-vendor-chevron">›</span>`;

            var dialog = document.createElement('dialog');
            dialog.id = id;
            dialog.className = 'dashboard-vendor-dialog';
            dialog.innerHTML = `<div class="dashboard-vendor-dialog-card"><div class="dashboard-vendor-dialog-head"><div><p>거래처 상세</p><h3>${escapeHtml(vendor.name)}</h3></div><button type="button" class="dashboard-vendor-dialog-close" aria-label="닫기">×</button></div><div class="dashboard-vendor-dialog-total"><span>당일 총금액</span><strong>${money(vendor.total)}</strong></div><div>${vendor.items.map(function (item) { return '<div class="dashboard-vendor-item"><div><strong>' + escapeHtml(item.item) + '</strong><span>수량 ' + escapeHtml(item.qty) + '</span></div><div class="dashboard-vendor-item-money"><small>단가 ' + escapeHtml(item.unit) + '</small><strong>' + escapeHtml(item.amount) + '</strong></div></div>'; }).join('')}</div></div>`;
            button.addEventListener('click', function () { if (dialog.showModal) dialog.showModal(); });
            dialog.querySelector('.dashboard-vendor-dialog-close').addEventListener('click', function () { dialog.close(); });
            dialog.addEventListener('click', function (event) { if (event.target === dialog) dialog.close(); });
            listBox.appendChild(button);
            detail.appendChild(dialog);
        });
        wrapper.replaceWith(listBox);
        detail.dataset.vendorAuditUpgraded = 'true';
    }

    document.addEventListener('DOMContentLoaded', upgradeDashboard);
})();

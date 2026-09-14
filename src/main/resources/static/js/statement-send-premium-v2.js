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
    function money(value) {
        return Math.round(Number(value || 0)).toLocaleString('ko-KR') + '원';
    }

    function percent(value) {
        var numeric = Number(value || 0);
        return (numeric > 0 ? '+' : '') + Math.round(numeric) + '%';
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
            .dashboard-vendor-row{width:100%;display:grid;grid-template-columns:minmax(0,1fr) minmax(170px,auto) 24px;align-items:center;gap:12px;padding:13px 15px;border:1px solid #e5e8eb!important;border-radius:12px!important;background:#fff!important;color:#191f28!important;text-align:left;cursor:pointer;box-shadow:none!important}
            .dashboard-vendor-row:hover{background:#f6f8fa!important;border-color:#b9c6d8!important}
            .dashboard-vendor-row.is-high,.dashboard-vendor-row.is-low{border-color:#f3c56e!important;background:#fffaf0!important}
            .dashboard-vendor-name strong{display:block;font-size:14px;font-weight:800}.dashboard-vendor-name small{display:block;margin-top:3px;color:#8b95a1;font-size:11px}
            .dashboard-vendor-side{text-align:right}.dashboard-vendor-total{display:block;font-size:15px;font-weight:900;white-space:nowrap}.dashboard-vendor-compare{display:block;margin-top:3px;font-size:11px;color:#8b95a1;white-space:nowrap}.dashboard-vendor-row.is-high .dashboard-vendor-compare,.dashboard-vendor-row.is-low .dashboard-vendor-compare{color:#a56300;font-weight:800}
            .dashboard-vendor-chevron{font-size:24px;color:#8b95a1}.dashboard-vendor-missing{display:inline-block;margin-left:7px;padding:2px 6px;border-radius:999px;background:#fff0df;color:#9d5d15;font-size:10px;font-weight:800}.dashboard-vendor-anomaly{display:inline-block;margin-left:7px;padding:2px 7px;border-radius:999px;background:#fff1d6;color:#9a6200;font-size:10px;font-style:normal;font-weight:900}
            .dashboard-vendor-dialog{border:0;padding:0;background:transparent;max-width:min(560px,calc(100vw - 28px));width:100%}.dashboard-vendor-dialog::backdrop{background:rgba(15,23,42,.35);backdrop-filter:blur(2px)}
            .dashboard-vendor-dialog-card{background:#fff;border-radius:20px;padding:20px;box-shadow:0 20px 60px rgba(15,23,42,.22)}
            .dashboard-vendor-dialog-head{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:15px}.dashboard-vendor-dialog-head p{margin:0;color:#8b95a1;font-size:11px}.dashboard-vendor-dialog-head h3{margin:3px 0 0;font-size:22px}.dashboard-vendor-dialog-close{width:38px;height:38px;border:0!important;border-radius:12px!important;background:#f2f4f6!important;color:#4e5968!important;font-size:22px;cursor:pointer}
            .dashboard-vendor-dialog-total{display:flex;justify-content:space-between;align-items:center;padding:13px 14px;border-radius:12px;background:#f2f7ff;margin-bottom:8px}.dashboard-vendor-dialog-total span{color:#6b7684;font-size:12px}.dashboard-vendor-dialog-total strong{font-size:20px;color:#1b64da}
            .dashboard-vendor-dialog-average{display:flex;justify-content:space-between;gap:10px;margin-bottom:13px;padding:10px 14px;border-radius:11px;background:#f8fafc;color:#6b7684;font-size:12px}.dashboard-vendor-dialog-average strong{color:#191f28}.dashboard-vendor-dialog-average.is-anomaly{background:#fff7e8;color:#8a5a00}.dashboard-vendor-dialog-average.is-anomaly strong{color:#8a5a00}
            .dashboard-vendor-item{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px;padding:11px 4px;border-bottom:1px solid #f0f2f4}.dashboard-vendor-item:last-child{border-bottom:0}.dashboard-vendor-item strong{display:block;font-size:13px}.dashboard-vendor-item span,.dashboard-vendor-item small{display:block;margin-top:2px;color:#6b7684;font-size:11px}.dashboard-vendor-item-money{text-align:right}.dashboard-vendor-item-money strong{font-size:13px}
            @media(max-width:800px){.dashboard-audit-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.dashboard-vendor-row{grid-template-columns:minmax(0,1fr) auto 18px}.dashboard-vendor-total{font-size:13px}.dashboard-vendor-compare{white-space:normal}}
        `;
        document.head.appendChild(style);
    }

    function selectedDateText(detail) {
        var heading = detail.querySelector('h2');
        if (!heading) return '';
        var match = heading.textContent.match(/(20\d{2}-\d{2}-\d{2})/);
        return match ? match[1] : '';
    }

    function selectedMonthText(dateText) {
        return dateText ? dateText.slice(0, 7) : '';
    }

    function anomalyText(vendor) {
        if (vendor.anomalyLevel === 'HIGH') return '평균보다 높음';
        if (vendor.anomalyLevel === 'LOW') return '평균보다 낮음';
        if (vendor.anomalyLevel === 'INSUFFICIENT') return '비교자료 부족';
        return '평소 범위';
    }

    function anomalyClass(vendor) {
        if (vendor.anomalyLevel === 'HIGH') return 'is-high';
        if (vendor.anomalyLevel === 'LOW') return 'is-low';
        return '';
    }

    async function loadAuditData(detail) {
        var date = selectedDateText(detail);
        var month = selectedMonthText(date);
        if (!date || !month) return null;
        var response = await fetch('/sales-calendar/audit-data?month=' + encodeURIComponent(month) + '&date=' + encodeURIComponent(date), {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        });
        if (!response.ok) throw new Error('audit data ' + response.status);
        return await response.json();
    }

    async function upgradeDashboard() {
        if (window.location.pathname !== '/') return;
        var detail = document.querySelector('.daily-sales-detail');
        var table = detail && detail.querySelector('.dashboard-daily-table');
        if (!detail || !table || detail.dataset.vendorAuditUpgraded === 'true') return;

        injectStyles();

        var audit;
        try {
            audit = await loadAuditData(detail);
        } catch (error) {
            console.warn('거래처별 평균 비교 데이터를 불러오지 못했습니다.', error);
            return;
        }
        if (!audit || !Array.isArray(audit.selectedVendors)) return;

        var vendors = audit.selectedVendors;
        var anomalyCount = vendors.filter(function (vendor) {
            return vendor.anomalyLevel === 'HIGH' || vendor.anomalyLevel === 'LOW';
        }).length;
        var total = audit.selectedDay ? Number(audit.selectedDay.salesAmount || 0) : 0;

        var summary = detail.querySelector('.daily-detail-summary');
        if (summary) {
            summary.className = 'dashboard-audit-summary';
            summary.innerHTML = `
                <div><span>일매출</span><strong>${money(total)}</strong><small>거래처 ${vendors.length}곳</small></div>
                <div><span>거래처</span><strong>${vendors.length}곳</strong><small>각 거래처 자기 평균과 비교</small></div>
                <div><span>이상 거래처</span><strong>${anomalyCount}곳</strong><small>평균 대비 ±35% 이상</small></div>
                <div class="${anomalyCount ? 'is-anomaly' : ''}"><span>비교 기준</span><strong>거래처별 ${audit.selectedDay ? audit.selectedDay.dayTypeLabel : ''} 평균</strong><small>해당 거래처가 실제 주문한 날만 비교</small></div>`;

            if (anomalyCount) {
                var alert = document.createElement('div');
                alert.className = 'dashboard-audit-alert';
                alert.textContent = '⚠ 평소 주문금액과 차이가 큰 거래처 ' + anomalyCount + '곳을 위로 올렸습니다. 중복 입력이나 수량 누락을 우선 확인해보세요.';
                summary.insertAdjacentElement('afterend', alert);
            }
        }

        var heading = detail.querySelector('.panel-heading');
        if (heading && !heading.querySelector('.dashboard-audit-note')) {
            var note = document.createElement('p');
            note.className = 'dashboard-audit-note';
            note.textContent = '전체 일평균이 아니라 각 거래처의 평소 주문금액과 비교합니다. 이상 거래처는 목록 맨 위에 표시됩니다.';
            heading.appendChild(note);
        }

        var wrapper = table.closest('.table-wrap');
        if (!wrapper) return;
        var listBox = document.createElement('div');
        listBox.className = 'dashboard-vendor-audit';

        vendors.forEach(function (vendor, index) {
            var id = 'dashboardVendorDialog' + index;
            var isAnomaly = vendor.anomalyLevel === 'HIGH' || vendor.anomalyLevel === 'LOW';
            var hasAverage = Number(vendor.comparableDayCount || 0) > 0;
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'dashboard-vendor-row ' + anomalyClass(vendor);
            button.innerHTML = `<span class="dashboard-vendor-name"><strong>${escapeHtml(vendor.vendorName)}${vendor.missingPriceCount ? '<em class="dashboard-vendor-missing">단가미등록 ' + vendor.missingPriceCount + '</em>' : ''}${isAnomaly ? '<em class="dashboard-vendor-anomaly">확인</em>' : ''}</strong><small>주문 ${vendor.orderCount}건 · 품목 ${vendor.items.length}개</small></span><span class="dashboard-vendor-side"><span class="dashboard-vendor-total">${money(vendor.salesAmount)}</span><span class="dashboard-vendor-compare">${hasAverage ? '평균 ' + money(vendor.comparableAverageSales) + ' · ' + percent(vendor.deviationPercent) : '비교자료 없음'}${isAnomaly ? ' · ' + anomalyText(vendor) : ''}</span></span><span class="dashboard-vendor-chevron">›</span>`;

            var dialog = document.createElement('dialog');
            dialog.id = id;
            dialog.className = 'dashboard-vendor-dialog';
            var comparisonHtml = hasAverage
                ? `<div class="dashboard-vendor-dialog-average ${isAnomaly ? 'is-anomaly' : ''}"><span>${escapeHtml(vendor.dayTypeLabel)} 평균 ${money(vendor.comparableAverageSales)} · ${vendor.comparableDayCount}일 기준</span><strong>${percent(vendor.deviationPercent)} ${isAnomaly ? '· ' + anomalyText(vendor) : ''}</strong></div>`
                : `<div class="dashboard-vendor-dialog-average"><span>비교자료가 아직 부족합니다.</span><strong>-</strong></div>`;
            dialog.innerHTML = `<div class="dashboard-vendor-dialog-card"><div class="dashboard-vendor-dialog-head"><div><p>거래처 상세</p><h3>${escapeHtml(vendor.vendorName)}</h3></div><button type="button" class="dashboard-vendor-dialog-close" aria-label="닫기">×</button></div><div class="dashboard-vendor-dialog-total"><span>오늘 주문금액</span><strong>${money(vendor.salesAmount)}</strong></div>${comparisonHtml}<div>${vendor.items.map(function (item) { return '<div class="dashboard-vendor-item"><div><strong>' + escapeHtml(item.itemName) + '</strong><span>수량 ' + escapeHtml(item.quantity) + '</span></div><div class="dashboard-vendor-item-money"><small>단가 ' + (item.unitPrice == null ? '미등록' : money(item.unitPrice)) + '</small><strong>' + (item.lineAmount == null ? '-' : money(item.lineAmount)) + '</strong></div></div>'; }).join('')}</div></div>`;
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

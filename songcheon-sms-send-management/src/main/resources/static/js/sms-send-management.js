(function () {
    var path = window.location.pathname || '';

    if (
        path.indexOf('/statement-export') !== 0
        && path.indexOf('/statement') !== 0
    ) {
        return;
    }

    function normalized(value) {
        return (value || '').replace(/\s+/g, ' ').trim();
    }

    function renamePage() {
        document.querySelectorAll('h1, h2, a, button').forEach(function (el) {
            var value = normalized(el.textContent);

            if (value === '거래명세서 PDF · 이미지 · 공유'
                || value === '거래명세서 PDF·이미지·공유') {
                el.textContent = '문자발송';
            }

            if (value === 'PDF·이미지·공유'
                || value === 'PDF · 이미지 · 공유') {
                el.textContent = '문자발송';
            }
        });

        if (document.title.indexOf('PDF') >= 0 || document.title.indexOf('이미지') >= 0) {
            document.title = '송천 문자발송';
        }
    }

    function selectedMonth() {
        var params = new URLSearchParams(window.location.search);
        var q = params.get('month');
        if (q) return q;

        var input = document.querySelector('input[type="month"]');
        if (input && input.value) return input.value;

        return '';
    }

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var result = {};

        if (token && header) {
            result[header.getAttribute('content')] = token.getAttribute('content');
        }

        return result;
    }

    function formatSentAt(value) {
        if (!value) return '';
        return String(value).replace('T', ' ').substring(0, 16);
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function createHistoryArea() {
        if (document.querySelector('.sms-send-history')) return;

        var capture = document.getElementById('statementCapture');
        if (!capture) return;

        var area = document.createElement('section');
        area.className = 'sms-send-history';
        area.innerHTML =
            '<button type="button" class="sms-send-history-toggle">'
            + '이번 달 문자 발송완료 확인'
            + '</button>'
            + '<div class="sms-send-history-panel">'
            + '<div class="sms-send-history-head">'
            + '<strong>문자 발송완료</strong>'
            + '<span>이미지 공유가 완료되어 저장된 거래처입니다.</span>'
            + '</div>'
            + '<div class="sms-send-history-content">'
            + '<div class="sms-send-history-empty">불러오는 중...</div>'
            + '</div>'
            + '</div>';

        capture.insertAdjacentElement('afterend', area);

        var toggle = area.querySelector('.sms-send-history-toggle');
        var panel = area.querySelector('.sms-send-history-panel');

        toggle.addEventListener('click', function () {
            panel.classList.toggle('open');
        });

        loadLogs(area);
    }

    function loadLogs(area) {
        var month = selectedMonth();
        var url = '/statement-send/manage/logs';
        if (month) url += '?month=' + encodeURIComponent(month);

        fetch(url, { credentials: 'same-origin' })
            .then(function (response) {
                if (!response.ok) throw new Error('발송기록 조회 실패 ' + response.status);
                return response.json();
            })
            .then(function (rows) {
                renderLogs(area, rows || []);
            })
            .catch(function (error) {
                console.warn('[sms-send-history]', error);
                var content = area.querySelector('.sms-send-history-content');
                content.innerHTML = '<div class="sms-send-history-empty">발송기록을 불러오지 못했습니다.</div>';
            });
    }

    function renderLogs(area, rows) {
        var toggle = area.querySelector('.sms-send-history-toggle');
        var content = area.querySelector('.sms-send-history-content');

        toggle.textContent = '이번 달 문자 발송완료 ' + rows.length + '곳';

        if (!rows.length) {
            content.innerHTML = '<div class="sms-send-history-empty">아직 발송완료 기록이 없습니다.</div>';
            return;
        }

        var html = '<ul class="sms-send-history-list">';

        rows.forEach(function (row) {
            html += '<li class="sms-send-history-item">'
                + '<div class="sms-send-history-info">'
                + '<strong>' + escapeHtml(row.vendorName || '거래처') + '</strong>'
                + '<span>'
                + escapeHtml(row.month || '')
                + (row.sentAt ? ' · ' + escapeHtml(formatSentAt(row.sentAt)) : '')
                + '</span>'
                + '</div>'
                + '<button type="button" class="sms-send-delete" data-log-id="'
                + escapeHtml(row.id)
                + '">삭제</button>'
                + '</li>';
        });

        html += '</ul>';
        content.innerHTML = html;

        content.querySelectorAll('.sms-send-delete').forEach(function (button) {
            button.addEventListener('click', function () {
                deleteLog(area, button.getAttribute('data-log-id'));
            });
        });
    }

    function deleteLog(area, id) {
        if (!id) return;
        if (!window.confirm('이 문자 발송완료 기록을 삭제할까요?')) return;

        var headers = csrfHeaders();

        fetch(
            '/statement-send/manage/logs/' + encodeURIComponent(id) + '/delete',
            {
                method: 'POST',
                headers: headers,
                credentials: 'same-origin'
            }
        )
        .then(function (response) {
            if (!response.ok) throw new Error('삭제 실패 ' + response.status);
            return response.json();
        })
        .then(function () {
            loadLogs(area);
        })
        .catch(function (error) {
            console.warn('[sms-send-delete]', error);
            window.alert('발송기록을 삭제하지 못했습니다.');
        });
    }

    renamePage();
    createHistoryArea();

    window.setTimeout(function () {
        renamePage();
        createHistoryArea();
    }, 300);
})();

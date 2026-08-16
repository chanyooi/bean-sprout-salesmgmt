(function () {
    var path =
        window.location.pathname || '';

    if (
        path.indexOf('/statement-export') !== 0
        && path.indexOf('/statement') !== 0
    ) {
        return;
    }

    function normalized(value) {
        return (value || '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function renamePageAndRemoveLogTab() {
        document
            .querySelectorAll(
                'h1, h2, a, button'
            )
            .forEach(
                function (el) {
                    var value =
                        normalized(
                            el.textContent
                        );

                    if (
                        value === '거래명세서 PDF · 이미지 · 공유'
                        || value === '거래명세서 PDF·이미지·공유'
                    ) {
                        el.textContent =
                            '문자발송';
                    }

                    if (
                        value === 'PDF·이미지·공유'
                        || value === 'PDF · 이미지 · 공유'
                    ) {
                        el.textContent =
                            '문자발송';
                    }

                    /*
                     * 상단 별도 발송기록 탭 제거
                     */
                    if (
                        value === '발송 기록'
                        || value === '발송기록'
                    ) {
                        el.style.display =
                            'none';
                    }
                }
            );

        if (
            document.title.indexOf('PDF') >= 0
            || document.title.indexOf('이미지') >= 0
        ) {
            document.title =
                '송천 문자발송';
        }
    }

    function selectedMonth() {
        var params =
            new URLSearchParams(
                window.location.search
            );

        var query =
            params.get('month');

        if (query) {
            return query;
        }

        var input =
            document.querySelector(
                'input[type="month"]'
            );

        if (
            input
            && input.value
        ) {
            return input.value;
        }

        return '';
    }

    function csrfHeaders() {
        var result = {};

        var token =
            document.querySelector(
                'meta[name="_csrf"]'
            );

        var header =
            document.querySelector(
                'meta[name="_csrf_header"]'
            );

        if (
            token
            && header
        ) {
            result[
                header.getAttribute(
                    'content'
                )
            ] =
                token.getAttribute(
                    'content'
                );
        }

        return result;
    }

    function escapeHtml(value) {
        return String(
            value == null
                ? ''
                : value
        )
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }

    function formatSentAt(value) {
        if (!value) {
            return '';
        }

        return String(value)
            .replace('T', ' ')
            .substring(0, 16);
    }

    function createInlineHistory() {
        if (
            document.querySelector(
                '.sms-inline-history'
            )
        ) {
            return document.querySelector(
                '.sms-inline-history'
            );
        }

        var capture =
            document.getElementById(
                'statementCapture'
            );

        if (!capture) {
            return null;
        }

        var section =
            document.createElement(
                'section'
            );

        section.className =
            'sms-inline-history';

        section.innerHTML =
            '<div class="sms-inline-history-head">'
            + '<div>'
            + '<h3>문자 발송완료</h3>'
            + '<p>이미지 공유가 완료되면 거래처가 자동으로 등록됩니다.</p>'
            + '</div>'
            + '<span class="sms-inline-count">0곳</span>'
            + '</div>'
            + '<div class="sms-inline-history-content">'
            + '<div class="sms-inline-empty">발송완료 기록을 불러오는 중...</div>'
            + '</div>';

        capture.insertAdjacentElement(
            'afterend',
            section
        );

        return section;
    }

    function loadLogs() {
        var area =
            createInlineHistory();

        if (!area) {
            return;
        }

        var url =
            '/statement-send/manage/logs';

        var month =
            selectedMonth();

        if (month) {
            url +=
                '?month='
                + encodeURIComponent(
                    month
                );
        }

        fetch(
            url,
            {
                credentials:
                    'same-origin'
            }
        )
        .then(
            function (response) {
                if (!response.ok) {
                    throw new Error(
                        '발송기록 조회 실패 '
                        + response.status
                    );
                }

                return response.json();
            }
        )
        .then(
            function (rows) {
                renderLogs(
                    area,
                    rows || []
                );
            }
        )
        .catch(
            function (error) {
                console.warn(
                    '[sms-inline-history]',
                    error
                );

                area
                    .querySelector(
                        '.sms-inline-history-content'
                    )
                    .innerHTML =
                    '<div class="sms-inline-empty">발송완료 기록을 불러오지 못했습니다.</div>';
            }
        );
    }

    function renderLogs(
        area,
        rows
    ) {
        area
            .querySelector(
                '.sms-inline-count'
            )
            .textContent =
            rows.length
            + '곳';

        var content =
            area.querySelector(
                '.sms-inline-history-content'
            );

        if (!rows.length) {
            content.innerHTML =
                '<div class="sms-inline-empty">아직 발송완료한 거래처가 없습니다.</div>';

            return;
        }

        var html =
            '<div class="sms-inline-table-wrap">'
            + '<table class="sms-inline-table">'
            + '<thead><tr>'
            + '<th>거래처</th>'
            + '<th>정산월</th>'
            + '<th>발송완료</th>'
            + '<th></th>'
            + '</tr></thead>'
            + '<tbody>';

        rows.forEach(
            function (row) {
                html +=
                    '<tr>'
                    + '<td><strong>'
                    + escapeHtml(
                        row.vendorName
                        || '거래처'
                    )
                    + '</strong></td>'
                    + '<td>'
                    + escapeHtml(
                        row.month
                        || ''
                    )
                    + '</td>'
                    + '<td>'
                    + escapeHtml(
                        formatSentAt(
                            row.sentAt
                        )
                    )
                    + '</td>'
                    + '<td>'
                    + '<button type="button" class="sms-inline-delete" data-log-id="'
                    + escapeHtml(
                        row.id
                    )
                    + '">삭제</button>'
                    + '</td>'
                    + '</tr>';
            }
        );

        html +=
            '</tbody></table></div>';

        content.innerHTML =
            html;

        content
            .querySelectorAll(
                '.sms-inline-delete'
            )
            .forEach(
                function (button) {
                    button.addEventListener(
                        'click',
                        function () {
                            deleteLog(
                                button.getAttribute(
                                    'data-log-id'
                                )
                            );
                        }
                    );
                }
            );
    }

    function deleteLog(id) {
        if (!id) {
            return;
        }

        if (
            !window.confirm(
                '이 발송완료 기록을 삭제할까요?'
            )
        ) {
            return;
        }

        fetch(
            '/statement-send/manage/logs/'
            + encodeURIComponent(id)
            + '/delete',
            {
                method: 'POST',
                headers: csrfHeaders(),
                credentials:
                    'same-origin'
            }
        )
        .then(
            function (response) {
                if (!response.ok) {
                    throw new Error(
                        '삭제 실패 '
                        + response.status
                    );
                }

                return response.json();
            }
        )
        .then(
            function () {
                loadLogs();
            }
        )
        .catch(
            function (error) {
                console.warn(
                    '[sms-inline-delete]',
                    error
                );

                window.alert(
                    '발송완료 기록을 삭제하지 못했습니다.'
                );
            }
        );
    }

    /*
     * 기존 statement_export의 markSent()는 공유 성공 후
     * /statement-send/mark-sent 를 fetch 합니다.
     *
     * 그 요청이 성공하는 순간 이 표를 자동 새로고침합니다.
     */
    var originalFetch =
        window.fetch.bind(window);

    window.fetch =
        function () {
            var args =
                Array.prototype.slice.call(
                    arguments
                );

            var requestTarget =
                args.length > 0
                    ? args[0]
                    : '';

            var requestUrl =
                typeof requestTarget === 'string'
                    ? requestTarget
                    : (
                        requestTarget
                        && requestTarget.url
                        ? requestTarget.url
                        : ''
                    );

            var promise =
                originalFetch.apply(
                    window,
                    args
                );

            if (
                requestUrl.indexOf(
                    '/statement-send/mark-sent'
                ) >= 0
            ) {
                promise
                    .then(
                        function (response) {
                            if (response.ok) {
                                window.setTimeout(
                                    loadLogs,
                                    200
                                );
                            }
                        }
                    )
                    .catch(
                        function () {
                        }
                    );
            }

            return promise;
        };

    renamePageAndRemoveLogTab();
    createInlineHistory();
    loadLogs();

    window.setTimeout(
        function () {
            renamePageAndRemoveLogTab();
            createInlineHistory();
        },
        300
    );
})();

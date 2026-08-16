(function () {
    var shareButton =
        document.getElementById('shareStatementBtn');

    if (!shareButton) {
        var buttons =
            document.querySelectorAll('button, a');

        for (var i = 0; i < buttons.length; i++) {
            var label =
                (buttons[i].textContent || '')
                    .replace(/\s+/g, ' ')
                    .trim();

            if (label.indexOf('이미지로 바로 공유') >= 0) {
                shareButton = buttons[i];
                break;
            }
        }
    }

    if (!shareButton) {
        return;
    }

    var registeredKeys = {};

    function selectedVendorId() {
        var vendorSelect =
            document.getElementById('vendorSelect');

        if (!vendorSelect) {
            vendorSelect =
                document.querySelector(
                    'select[name="vendorId"]'
                );
        }

        return vendorSelect
            ? String(vendorSelect.value || '').trim()
            : '';
    }

    function selectedMonth() {
        var monthInput =
            document.getElementById('monthInput');

        if (!monthInput) {
            monthInput =
                document.querySelector(
                    'input[type="month"]'
                );
        }

        return monthInput
            ? String(monthInput.value || '').trim()
            : '';
    }

    function csrfHeaders() {
        var headers = {
            'Content-Type':
                'application/x-www-form-urlencoded;charset=UTF-8'
        };

        var token =
            document.querySelector(
                'meta[name="_csrf"]'
            );

        var header =
            document.querySelector(
                'meta[name="_csrf_header"]'
            );

        if (token && header) {
            headers[
                header.getAttribute('content')
            ] =
                token.getAttribute('content');
        }

        return headers;
    }

    function makeKey(
        vendorId,
        month
    ) {
        return vendorId + '|' + month;
    }

    function registerImmediately() {
        var vendorId =
            selectedVendorId();

        var month =
            selectedMonth();

        if (!vendorId || !month) {
            return Promise.resolve(false);
        }

        var key =
            makeKey(
                vendorId,
                month
            );

        if (registeredKeys[key]) {
            return Promise.resolve(true);
        }

        var body =
            new URLSearchParams();

        body.set(
            'vendorId',
            vendorId
        );

        body.set(
            'month',
            month
        );

        return window.fetch(
            '/statement-send/mark-sent',
            {
                method: 'POST',
                headers: csrfHeaders(),
                body: body.toString(),
                credentials: 'same-origin'
            }
        )
        .then(
            function (response) {
                if (!response.ok) {
                    throw new Error(
                        'mark-sent failed: '
                        + response.status
                    );
                }

                registeredKeys[key] =
                    true;

                /*
                 * 인라인 발송완료 표 V2가 window.fetch를 감시 중이라
                 * 이 요청 성공 후 자동 새로고침됩니다.
                 */
                return true;
            }
        )
        .catch(
            function (error) {
                console.warn(
                    '[register-on-click-v3]',
                    error
                );

                return false;
            }
        );
    }

    /*
     * capture=true로 기존 공유 처리보다 먼저 발송완료 저장
     */
    shareButton.addEventListener(
        'click',
        function () {
            registerImmediately();
        },
        true
    );
})();

(function () {
    var shareButton = document.getElementById('shareStatementBtn');

    if (!shareButton) {
        return;
    }

    var registeredKeys = {};

    function selectedVendorId() {
        var vendorSelect = document.getElementById('vendorSelect');

        if (!vendorSelect) {
            vendorSelect = document.querySelector('select[name="vendorId"]');
        }

        if (!vendorSelect) {
            return '';
        }

        return String(vendorSelect.value || '').trim();
    }

    function selectedMonth() {
        var monthInput = document.getElementById('monthInput');

        if (!monthInput) {
            monthInput = document.querySelector('input[type="month"]');
        }

        if (!monthInput) {
            return '';
        }

        return String(monthInput.value || '').trim();
    }

    function csrfHeaders() {
        var headers = {
            'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        };

        var csrfToken = document.querySelector('meta[name="_csrf"]');
        var csrfHeader = document.querySelector('meta[name="_csrf_header"]');

        if (csrfToken && csrfHeader) {
            headers[csrfHeader.getAttribute('content')] =
                csrfToken.getAttribute('content');
        }

        return headers;
    }

    function makeKey(vendorId, month) {
        return vendorId + '|' + month;
    }

    function registerImmediately() {
        var vendorId = selectedVendorId();
        var month = selectedMonth();

        if (!vendorId || !month) {
            return Promise.resolve(false);
        }

        var key = makeKey(vendorId, month);

        if (registeredKeys[key]) {
            return Promise.resolve(true);
        }

        var body = new URLSearchParams();
        body.set('vendorId', vendorId);
        body.set('month', month);

        return window.fetch(
            '/statement-send/mark-sent',
            {
                method: 'POST',
                headers: csrfHeaders(),
                body: body.toString(),
                credentials: 'same-origin'
            }
        )
        .then(function (response) {
            if (!response.ok) {
                throw new Error(
                    'mark-sent failed: ' + response.status
                );
            }

            registeredKeys[key] = true;

            window.dispatchEvent(
                new CustomEvent(
                    'songcheon:statement-sent-registered',
                    {
                        detail: {
                            vendorId: vendorId,
                            month: month
                        }
                    }
                )
            );

            return true;
        })
        .catch(function (error) {
            console.warn(
                '[register-on-click-v2]',
                error
            );

            return false;
        });
    }

    /*
     * capture=true:
     * 기존 statement_export.html의 공유 click handler보다 먼저 실행됩니다.
     */
    shareButton.addEventListener(
        'click',
        function () {
            registerImmediately();
        },
        true
    );

    /*
     * 기존 공유 성공 후 markSent()가 다시 같은 요청을 보낼 수 있습니다.
     * 한 페이지 안에서 같은 거래처/월은 두 번째 요청을 막습니다.
     */
    var nativeFetch = window.fetch.bind(window);

    window.fetch = function () {
        var args = Array.prototype.slice.call(arguments);
        var request = args.length > 0 ? args[0] : '';
        var options = args.length > 1 ? args[1] || {} : {};

        var url =
            typeof request === 'string'
                ? request
                : (
                    request && request.url
                        ? request.url
                        : ''
                );

        if (url.indexOf('/statement-send/mark-sent') >= 0) {
            var bodyText = '';

            if (typeof options.body === 'string') {
                bodyText = options.body;
            } else if (options.body instanceof URLSearchParams) {
                bodyText = options.body.toString();
            }

            if (bodyText) {
                var params = new URLSearchParams(bodyText);
                var vendorId = params.get('vendorId') || '';
                var month = params.get('month') || '';
                var key = makeKey(vendorId, month);

                if (
                    vendorId
                    && month
                    && registeredKeys[key]
                ) {
                    return Promise.resolve(
                        new Response(
                            JSON.stringify({
                                ok: true,
                                duplicatePrevented: true
                            }),
                            {
                                status: 200,
                                headers: {
                                    'Content-Type': 'application/json'
                                }
                            }
                        )
                    );
                }
            }
        }

        return nativeFetch.apply(
            window,
            args
        );
    };
})();

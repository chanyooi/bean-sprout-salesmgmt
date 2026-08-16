(function () {
    var shareButton = document.getElementById('shareStatementBtn');

    if (!shareButton) {
        var candidates = document.querySelectorAll('button, a');

        for (var i = 0; i < candidates.length; i++) {
            var label = (candidates[i].textContent || '')
                .replace(/\s+/g, ' ')
                .trim();

            if (label.indexOf('이미지로 바로 공유') >= 0) {
                shareButton = candidates[i];
                break;
            }
        }
    }

    if (!shareButton) {
        return;
    }

    var registered = false;

    function getVendorId() {
        var el = document.getElementById('vendorSelect');

        if (!el) {
            el = document.querySelector('select[name="vendorId"]');
        }

        return el ? String(el.value || '').trim() : '';
    }

    function getMonth() {
        var el = document.getElementById('monthInput');

        if (!el) {
            el = document.querySelector('input[type="month"]');
        }

        return el ? String(el.value || '').trim() : '';
    }

    function getCsrfHeaders() {
        var headers = {
            'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        };

        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');

        if (token && header) {
            headers[header.getAttribute('content')] =
                token.getAttribute('content');
        }

        return headers;
    }

    function markSentImmediately() {
        if (registered) {
            return;
        }

        var vendorId = getVendorId();
        var month = getMonth();

        if (!vendorId || !month) {
            return;
        }

        var body = new URLSearchParams();
        body.set('vendorId', vendorId);
        body.set('month', month);

        fetch('/statement-send/mark-sent', {
            method: 'POST',
            headers: getCsrfHeaders(),
            body: body.toString(),
            credentials: 'same-origin'
        })
        .then(function (response) {
            if (!response.ok) {
                throw new Error('mark-sent failed: ' + response.status);
            }

            registered = true;

            window.dispatchEvent(
                new CustomEvent(
                    'songcheon:sent-registered',
                    {
                        detail: {
                            vendorId: vendorId,
                            month: month
                        }
                    }
                )
            );
        })
        .catch(function (error) {
            console.warn('[sms-register-v4]', error);
        });
    }

    shareButton.addEventListener(
        'click',
        markSentImmediately,
        true
    );
})();

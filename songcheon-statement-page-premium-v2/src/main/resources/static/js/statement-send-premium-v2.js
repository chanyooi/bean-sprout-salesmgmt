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

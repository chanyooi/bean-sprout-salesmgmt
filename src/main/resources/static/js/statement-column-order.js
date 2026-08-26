(function () {
    var ORDER = [
        '두절',
        '일반',
        '곱슬',
        '3.5kg일반',
        '3.5kg곱슬',
        '숙주'
    ];

    function clean(value) {
        return String(value || '')
            .replace(/\s+/g, '')
            .trim();
    }

    function canonical(label) {
        var value = clean(label);

        if (
            value === '날짜'
            || value === '일계'
            || value === '합계'
        ) {
            return value;
        }

        if (
            value === '두절'
            || value === '두절kg'
        ) {
            return '두절';
        }

        if (
            value === '일반'
            || value === '일반콩나물'
            || value === '일반(소)콩나물'
            || value === '일반소콩나물'
        ) {
            return '일반';
        }

        if (
            value === '곱슬'
            || value === '곱슬콩나물'
        ) {
            return '곱슬';
        }

        if (
            value === '3.5kg일반'
            || value === '3.5일반'
        ) {
            return '3.5kg일반';
        }

        if (
            value === '3.5kg곱슬'
            || value === '3.5곱슬'
        ) {
            return '3.5kg곱슬';
        }

        if (value === '숙주') {
            return '숙주';
        }

        return value;
    }

    function rank(label) {
        var key = canonical(label);
        var index = ORDER.indexOf(key);

        if (index >= 0) {
            return index;
        }

        return 1000;
    }

    function isStatementTable(table) {
        var header = table.querySelector('thead tr');

        if (!header) {
            return false;
        }

        var labels = Array.prototype.map.call(
            header.children,
            function (cell) {
                return canonical(cell.textContent);
            }
        );

        return (
            labels.indexOf('날짜') >= 0
            && labels.indexOf('일계') >= 0
        );
    }

    function reorderTable(table) {
        if (
            !table
            || table.dataset.songcheonOrdered === 'true'
        ) {
            return;
        }

        var headerRow = table.querySelector('thead tr');

        if (!headerRow) {
            return;
        }

        var headers = Array.prototype.slice.call(
            headerRow.children
        );

        if (headers.length < 3) {
            return;
        }

        var dateIndex = -1;
        var totalIndex = -1;
        var productIndexes = [];
        var extraIndexes = [];

        headers.forEach(function (cell, index) {
            var key = canonical(cell.textContent);

            if (key === '날짜') {
                dateIndex = index;
                return;
            }

            if (key === '일계' || key === '합계') {
                totalIndex = index;
                return;
            }

            if (ORDER.indexOf(key) >= 0) {
                productIndexes.push(index);
                return;
            }

            extraIndexes.push(index);
        });

        if (dateIndex < 0 || totalIndex < 0) {
            return;
        }

        productIndexes.sort(function (a, b) {
            var ra = rank(headers[a].textContent);
            var rb = rank(headers[b].textContent);

            if (ra !== rb) {
                return ra - rb;
            }

            return a - b;
        });

        var newOrder = [dateIndex]
            .concat(productIndexes)
            .concat(extraIndexes)
            .concat([totalIndex]);

        function reorderRow(row) {
            var cells = Array.prototype.slice.call(
                row.children
            );

            if (cells.length !== headers.length) {
                return;
            }

            var fragment =
                document.createDocumentFragment();

            newOrder.forEach(function (oldIndex) {
                fragment.appendChild(
                    cells[oldIndex]
                );
            });

            row.appendChild(fragment);
        }

        reorderRow(headerRow);

        var bodyRows =
            table.querySelectorAll('tbody tr');

        bodyRows.forEach(function (row) {
            reorderRow(row);
        });

        table.dataset.songcheonOrdered = 'true';
    }

    function apply() {
        var tables =
            document.querySelectorAll('table');

        tables.forEach(function (table) {
            if (isStatementTable(table)) {
                reorderTable(table);
            }
        });
    }

    function injectWeeklyPaymentLink() {
        if ((window.location.pathname || '/') !== '/payments') {
            return;
        }
        if (document.querySelector('[data-weekly-payment-link]')) {
            return;
        }
        var buttonRow = document.querySelector('.hero .button-row');
        if (!buttonRow) {
            return;
        }
        var link = document.createElement('a');
        link.href = '/payments/weekly';
        link.className = 'secondary-link';
        link.dataset.weeklyPaymentLink = 'true';
        link.textContent = '주별 입금확인';
        buttonRow.insertBefore(link, buttonRow.firstChild);
    }

    document.addEventListener(
        'DOMContentLoaded',
        function () {
            apply();
            injectWeeklyPaymentLink();
        }
    );

    window.setTimeout(apply, 300);
    window.setTimeout(apply, 800);
    window.setTimeout(injectWeeklyPaymentLink, 300);

    if ((window.location.pathname || '/') === '/') {
        var associationScript = document.createElement('script');
        associationScript.src = '/js/association-credit-dashboard.js?v=20260825_1';
        associationScript.defer = true;
        document.head.appendChild(associationScript);
    }
})();

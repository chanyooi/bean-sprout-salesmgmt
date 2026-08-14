(function () {
    function text(el) {
        return (el && el.textContent || '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function won(value) {
        return new Intl.NumberFormat('ko-KR')
            .format(
                Math.round(
                    Number(value || 0)
                )
            )
            + '원';
    }

    function number(value) {
        return new Intl.NumberFormat('ko-KR')
            .format(
                Number(value || 0)
            );
    }

    function selectedMonth() {
        var params =
            new URLSearchParams(
                window.location.search
            );

        var queryMonth =
            params.get('month');

        if (queryMonth) {
            return queryMonth;
        }

        var input =
            document.querySelector(
                'input[type="month"]'
            );

        if (input && input.value) {
            return input.value;
        }

        var now = new Date();

        return now.getFullYear()
            + '-'
            + String(
                now.getMonth() + 1
            ).padStart(
                2,
                '0'
            );
    }

    function findProductTable() {
        var found = null;

        document
            .querySelectorAll('table')
            .forEach(
                function (table) {
                    if (found) {
                        return;
                    }

                    var header =
                        text(
                            table.querySelector(
                                'thead'
                            )
                            || table
                        );

                    if (
                        header.indexOf('판매 수량') >= 0
                        && header.indexOf('매출') >= 0
                        && header.indexOf('거래처') >= 0
                    ) {
                        found = table;
                    }
                }
            );

        return found;
    }

    function restoreReturnContainerAndHideTofuRows() {
        var table =
            findProductTable();

        if (!table) {
            return;
        }

        table
            .querySelectorAll(
                'tbody tr'
            )
            .forEach(
                function (row) {
                    var rowText =
                        text(row);

                    /*
                     * 손두부 / 두부판만 일반 품목표에서 제외.
                     * 회수통은 다시 표시한다.
                     */
                    if (
                        rowText.indexOf('손두부') >= 0
                        || rowText.indexOf('두부판') >= 0
                    ) {
                        row.classList.add(
                            'special-hidden-row'
                        );
                    }

                    if (
                        rowText.indexOf('회수통') >= 0
                    ) {
                        row.classList.remove(
                            'special-hidden-row'
                        );
                    }
                }
            );
    }

    function removeOldCards() {
        document
            .querySelectorAll(
                '.special-accounting-section'
            )
            .forEach(
                function (el) {
                    el.remove();
                }
            );
    }

    function addTofuTable(report) {
        var productTable =
            findProductTable();

        if (
            !productTable
            || document.querySelector(
                '.special-tofu-section'
            )
        ) {
            return;
        }

        var section =
            document.createElement(
                'section'
            );

        section.className =
            'special-tofu-section';

        section.innerHTML =
            '<div class="special-tofu-head">'
            + '<h3>손두부</h3>'
            + '<p>팔공 매입 → 아포농협 판매, 판 반납 수익까지 포함합니다.</p>'
            + '</div>'
            + '<table class="special-tofu-table">'
            + '<thead><tr>'
            + '<th>품목</th>'
            + '<th>판매 수량</th>'
            + '<th>판매매출</th>'
            + '<th>팔공 매입원가</th>'
            + '<th>판 반납</th>'
            + '<th>총 반영 매출</th>'
            + '<th>이익</th>'
            + '</tr></thead>'
            + '<tbody><tr>'
            + '<td><strong>손두부</strong></td>'
            + '<td>'
            + number(report.tofuResaleQty)
            + '</td>'
            + '<td>'
            + won(report.tofuResaleSales)
            + '</td>'
            + '<td>'
            + won(report.tofuPurchaseCost)
            + '</td>'
            + '<td>'
            + number(report.tofuTrayQty)
            + '판 / '
            + won(report.tofuTrayReturnRevenue)
            + '</td>'
            + '<td><strong>'
            + won(report.tofuTotalRevenue)
            + '</strong></td>'
            + '<td class="special-tofu-profit">'
            + won(report.tofuProfit)
            + '</td>'
            + '</tr></tbody></table>'
            + '<div class="special-tofu-note">'
            + '판 반납 수익은 1판당 2,000원으로 계산합니다.'
            + '</div>';

        productTable
            .parentElement
            .insertBefore(
                section,
                productTable.nextSibling
            );
    }

    function parseMoney(value) {
        var match =
            (value || '')
                .replace(/,/g, '')
                .match(
                    /-?\d+(?:\.\d+)?/
                );

        return match
            ? Number(match[0])
            : null;
    }

    function patchKpis(report) {
        document
            .querySelectorAll('*')
            .forEach(
                function (el) {
                    if (el.children.length) {
                        return;
                    }

                    var label =
                        text(el);

                    if (label === '월매출') {
                        var card =
                            el.closest(
                                '.metric-card, .sc-kpi-card, .card, article'
                            );

                        if (card) {
                            var strong =
                                card.querySelector(
                                    'strong'
                                );

                            if (strong) {
                                strong.textContent =
                                    won(
                                        report.adjustedSales
                                    );

                                if (
                                    !card.querySelector(
                                        '.special-adjusted-badge'
                                    )
                                ) {
                                    var badge =
                                        document.createElement(
                                            'span'
                                        );

                                    badge.className =
                                        'special-adjusted-badge';

                                    badge.textContent =
                                        '손두부·회수통 반영';

                                    el.appendChild(
                                        badge
                                    );
                                }
                            }
                        }
                    }

                    if (label === '예상이익') {
                        var profitCard =
                            el.closest(
                                '.metric-card, .sc-kpi-card, .card, article'
                            );

                        if (profitCard) {
                            var profitStrong =
                                profitCard.querySelector(
                                    'strong'
                                );

                            if (profitStrong) {
                                var oldValue =
                                    parseMoney(
                                        profitStrong.textContent
                                    );

                                if (oldValue !== null) {
                                    profitStrong.textContent =
                                        won(
                                            oldValue
                                            + Number(
                                                report.profitAdjustment || 0
                                            )
                                        );
                                }
                            }
                        }
                    }
                }
            );
    }

    fetch(
        '/api/special-item-accounting?month='
        + encodeURIComponent(
            selectedMonth()
        ),
        {
            credentials: 'same-origin'
        }
    )
    .then(
        function (response) {
            if (!response.ok) {
                throw new Error(
                    'accounting api '
                    + response.status
                );
            }

            return response.json();
        }
    )
    .then(
        function (report) {
            removeOldCards();
            restoreReturnContainerAndHideTofuRows();
            addTofuTable(report);
            patchKpis(report);
        }
    )
    .catch(
        function (error) {
            console.warn(
                '[special-item-accounting-v2]',
                error
            );
        }
    );
})();

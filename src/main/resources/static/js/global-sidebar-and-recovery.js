(function () {
    const path =
        window.location.pathname || '/';

    function group() {
        if (path === '/') return 'dashboard';
        if (path.startsWith('/upload')) return 'upload';
        if (path.startsWith('/statement')) return 'statement';
        if (path.startsWith('/vendor-management')
                || path.startsWith('/sales')
                || path.startsWith('/promotion')) return 'vendor';
        if (path.startsWith('/vendors')) return 'route';
        if (path.startsWith('/payment')) return 'payment';
        if (path.startsWith('/profit')) return 'profit';
        if (path.startsWith('/inventory')) return 'inventory';
        if (path.startsWith('/admin/users')) return 'users';
        return '';
    }

    function link(
            href,
            icon,
            label,
            key
    ) {
        const active =
            group() === key
                    ? ' active'
                    : '';

        return `
            <a href="${href}"
               class="${active.trim()}">
                <span class="dgs-icon">${icon}</span>
                <strong>${label}</strong>
            </a>
        `;
    }

    if (
        window.matchMedia(
            '(min-width: 641px)'
        ).matches
        && !document.body.classList.contains(
            'security-page'
        )
    ) {
        const sidebar =
            document.createElement('aside');

        sidebar.className =
            'desktop-global-sidebar';

        sidebar.innerHTML = `
            <a class="dgs-brand"
               href="/">
                <span class="dgs-brand-mark">송</span>
                <div>
                    <strong>송천</strong>
                    <small>매출 관리</small>
                </div>
            </a>

            <nav class="dgs-nav">
                ${link('/', '⌂', '대시보드', 'dashboard')}
                ${link('/upload', '↑', '주문 업로드', 'upload')}
                ${link('/statements', '▤', '거래명세서', 'statement')}
                ${link('/vendor-management', '◎', '거래처 관리', 'vendor')}
                ${link('/vendors', '⌖', '배송 코스', 'route')}
                ${link('/payments', '₩', '입금 관리', 'payment')}
                ${link('/profit', '↗', '원가·이익', 'profit')}
                ${link('/inventory', '◇', '재고 관리', 'inventory')}
            </nav>

            <div class="dgs-bottom">
                ${link('/admin/users', '♙', '사용자 관리', 'users')}
            </div>
        `;

        document.body.appendChild(
            sidebar
        );
    }

    if (path.startsWith('/upload')) {
        injectRecoveryCard();
    }

    function injectRecoveryCard() {
        if (
            document.querySelector(
                '.input-recovery-card'
            )
        ) {
            return;
        }

        const main =
            document.querySelector(
                'main.container, main'
            );

        if (!main) {
            return;
        }

        const current =
            new Date();

        const year =
            current.getFullYear();

        const monthNumber =
            String(
                current.getMonth() + 1
            ).padStart(2, '0');

        const today =
            String(
                current.getDate()
            ).padStart(2, '0');

        const card =
            document.createElement(
                'section'
            );

        card.className =
            'input-recovery-card';

        card.innerHTML = `
            <h2>현재 장부 복구</h2>
            <p>
                사이트 DB에 저장된 거래내역을 다시 엑셀로 내려받습니다.
                원본 파일을 잃어버렸을 때 1일부터 다시 정리할 필요가 없습니다.
            </p>

            <form class="input-recovery-grid"
                  action="/input-data/recovery"
                  method="get">
                <label>
                    정산월
                    <input type="month"
                           name="month"
                           value="${year}-${monthNumber}"
                           required>
                </label>

                <label>
                    기준일
                    <input type="date"
                           name="through"
                           value="${year}-${monthNumber}-${today}"
                           required>
                </label>

                <button type="submit"
                        class="input-recovery-button">
                    현재까지 장부 다운로드
                </button>
            </form>
        `;

        const hero =
            main.querySelector(
                '.hero'
            );

        if (
            hero
            && hero.nextSibling
        ) {
            main.insertBefore(
                card,
                hero.nextSibling
            );
        } else if (hero) {
            hero.after(card);
        } else {
            main.prepend(card);
        }
    }
})();

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
        if (path.startsWith('/profit') || path.startsWith('/bean-usage')) return 'profit';
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

    function statementMenu() {
        const isStatementGroup =
            group() === 'statement';

        const excelActive =
            path === '/statements'
                    || path.startsWith('/statements/');

        const smsActive =
            path.startsWith('/statement-export')
                    || path.startsWith('/statement-send');

        return `
            <div class="dgs-menu-group ${isStatementGroup ? 'open active' : ''}"
                 data-dgs-statement-group>
                <button type="button"
                        class="dgs-menu-toggle"
                        data-dgs-statement-toggle
                        aria-expanded="${isStatementGroup ? 'true' : 'false'}">
                    <span class="dgs-icon">▤</span>
                    <strong>거래명세서</strong>
                    <span class="dgs-menu-arrow">⌄</span>
                </button>
                <div class="dgs-submenu">
                    <a href="/statements"
                       class="${excelActive ? 'active' : ''}">
                        <span>엑셀 명세서</span>
                    </a>
                    <a href="/statement-export"
                       class="${smsActive ? 'active' : ''}">
                        <span>문자발송</span>
                    </a>
                </div>
            </div>
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
                ${statementMenu()}
                ${link('/vendor-management', '◎', '거래처 관리', 'vendor')}
                ${link('/vendors', '⌖', '배송 코스', 'route')}
                ${link('/payments', '₩', '입금 관리', 'payment')}
                ${link('/profit', '↗', '원가·이익', 'profit')}
            </nav>

            <div class="dgs-bottom">
                ${link('/admin/users', '♙', '사용자 관리', 'users')}
            </div>
        `;

        document.body.appendChild(
            sidebar
        );

        const statementGroup =
            sidebar.querySelector(
                '[data-dgs-statement-group]'
            );

        const statementToggle =
            sidebar.querySelector(
                '[data-dgs-statement-toggle]'
            );

        if (statementGroup && statementToggle) {
            statementToggle.addEventListener(
                'click',
                () => {
                    const open =
                        statementGroup.classList.toggle(
                            'open'
                        );

                    statementToggle.setAttribute(
                        'aria-expanded',
                        open ? 'true' : 'false'
                    );
                }
            );
        }
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

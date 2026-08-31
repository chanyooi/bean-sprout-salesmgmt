(function () {
    const path = window.location.pathname || '/';

    function group() {
        if (path === '/') return 'dashboard';
        if (path.startsWith('/daily-entry') || path.startsWith('/upload')) return 'entry';
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

    function icon(name) {
        const icons = {
            dashboard: `<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="2"></rect><rect x="14" y="3" width="7" height="7" rx="2"></rect><rect x="3" y="14" width="7" height="7" rx="2"></rect><rect x="14" y="14" width="7" height="7" rx="2"></rect></svg>`,
            entry: `<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"></rect><path d="M3 9h18M9 9v11M15 9v11"></path><path d="M6 6.5h12"></path></svg>`,
            statement: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12a2 2 0 0 1 2 2v16l-3-2-3 2-3-2-3 2-3-2V5a2 2 0 0 1 2-2Z"></path><path d="M8 8h8M8 12h8M8 16h5"></path></svg>`,
            vendor: `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3"></circle><path d="M3.5 19c.5-4 2.5-6 5.5-6s5 2 5.5 6"></path><circle cx="17" cy="9" r="2.2"></circle><path d="M15 14c3.1-.3 5.1 1.3 5.5 4.5"></path></svg>`,
            route: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h11v10H3z"></path><path d="M14 9h3.7L21 12.5V16h-7z"></path><circle cx="7" cy="18" r="2"></circle><circle cx="17.5" cy="18" r="2"></circle><path d="M9 18h6.5M3 18h2"></path></svg>`,
            payment: `<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="6" width="18" height="13" rx="3"></rect><path d="M3 10h18"></path><path d="M15 15h3"></path></svg>`,
            profit: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19V10M10 19V5M16 19v-7M22 19H2"></path><path d="m4 8 5-4 5 4 6-5"></path></svg>`,
            users: `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.2"></circle><path d="M5 20c.6-4.4 3-6.5 7-6.5s6.4 2.1 7 6.5"></path></svg>`
        };
        return icons[name] || icons.dashboard;
    }

    function link(href, iconName, label, description, key, tone) {
        const active = group() === key ? ' active' : '';
        return `<a href="${href}" class="dgs-card${active}" data-tone="${tone}">
            <span class="dgs-icon">${icon(iconName)}</span>
            <span class="dgs-copy"><strong>${label}</strong><small>${description}</small></span>
            <span class="dgs-card-arrow" aria-hidden="true">›</span>
        </a>`;
    }

    function desktopMenu(key, iconName, label, description, tone, items) {
        const open = group() === key;
        const links = items.map(item => {
            const active = item.active() ? 'active' : '';
            return `<a href="${item.href}" class="${active}"><span>${item.label}</span></a>`;
        }).join('');
        return `<div class="dgs-menu-group ${open ? 'open active' : ''}" data-dgs-menu-group data-tone="${tone}">
            <button type="button" class="dgs-menu-toggle" data-dgs-menu-toggle aria-expanded="${open ? 'true' : 'false'}">
                <span class="dgs-icon">${icon(iconName)}</span>
                <span class="dgs-copy"><strong>${label}</strong><small>${description}</small></span>
                <span class="dgs-menu-arrow">⌄</span>
            </button>
            <div class="dgs-submenu">${links}</div>
        </div>`;
    }

    function entryMenu() {
        return desktopMenu('entry', 'entry', '납품 입력', '직접입력 · 파일업로드', 'mint', [
            { href: '/daily-entry', label: '직접입력', active: () => path.startsWith('/daily-entry') },
            { href: '/upload', label: '파일업로드', active: () => path.startsWith('/upload') }
        ]);
    }

    function statementMenu() {
        return desktopMenu('statement', 'statement', '거래명세서', '엑셀 생성 · 문자 발송', 'violet', [
            { href: '/statements', label: '엑셀 명세서', active: () => path === '/statements' || path.startsWith('/statements/') },
            { href: '/statement-export', label: '문자발송', active: () => path.startsWith('/statement-export') || path.startsWith('/statement-send') }
        ]);
    }

    function paymentMenu() {
        return desktopMenu('payment', 'payment', '입금 관리', '주별입금 · 월별입금', 'amber', [
            { href: '/payments/weekly', label: '주별입금', active: () => path.startsWith('/payments/weekly') },
            { href: '/payments', label: '월별입금', active: () => path === '/payments' || (path.startsWith('/payments/') && !path.startsWith('/payments/weekly')) }
        ]);
    }

    function bindDesktopMenus(root) {
        root.querySelectorAll('[data-dgs-menu-group]').forEach(menu => {
            const toggle = menu.querySelector('[data-dgs-menu-toggle]');
            if (!toggle) return;
            toggle.addEventListener('click', () => {
                const open = menu.classList.toggle('open');
                toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            });
        });
    }

    if (window.matchMedia('(min-width: 641px)').matches
            && !document.body.classList.contains('security-page')) {
        const sidebar = document.createElement('aside');
        sidebar.className = 'desktop-global-sidebar';
        sidebar.innerHTML = `
            <a class="dgs-brand" href="/"><span class="dgs-brand-mark">송</span><div><strong>송천</strong><small>매출 관리</small></div></a>
            <div class="dgs-section-label">업무 메뉴</div>
            <nav class="dgs-nav">
                ${link('/', 'dashboard', '대시보드', '오늘의 운영 현황', 'dashboard', 'blue')}
                ${entryMenu()}
                ${statementMenu()}
                ${link('/vendor-management', 'vendor', '거래처 관리', '단가 · 판매내역', 'vendor', 'indigo')}
                ${link('/vendors', 'route', '배송 코스', '거래처 배송 순서', 'route', 'sky')}
                ${paymentMenu()}
                ${link('/profit', 'profit', '원가 · 이익', '원가와 수익 분석', 'profit', 'purple')}
            </nav>
            <div class="dgs-bottom">${link('/admin/users', 'users', '사용자 관리', '계정 · 권한 설정', 'users', 'slate')}</div>`;
        document.body.appendChild(sidebar);
        bindDesktopMenus(sidebar);
    }

    function injectMobileSidebarStyles() {
        if (document.querySelector('link[data-mobile-sidebar-pc-v3]')) return;
        const css = document.createElement('link');
        css.rel = 'stylesheet';
        css.href = '/css/mobile-sidebar-pc-v3.css?v=20260831_1';
        css.dataset.mobileSidebarPcV3 = 'true';
        document.head.appendChild(css);
    }

    function mobileCard(href, iconName, label, description, key, tone) {
        const active = group() === key ? ' is-active' : '';
        return `<a href="${href}" class="mobile-pc-card${active}" data-tone="${tone}">
            <span class="mobile-pc-icon">${icon(iconName)}</span>
            <span class="mobile-pc-copy"><strong>${label}</strong><small>${description}</small></span>
            <span class="mobile-pc-arrow" aria-hidden="true">›</span>
        </a>`;
    }

    function mobileMenu(key, iconName, label, description, items) {
        const open = group() === key;
        const links = items.map(item => `<a href="${item.href}" class="${item.active() ? 'is-active' : ''}">${item.label}</a>`).join('');
        return `<div class="mobile-pc-statement-group${open ? ' open is-active' : ''}" data-mobile-menu-group>
            <button type="button" class="mobile-pc-statement-toggle" data-mobile-menu-toggle aria-expanded="${open ? 'true' : 'false'}">
                <span class="mobile-pc-icon">${icon(iconName)}</span>
                <span class="mobile-pc-copy"><strong>${label}</strong><small>${description}</small></span>
                <span class="mobile-pc-arrow" aria-hidden="true">⌄</span>
            </button>
            <div class="mobile-pc-statement-submenu">${links}</div>
        </div>`;
    }

    function mobileEntryMenu() {
        return mobileMenu('entry', 'entry', '납품 입력', '직접입력 · 파일업로드', [
            { href: '/daily-entry', label: '직접입력', active: () => path.startsWith('/daily-entry') },
            { href: '/upload', label: '파일업로드', active: () => path.startsWith('/upload') }
        ]);
    }

    function mobileStatementMenu() {
        return mobileMenu('statement', 'statement', '거래명세서', '엑셀 생성 · 문자 발송', [
            { href: '/statements', label: '엑셀 명세서', active: () => path === '/statements' || path.startsWith('/statements/') },
            { href: '/statement-export', label: '문자발송', active: () => path.startsWith('/statement-export') || path.startsWith('/statement-send') }
        ]);
    }

    function mobilePaymentMenu() {
        return mobileMenu('payment', 'payment', '입금 관리', '주별입금 · 월별입금', [
            { href: '/payments/weekly', label: '주별입금', active: () => path.startsWith('/payments/weekly') },
            { href: '/payments', label: '월별입금', active: () => path === '/payments' || (path.startsWith('/payments/') && !path.startsWith('/payments/weekly')) }
        ]);
    }

    function upgradeMobileDrawer() {
        const nav = document.querySelector('.sc-mobile-drawer .sc-drawer-nav')
                || document.querySelector('.sp-mobile-drawer .sp-drawer-nav');
        if (!nav || nav.dataset.pcUpgraded === 'true') return;
        const hasUsers = Array.from(nav.querySelectorAll('a')).some(a => (a.getAttribute('href') || '').startsWith('/admin/users'));
        const label = document.createElement('div');
        label.className = 'mobile-pc-menu-label';
        label.textContent = '업무 메뉴';
        nav.parentNode.insertBefore(label, nav);
        nav.innerHTML = `
            ${mobileCard('/', 'dashboard', '대시보드', '오늘의 운영 현황', 'dashboard', 'blue')}
            ${mobileEntryMenu()}
            ${mobileStatementMenu()}
            ${mobileCard('/vendor-management', 'vendor', '거래처 관리', '단가 · 판매내역', 'vendor', 'indigo')}
            ${mobileCard('/vendors', 'route', '배송 코스', '거래처 배송 순서', 'route', 'sky')}
            ${mobilePaymentMenu()}
            ${mobileCard('/profit', 'profit', '원가 · 이익', '원가와 수익 분석', 'profit', 'purple')}
            ${hasUsers ? mobileCard('/admin/users', 'users', '사용자 관리', '계정 · 권한 설정', 'users', 'slate') : ''}`;
        nav.dataset.pcUpgraded = 'true';
        nav.querySelectorAll('[data-mobile-menu-group]').forEach(menu => {
            const toggle = menu.querySelector('[data-mobile-menu-toggle]');
            if (!toggle) return;
            toggle.addEventListener('click', () => {
                const open = menu.classList.toggle('open');
                toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            });
        });
    }

    if (window.matchMedia('(max-width: 640px)').matches
            && !document.body.classList.contains('security-page')) {
        injectMobileSidebarStyles();
        upgradeMobileDrawer();
    }

    function removeRedundantHeroQuickNav() {
        document.querySelectorAll('.hero .button-row').forEach(row => {
            const links = Array.from(row.querySelectorAll('a'));
            if (links.length < 2) return;
            const allowed = links.every(link => {
                const href = link.getAttribute('href') || '';
                return href === '/'
                        || href.startsWith('/payments')
                        || href.startsWith('/vendor-management')
                        || href.startsWith('/reports/monthly')
                        || href.startsWith('/upload')
                        || href.startsWith('/daily-entry')
                        || href.startsWith('/profit')
                        || href.startsWith('/statements')
                        || href.startsWith('/prices')
                        || href.startsWith('/sales')
                        || href.startsWith('/admin/safety');
            });
            if (allowed) row.remove();
        });
    }

    removeRedundantHeroQuickNav();

    if (path.startsWith('/upload')) injectRecoveryCard();

    function injectRecoveryCard() {
        if (document.querySelector('.input-recovery-card')) return;
        const main = document.querySelector('main.container, main');
        if (!main) return;
        const current = new Date();
        const year = current.getFullYear();
        const monthNumber = String(current.getMonth() + 1).padStart(2, '0');
        const today = String(current.getDate()).padStart(2, '0');
        const card = document.createElement('section');
        card.className = 'input-recovery-card';
        card.innerHTML = `<h2>현재 장부 복구</h2>
            <p>사이트 DB에 저장된 거래내역을 다시 엑셀로 내려받습니다. 원본 파일을 잃어버렸을 때 1일부터 다시 정리할 필요가 없습니다.</p>
            <form class="input-recovery-grid" action="/input-data/recovery" method="get">
                <label>정산월<input type="month" name="month" value="${year}-${monthNumber}" required></label>
                <label>기준일<input type="date" name="through" value="${year}-${monthNumber}-${today}" required></label>
                <button type="submit" class="input-recovery-button">현재까지 장부 다운로드</button>
            </form>`;
        const hero = main.querySelector('.hero');
        if (hero && hero.nextSibling) main.insertBefore(card, hero.nextSibling);
        else if (hero) hero.after(card);
        else main.prepend(card);
    }
})();

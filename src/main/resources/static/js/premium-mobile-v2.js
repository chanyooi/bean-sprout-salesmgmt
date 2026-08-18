(function () {
    if (!window.matchMedia('(max-width: 640px)').matches) return;

    const body = document.body;
    if (!body || body.classList.contains('security-page')) return;

    const path = window.location.pathname || '/';

    function icon(paths) {
        return '<svg viewBox="0 0 24 24" aria-hidden="true">' + paths + '</svg>';
    }

    const icons = {
        menu: icon('<path d="M4 7h16M4 12h16M4 17h16"/>'),
        back: icon('<path d="m15 18-6-6 6-6"/>'),
        home: icon('<path d="m3 11 9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>'),
        statement: icon('<path d="M6 3h9l4 4v14H6zM15 3v5h5M9 12h6M9 16h6"/>'),
        vendor: icon('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-3.9"/>'),
        payment: icon('<path d="M3 6h18v12H3zM3 10h18M7 15h4"/>'),
        profit: icon('<path d="M4 19V9M10 19V5M16 19v-7M3 19h18M16 7l4-4M20 3v5"/>')
    };

    function title() {
        const h1 = document.querySelector('main h1, .container h1, h1');
        if (h1 && h1.textContent.trim()) return h1.textContent.trim().replace(/\s+/g,' ');
        return document.title || '송천';
    }

    function group() {
        if (path === '/') return 'home';
        if (path.startsWith('/statement')) return 'statement';
        if (path.startsWith('/vendor') || path.startsWith('/sales') || path.startsWith('/promotion')) return 'vendor';
        if (path.startsWith('/payment')) return 'payment';
        if (path.startsWith('/profit') || path.startsWith('/bean-usage')) return 'profit';
        return '';
    }

    function esc(v) {
        return String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    function drawerLink(href, symbol, label, g) {
        const active = g && group() === g ? ' is-active' : '';
        return `<a href="${href}" class="${active.trim()}"><span class="sp-drawer-icon">${symbol}</span><strong>${label}</strong></a>`;
    }

    function bottomLink(href, svg, label, g) {
        const active = group() === g ? ' is-active' : '';
        return `<a href="${href}" class="${active.trim()}">${svg}<span>${label}</span></a>`;
    }

    const appbar = document.createElement('header');
    appbar.className = 'sp-mobile-appbar';
    appbar.innerHTML = `
        <button class="sp-appbar-button sp-left-action" type="button" aria-label="${path === '/' ? '메뉴 열기' : '뒤로 가기'}">${path === '/' ? icons.menu : icons.back}</button>
        <div class="sp-appbar-title">${esc(title())}</div>
        <span class="sp-appbar-button" aria-hidden="true"></span>
    `;
    body.appendChild(appbar);

    const backdrop = document.createElement('div');
    backdrop.className = 'sp-drawer-backdrop';

    const drawer = document.createElement('aside');
    drawer.className = 'sp-mobile-drawer';
    drawer.setAttribute('aria-hidden','true');
    drawer.innerHTML = `
        <div class="sp-drawer-head">
            <div class="sp-drawer-brand">
                <span class="sp-brand-mark">송</span>
                <div class="sp-drawer-brand-text"><strong>송천</strong><small>관리 시스템</small></div>
            </div>
            <button class="sp-drawer-close" type="button" aria-label="메뉴 닫기">×</button>
        </div>
        <div class="sp-drawer-section-label">업무 메뉴</div>
        <nav class="sp-drawer-nav">
            ${drawerLink('/', '⌂', '대시보드', 'home')}
            ${drawerLink('/upload', '↑', '주문업로드', '')}
            ${drawerLink('/statements', '▤', '거래명세서', 'statement')}
            ${drawerLink('/vendor-management', '◎', '거래처관리', 'vendor')}
            ${drawerLink('/payments', '₩', '입금관리', 'payment')}
            ${drawerLink('/profit', '↗', '원가·이익', 'profit')}
            ${drawerLink('/admin/users', '♙', '사용자관리', '')}
        </nav>
    `;
    body.appendChild(backdrop);
    body.appendChild(drawer);

    const bottom = document.createElement('nav');
    bottom.className = 'sp-bottom-nav';
    bottom.innerHTML = `
        ${bottomLink('/', icons.home, '홈', 'home')}
        ${bottomLink('/statements', icons.statement, '명세서', 'statement')}
        ${bottomLink('/vendor-management', icons.vendor, '거래처', 'vendor')}
        ${bottomLink('/payments', icons.payment, '입금', 'payment')}
        ${bottomLink('/profit', icons.profit, '원가', 'profit')}
    `;
    body.appendChild(bottom);

    function openDrawer() {
        drawer.classList.add('is-open');
        backdrop.classList.add('is-open');
        body.classList.add('sp-drawer-open');
        drawer.setAttribute('aria-hidden','false');
    }

    function closeDrawer() {
        drawer.classList.remove('is-open');
        backdrop.classList.remove('is-open');
        body.classList.remove('sp-drawer-open');
        drawer.setAttribute('aria-hidden','true');
    }

    appbar.querySelector('.sp-left-action').addEventListener('click', function () {
        if (path === '/') openDrawer();
        else if (history.length > 1) history.back();
        else location.href = '/';
    });
    drawer.querySelector('.sp-drawer-close').addEventListener('click', closeDrawer);
    backdrop.addEventListener('click', closeDrawer);
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeDrawer(); });
})();

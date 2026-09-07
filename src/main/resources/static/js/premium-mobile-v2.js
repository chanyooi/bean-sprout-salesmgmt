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

// Galaxy / Android: prepare the statement image before the user taps Share.
// This keeps navigator.share() inside the original user gesture and avoids
// large PNG canvas memory spikes that can occur on Android browsers.
(function () {
    if (!/Android/i.test(navigator.userAgent)) return;
    if (!window.location.pathname.startsWith('/statement-export')) return;

    const target = document.getElementById('statementCapture');
    const shareButton = document.getElementById('shareStatement');
    if (!target || !shareButton || typeof window.html2canvas !== 'function') return;

    const params = new URLSearchParams(window.location.search);
    const monthInput = document.querySelector('input[name="month"]');
    const vendorSelect = document.querySelector('select[name="vendorId"]');
    const month = params.get('month') || monthInput?.value || 'month';
    const vendorId = params.get('vendorId') || vendorSelect?.value || '';
    const vendorName = (vendorSelect?.selectedOptions?.[0]?.textContent || '명세서').trim();
    const safeName = vendorName.replace(/[\\/:*?"<>|]/g, '_');
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    let preparedBlob = null;
    let preparedFile = null;
    let preparing = null;

    function canvasToBlob(canvas, type, quality) {
        return new Promise((resolve, reject) => {
            canvas.toBlob(
                blob => blob ? resolve(blob) : reject(new Error('이미지 생성 실패')),
                type,
                quality
            );
        });
    }

    async function prepareShareFile(force) {
        if (!force && preparedFile && preparedBlob) return preparedFile;
        if (!force && preparing) return preparing;

        preparing = (async () => {
            if (document.fonts?.ready) {
                try { await document.fonts.ready; } catch (ignored) {}
            }

            const lowMemory = typeof navigator.deviceMemory === 'number' && navigator.deviceMemory <= 4;
            const scale = lowMemory ? 1 : 1.2;
            const canvas = await window.html2canvas(target, {
                scale,
                backgroundColor: '#ffffff',
                useCORS: true,
                logging: false,
                imageTimeout: 8000
            });

            let blob;
            try {
                blob = await canvasToBlob(canvas, 'image/jpeg', lowMemory ? 0.84 : 0.9);
            } catch (firstError) {
                blob = await canvasToBlob(canvas, 'image/jpeg', 0.78);
            }

            preparedBlob = blob;
            preparedFile = new File(
                [blob],
                `${month}_${safeName}_명세서.jpg`,
                { type: 'image/jpeg', lastModified: Date.now() }
            );
            return preparedFile;
        })();

        try {
            return await preparing;
        } finally {
            preparing = null;
        }
    }

    async function markSent() {
        if (!vendorId) return;
        const body = new URLSearchParams();
        body.set('vendorId', vendorId);
        body.set('month', month);
        const headers = { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' };
        if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
        try {
            await fetch('/statement-send/mark-sent', {
                method: 'POST',
                headers,
                body,
                credentials: 'same-origin'
            });
        } catch (ignored) {}
    }

    function downloadPreparedFile() {
        if (!preparedBlob || !preparedFile) return;
        const url = URL.createObjectURL(preparedBlob);
        const link = document.createElement('a');
        link.href = url;
        link.download = preparedFile.name;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1500);
    }

    async function warmup() {
        shareButton.disabled = true;
        shareButton.textContent = '공유용 이미지 준비 중...';
        try {
            await prepareShareFile(false);
            shareButton.textContent = '이미지로 바로 공유';
        } catch (error) {
            console.error('Android statement image warmup failed', error);
            shareButton.textContent = '이미지 다시 준비';
        } finally {
            shareButton.disabled = false;
        }
    }

    // Capture-phase handler runs before the older inline click listener.
    // On Android we stop that listener and use the prebuilt lightweight JPEG.
    shareButton.addEventListener('click', function (event) {
        event.preventDefault();
        event.stopImmediatePropagation();

        if (!preparedFile || !preparedBlob) {
            shareButton.disabled = true;
            shareButton.textContent = '이미지 준비 중...';
            prepareShareFile(true)
                .then(() => {
                    shareButton.textContent = '준비 완료 · 다시 눌러 공유';
                })
                .catch(error => {
                    console.error('Android statement image prepare retry failed', error);
                    shareButton.textContent = '이미지로 바로 공유';
                    alert('이미지 생성에 실패했습니다. 잠시 후 다시 눌러주세요.');
                })
                .finally(() => { shareButton.disabled = false; });
            return;
        }

        if (navigator.share && (!navigator.canShare || navigator.canShare({ files: [preparedFile] }))) {
            // Call share immediately, before any await, so Android keeps user activation.
            const sharePromise = navigator.share({
                title: `${safeName} ${month} 명세서`,
                text: `안녕하세요 송천 콩나물입니다. ${safeName} ${month} 명세서입니다. 감사합니다.`,
                files: [preparedFile]
            });

            shareButton.disabled = true;
            sharePromise
                .then(async () => {
                    await markSent();
                    shareButton.textContent = '공유 완료 · 발송기록 저장됨';
                })
                .catch(error => {
                    if (error?.name !== 'AbortError') {
                        console.error('Android Web Share failed', error);
                        downloadPreparedFile();
                        alert('공유창을 열지 못해 이미지 파일로 저장했습니다. 저장된 이미지를 문자에서 첨부해주세요.');
                    }
                    shareButton.textContent = '이미지로 바로 공유';
                })
                .finally(() => { shareButton.disabled = false; });
            return;
        }

        downloadPreparedFile();
        shareButton.textContent = '이미지 저장 완료';
    }, { capture: true });

    if ('requestIdleCallback' in window) {
        window.requestIdleCallback(warmup, { timeout: 1800 });
    } else {
        window.setTimeout(warmup, 350);
    }
})();

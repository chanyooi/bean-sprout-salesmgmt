(function () {
    loadVendorOrderCalendar();

    function loadVendorOrderCalendar() {
        if (!window.location.pathname.startsWith('/vendor-management/')) return;
        if (!document.querySelector('.order-table')) return;

        if (!document.querySelector('link[data-vendor-order-calendar]')) {
            var link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = '/css/vendor-order-calendar.css?v=20260819_1';
            link.dataset.vendorOrderCalendar = 'true';
            document.head.appendChild(link);
        }

        if (!document.querySelector('script[data-vendor-order-calendar]')) {
            var script = document.createElement('script');
            script.src = '/js/vendor-order-calendar.js?v=20260819_1';
            script.defer = true;
            script.dataset.vendorOrderCalendar = 'true';
            document.body.appendChild(script);
        }
    }

    if (!window.matchMedia('(min-width: 641px)').matches) return;

    function install() {
        if (document.querySelector('.songcheon-pc-logout-btn')) return true;

        var form = document.getElementById('songcheonPcLogoutForm');
        if (!form) return false;

        var target = document.querySelector('.desktop-global-sidebar .dgs-bottom');
        if (!target) {
            target = document.querySelector('.desktop-app-sidebar .desktop-sidebar-bottom');
        }
        if (!target) return false;

        var wrap = document.createElement('div');
        wrap.className = 'songcheon-pc-logout-wrap';
        wrap.innerHTML =
            '<button type="button" class="songcheon-pc-logout-btn">' +
            '<span class="songcheon-pc-logout-icon" aria-hidden="true">' +
            '<svg viewBox="0 0 24 24"><path d="M10 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5"></path><path d="M14 8l4 4-4 4"></path><path d="M8 12h10"></path></svg>' +
            '</span>' +
            '<strong>로그아웃</strong>' +
            '</button>';

        target.appendChild(wrap);

        wrap.querySelector('.songcheon-pc-logout-btn').addEventListener('click', function () {
            if (window.confirm('로그아웃하시겠습니까?')) {
                if (typeof form.requestSubmit === 'function') {
                    form.requestSubmit();
                } else {
                    form.submit();
                }
            }
        });

        return true;
    }

    var count = 0;
    var timer = setInterval(function () {
        count++;
        if (install() || count >= 40) {
            clearInterval(timer);
        }
    }, 100);

    window.addEventListener('load', install);
})();

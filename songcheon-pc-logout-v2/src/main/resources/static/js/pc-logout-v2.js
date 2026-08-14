(function () {
    if (!window.matchMedia('(min-width: 641px)').matches) {
        return;
    }

    function ensureLogout() {
        if (document.querySelector('.songcheon-pc-logout-btn')) {
            return true;
        }

        const form =
            document.getElementById('songcheonPcLogoutForm');

        if (!form) {
            return false;
        }

        let target =
            document.querySelector(
                '.desktop-global-sidebar .dgs-bottom'
            );

        if (!target) {
            target =
                document.querySelector(
                    '.desktop-app-sidebar .desktop-sidebar-bottom'
                );
        }

        if (!target) {
            return false;
        }

        const wrap =
            document.createElement('div');

        wrap.className =
            'songcheon-pc-logout-wrap';

        wrap.innerHTML = `
            <button type="button"
                    class="songcheon-pc-logout-btn"
                    aria-label="로그아웃">
                <span class="songcheon-pc-logout-icon">↪</span>
                <strong>로그아웃</strong>
            </button>
        `;

        target.appendChild(wrap);

        wrap
            .querySelector('.songcheon-pc-logout-btn')
            .addEventListener(
                'click',
                function () {
                    if (
                        window.confirm(
                            '로그아웃하시겠습니까?'
                        )
                    ) {
                        if (typeof form.requestSubmit === 'function') {
                            form.requestSubmit();
                        } else {
                            form.submit();
                        }
                    }
                }
            );

        return true;
    }

    let tries = 0;

    const timer =
        window.setInterval(
            function () {
                tries++;

                if (
                    ensureLogout()
                    || tries >= 40
                ) {
                    window.clearInterval(timer);
                }
            },
            100
        );

    window.addEventListener(
        'load',
        ensureLogout
    );
})();

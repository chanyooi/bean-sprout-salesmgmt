(function () {
    if (!window.matchMedia('(min-width: 641px)').matches) {
        return;
    }

    function installLogoutButton() {
        const sidebar =
            document.querySelector(
                '.desktop-global-sidebar'
            );

        if (!sidebar) {
            window.setTimeout(
                installLogoutButton,
                50
            );
            return;
        }

        if (
            sidebar.querySelector(
                '.dgs-logout-button'
            )
        ) {
            return;
        }

        const bottom =
            sidebar.querySelector(
                '.dgs-bottom'
            );

        if (!bottom) {
            return;
        }

        const visibleForm =
            document.createElement(
                'div'
            );

        visibleForm.className =
            'dgs-logout-form';

        visibleForm.innerHTML = `
            <button type="button"
                    class="dgs-logout-button">
                <span class="dgs-icon">↪</span>
                <strong>로그아웃</strong>
            </button>
        `;

        bottom.appendChild(
            visibleForm
        );

        const button =
            visibleForm.querySelector(
                '.dgs-logout-button'
            );

        button.addEventListener(
            'click',
            function () {
                const form =
                    document.getElementById(
                        'desktopLogoutSecurityForm'
                    );

                if (!form) {
                    window.alert(
                        '로그아웃 폼을 찾지 못했습니다.'
                    );
                    return;
                }

                form.requestSubmit();
            }
        );
    }

    installLogoutButton();
})();

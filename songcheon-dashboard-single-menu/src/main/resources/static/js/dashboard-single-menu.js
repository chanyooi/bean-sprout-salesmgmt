(function () {
    if (!window.matchMedia('(max-width: 640px)').matches) {
        return;
    }

    const path = window.location.pathname || '/';

    if (path === '/' || path === '') {
        document.body.classList.add('sp-dashboard-page');
    }
})();

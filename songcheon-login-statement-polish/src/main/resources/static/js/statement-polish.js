(function () {
    var path = window.location.pathname || '';

    if (
        path === '/statements'
        || path.indexOf('/statements/') === 0
        || path === '/statement'
        || path.indexOf('/statement/') === 0
    ) {
        document.body.classList.add('songcheon-statements-polished');
    }
})();

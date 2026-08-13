(function () {
    if (!window.matchMedia('(max-width: 640px)').matches) return;

    function cleanText(el) {
        return (el.textContent || '').replace(/\s+/g, ' ').trim();
    }

    document.querySelectorAll('a, button').forEach(function (el) {
        const text = cleanText(el);

        if (text.includes('새 월 장부 다운로드')) {
            el.classList.add('sp-context-action', 'sp-context-primary', 'sp-upload-download-action');
        }

        if (text === '수익분석' || text === '수익 분석') {
            el.classList.add('sp-context-action', 'sp-profit-analysis-action');
        }
    });
})();

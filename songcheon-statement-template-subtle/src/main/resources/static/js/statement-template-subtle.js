(function () {
    var path = window.location.pathname || '';
    if (
        path !== '/statements'
        && path.indexOf('/statements/') !== 0
        && path !== '/statement'
        && path.indexOf('/statement/') !== 0
    ) {
        return;
    }

    document.body.classList.add('songcheon-statements-polished');

    function normalizeText(value) {
        return (value || '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function findTextElement(text) {
        var nodes = document.querySelectorAll('label, span, strong, div, p');
        for (var i = 0; i < nodes.length; i++) {
            if (normalizeText(nodes[i].textContent) === text) {
                return nodes[i];
            }
        }
        return null;
    }

    function closestBlockWithFileInput(el) {
        var node = el;
        for (var i = 0; i < 6 && node; i++, node = node.parentElement) {
            if (
                node.querySelector
                && node.querySelector('input[type="file"]')
            ) {
                return node;
            }
        }
        return null;
    }

    function applyOptionalStyle() {
        var templateLabel =
            findTextElement('새 템플릿 사용 (선택)');

        if (!templateLabel) return false;

        var templateBlock =
            closestBlockWithFileInput(templateLabel);

        if (!templateBlock) return false;

        templateBlock.classList.add('statement-template-optional');

        var helpCandidates =
            templateBlock.querySelectorAll('p, small, .help, .hint');

        for (var i = 0; i < helpCandidates.length; i++) {
            var text = normalizeText(
                helpCandidates[i].textContent
            );
            if (
                text.indexOf('파일을 선택하지 않으면') !== -1
                || text.indexOf('template.xlsx') !== -1
            ) {
                helpCandidates[i].classList.add('statement-align-help');
            }
        }

        return true;
    }

    if (!applyOptionalStyle()) {
        var tries = 0;
        var timer = setInterval(function () {
            tries++;
            if (applyOptionalStyle() || tries >= 20) {
                clearInterval(timer);
            }
        }, 100);
    }
})();

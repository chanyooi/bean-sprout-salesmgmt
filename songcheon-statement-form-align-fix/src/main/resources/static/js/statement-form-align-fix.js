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

    function closestUsefulBlock(el, inputType) {
        if (!el) return null;

        var node = el;
        for (var i = 0; i < 5 && node; i++, node = node.parentElement) {
            if (
                node.querySelector
                && node.querySelector('input[type="' + inputType + '"]')
            ) {
                return node;
            }
        }
        return null;
    }

    function applyAlignment() {
        var monthLabel = findTextElement('생성 월');
        var templateLabel = findTextElement('새 템플릿 사용 (선택)');

        if (!monthLabel || !templateLabel) {
            return false;
        }

        var monthBlock = closestUsefulBlock(monthLabel, 'month');
        var templateBlock = closestUsefulBlock(templateLabel, 'file');

        if (!monthBlock || !templateBlock) {
            return false;
        }

        var parent = monthBlock.parentElement;
        if (!parent || templateBlock.parentElement !== parent) {
            return false;
        }

        parent.classList.add('statement-align-row');

        monthBlock.classList.add(
            'statement-align-field',
            'month-field'
        );

        templateBlock.classList.add(
            'statement-align-field',
            'template-field'
        );

        monthLabel.classList.add('statement-align-label');
        templateLabel.classList.add('statement-align-label');

        var templateHelpCandidates =
            templateBlock.querySelectorAll('p, small, .help, .hint');

        for (var i = 0; i < templateHelpCandidates.length; i++) {
            var text = normalizeText(
                templateHelpCandidates[i].textContent
            );

            if (
                text.indexOf('파일을 선택하지 않으면') !== -1
                || text.indexOf('template.xlsx') !== -1
            ) {
                templateHelpCandidates[i]
                    .classList
                    .add('statement-align-help');
            }
        }

        return true;
    }

    if (!applyAlignment()) {
        var tries = 0;
        var timer = setInterval(function () {
            tries++;
            if (applyAlignment() || tries >= 20) {
                clearInterval(timer);
            }
        }, 100);
    }
})();

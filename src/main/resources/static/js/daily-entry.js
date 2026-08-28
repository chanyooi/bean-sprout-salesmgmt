(function () {
    const form = document.querySelector('[data-daily-entry-form]');
    if (!form) return;

    const dateInput = document.querySelector('[data-entry-date]');
    const filterButton = document.querySelector('[data-entry-filter]');
    const rows = Array.from(document.querySelectorAll('[data-entry-row]'));
    const editableInputs = Array.from(form.querySelectorAll('input[data-entry-input]'));
    let dirty = false;
    let showFilledOnly = false;

    function hasMeaningfulValue(input) {
        return String(input.value || '').trim() !== '';
    }

    function refreshInputState(input) {
        input.classList.toggle('entry-has-value', hasMeaningfulValue(input));
    }

    function rowHasInput(row) {
        return Array.from(row.querySelectorAll('input[data-entry-input]'))
                .some(hasMeaningfulValue);
    }

    function applyFilter() {
        rows.forEach(row => {
            row.classList.toggle('entry-hidden-row', showFilledOnly && !rowHasInput(row));
        });
        if (filterButton) {
            filterButton.classList.toggle('daily-entry-filter-active', showFilledOnly);
            filterButton.textContent = showFilledOnly ? '전체 거래처 보기' : '입력 있는 거래처만';
        }
    }

    editableInputs.forEach(input => {
        refreshInputState(input);

        input.addEventListener('input', () => {
            dirty = true;
            refreshInputState(input);
            if (showFilledOnly) applyFilter();
        });

        input.addEventListener('focus', () => {
            if (input.type === 'number') input.select();
        });

        input.addEventListener('wheel', event => {
            if (document.activeElement === input && input.type === 'number') {
                input.blur();
            }
        }, {passive: true});

        input.addEventListener('keydown', event => {
            if (event.key !== 'Enter') return;
            event.preventDefault();

            const column = input.dataset.entryCol;
            if (!column) return;

            const sameColumn = Array.from(
                    form.querySelectorAll(`input[data-entry-col="${column}"]`)
            ).filter(candidate => !candidate.closest('tr').classList.contains('entry-hidden-row'));

            const currentIndex = sameColumn.indexOf(input);
            if (currentIndex < 0) return;

            const nextIndex = event.shiftKey ? currentIndex - 1 : currentIndex + 1;
            if (sameColumn[nextIndex]) {
                sameColumn[nextIndex].focus();
            }
        });
    });

    if (dateInput) {
        dateInput.addEventListener('change', () => {
            const value = dateInput.value;
            if (!value) return;
            if (dirty && !window.confirm('저장하지 않은 입력이 있습니다. 날짜를 이동할까요?')) {
                return;
            }
            window.location.href = `/daily-entry?date=${encodeURIComponent(value)}`;
        });
    }

    if (filterButton) {
        filterButton.addEventListener('click', () => {
            showFilledOnly = !showFilledOnly;
            applyFilter();
        });
    }

    form.addEventListener('submit', () => {
        dirty = false;
        const saveButtons = form.querySelectorAll('button[type="submit"]');
        saveButtons.forEach(button => {
            button.disabled = true;
            button.textContent = '저장 중...';
        });
    });

    window.addEventListener('beforeunload', event => {
        if (!dirty) return;
        event.preventDefault();
        event.returnValue = '';
    });
})();

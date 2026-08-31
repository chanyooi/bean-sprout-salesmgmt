(function () {
    const root = document.querySelector('[data-week-calendar]');
    if (!root) return;

    const body = root.querySelector('[data-calendar-body]');
    const title = root.querySelector('[data-calendar-title]');
    const previousButton = root.querySelector('[data-calendar-prev]');
    const nextButton = root.querySelector('[data-calendar-next]');
    const todayButton = root.querySelector('[data-calendar-today]');
    const selectedText = root.dataset.selectedWeek || '';

    function parseDate(value) {
        const parts = String(value || '').split('-').map(Number);
        if (parts.length !== 3 || parts.some(Number.isNaN)) return null;
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }

    function startOfWeek(date) {
        const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
        copy.setDate(copy.getDate() - copy.getDay());
        return copy;
    }

    function formatDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    function sameDate(a, b) {
        return a && b
            && a.getFullYear() === b.getFullYear()
            && a.getMonth() === b.getMonth()
            && a.getDate() === b.getDate();
    }

    const selectedWeek = startOfWeek(parseDate(selectedText) || new Date());
    let visibleMonth = new Date(selectedWeek.getFullYear(), selectedWeek.getMonth(), 1);

    function navigateToWeek(date) {
        const weekStart = startOfWeek(date);
        window.location.href = `/payments/weekly?week=${encodeURIComponent(formatDate(weekStart))}`;
    }

    function makeDayButton(date, currentMonth, row) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'calendar-day';
        button.dataset.date = formatDate(date);
        button.setAttribute('aria-label', `${date.getMonth() + 1}월 ${date.getDate()}일이 포함된 주 조회`);

        if (date.getMonth() !== currentMonth) button.classList.add('outside-month');
        if (date.getDay() === 0) button.classList.add('sunday');
        if (date.getDay() === 6) button.classList.add('saturday');
        if (sameDate(date, new Date())) button.classList.add('today');

        const number = document.createElement('span');
        number.className = 'day-number';
        number.textContent = String(date.getDate());
        button.appendChild(number);

        button.addEventListener('click', () => navigateToWeek(date));
        button.addEventListener('focus', () => row.classList.add('keyboard-focus'));
        button.addEventListener('blur', () => row.classList.remove('keyboard-focus'));
        return button;
    }

    function render() {
        const year = visibleMonth.getFullYear();
        const month = visibleMonth.getMonth();
        title.textContent = `${year}년 ${month + 1}월`;
        body.innerHTML = '';

        const firstOfMonth = new Date(year, month, 1);
        const firstVisible = startOfWeek(firstOfMonth);
        const lastOfMonth = new Date(year, month + 1, 0);
        const lastWeekStart = startOfWeek(lastOfMonth);
        const weekCount = Math.round((lastWeekStart - firstVisible) / 604800000) + 1;

        for (let weekIndex = 0; weekIndex < weekCount; weekIndex++) {
            const rowStart = new Date(firstVisible);
            rowStart.setDate(firstVisible.getDate() + weekIndex * 7);

            const row = document.createElement('div');
            row.className = 'calendar-week-row';
            row.dataset.weekStart = formatDate(rowStart);
            row.setAttribute('role', 'group');
            row.setAttribute('aria-label', `${rowStart.getMonth() + 1}월 ${rowStart.getDate()}일부터 7일간`);

            if (sameDate(rowStart, selectedWeek)) row.classList.add('selected');

            for (let dayIndex = 0; dayIndex < 7; dayIndex++) {
                const date = new Date(rowStart);
                date.setDate(rowStart.getDate() + dayIndex);
                row.appendChild(makeDayButton(date, month, row));
            }
            body.appendChild(row);
        }
    }

    previousButton.addEventListener('click', () => {
        visibleMonth = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() - 1, 1);
        render();
    });

    nextButton.addEventListener('click', () => {
        visibleMonth = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + 1, 1);
        render();
    });

    todayButton.addEventListener('click', () => navigateToWeek(new Date()));

    render();
})();

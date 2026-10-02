(function () {
    const form = document.querySelector('[data-daily-entry-form]');
    if (!form) return;
    const table = form.querySelector('table');
    const rows = Array.from(table.tBodies[0].rows);
    const header = table.tHead.rows[0];
    const headers = Array.from(header.cells).slice(1);
    const products = headers.map((cell, i) => ({key: rows[0].cells[i + 1].querySelector('input').name, label: cell.textContent.trim()}));
    const vendors = rows.map(row => ({key: row.dataset.entrySequence, label: row.querySelector('[name="vendorName"]').value}));
    const originalCells = new Map(rows.map(row => [row, Array.from(row.cells)]));
    const originalHeaders = Array.from(header.cells);
    const editor = document.querySelector('[data-layout-editor]');
    const status = editor.querySelector('[data-layout-status]');
    const editButton = document.querySelector('[data-layout-edit]');
    let saved = normalize(window.dailyEntryLayout || {});
    let draft = structuredClone(saved);
    let changed = false;
    let saving = false;
    function normalize(layout) {
        const result = {};
        [['vendor', vendors], ['product', products]].forEach(([type, items]) => {
            const keys = items.map(item => item.key);
            result[type + 'Order'] = [...new Set([...(layout[type + 'Order'] || []), ...keys])].filter(key => keys.includes(key));
            const hiddenKey = type === 'vendor' ? 'hiddenVendors' : 'hiddenProducts';
            result[hiddenKey] = (layout[hiddenKey] || []).filter(key => keys.includes(key));
        });
        return result;
    }
    function apply(layout) {
        const hiddenVendors = new Set(layout.hiddenVendors);
        layout.vendorOrder.forEach(key => {
            const row = rows.find(row => row.dataset.entrySequence === key);
            row.classList.toggle('entry-layout-hidden', hiddenVendors.has(key));
            table.tBodies[0].append(row);
        });
        const orderedIndexes = layout.productOrder.map(key => products.findIndex(product => product.key === key) + 1);
        [header, ...rows].forEach(row => {
            const cells = row === header ? originalHeaders : originalCells.get(row);
            orderedIndexes.forEach(index => {
                cells[index].classList.toggle('entry-layout-hidden', layout.hiddenProducts.includes(products[index - 1].key));
                row.append(cells[index]);
            });
        });
        // Equal-width quantity columns continue to match the visible column count.
        table.querySelector('.entry-qty-col').span = Math.max(1, products.length - layout.hiddenProducts.length);
    }
    function render() {
        [['vendor', vendors, 'hiddenVendors', '[data-layout-vendors]'], ['product', products, 'hiddenProducts', '[data-layout-products]']].forEach(([type, items, hiddenKey, selector]) => {
            const list = editor.querySelector(selector);
            const scrollTop = list.scrollTop;
            list.replaceChildren();
            const order = draft[type + 'Order'];
            order.forEach((key, index) => {
                const item = items.find(item => item.key === key);
                const line = document.createElement('div');
                line.className = 'entry-layout-item';
                const label = document.createElement('span');
                label.textContent = item.label;
                const hidden = draft[hiddenKey].includes(key);
                line.classList.toggle('entry-layout-deleted', hidden);
                line.append(label);
                function button(text, disabled, action) {
                    const control = document.createElement('button');
                    control.type = 'button';
                    control.textContent = text;
                    control.disabled = disabled || saving;
                    control.setAttribute('aria-label', `${item.label} ${text}`);
                    control.addEventListener('click', () => {
                        action(); changed = true; apply(draft); render();
                    });
                    line.append(control);
                }
                button(type === 'vendor' ? '위로' : '앞으로', index === 0, () => [order[index - 1], order[index]] = [order[index], order[index - 1]]);
                button(type === 'vendor' ? '아래로' : '뒤로', index === order.length - 1, () => [order[index + 1], order[index]] = [order[index], order[index + 1]]);
                button(hidden ? '복원' : '삭제', !hidden && draft[hiddenKey].length >= items.length - 1, () => {
                    draft[hiddenKey] = hidden ? draft[hiddenKey].filter(value => value !== key) : [...draft[hiddenKey], key];
                });
                list.append(line);
            });
            list.scrollTop = scrollTop;
        });
    }
    function close() {
        editor.hidden = true; editButton.disabled = false; changed = false;
    }
    editButton.addEventListener('click', () => {
        draft = structuredClone(saved); render(); status.textContent = ''; editor.hidden = false; editButton.disabled = true;
    });
    editor.querySelector('[data-layout-cancel]').addEventListener('click', () => { if (saving) return; apply(saved); close(); });
    editor.querySelector('[data-layout-reset]').addEventListener('click', () => {
        if (saving) return; draft = normalize({}); changed = true; apply(draft); render();
    });
    editor.querySelector('[data-layout-save]').addEventListener('click', async () => {
        if (saving) return;
        saving = true;
        editor.querySelectorAll('button').forEach(button => button.disabled = true);
        status.textContent = '저장 중...';
        try {
            const token = document.querySelector('meta[name="_csrf"]').content;
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
            const response = await fetch('/daily-entry/layout', {
                method: 'POST', headers: {'Content-Type': 'application/json', [csrfHeader]: token}, body: JSON.stringify(draft)
            });
            if (!response.ok || response.redirected) throw new Error('표 설정을 저장하지 못했습니다. 로그인 상태를 확인하고 다시 시도해주세요.');
            saved = structuredClone(draft); close();
        } catch (error) { status.textContent = error.message; }
        finally {
            saving = false;
            editor.querySelectorAll('button').forEach(button => button.disabled = false);
            render();
        }
    });
    // Existing order numbers depend on original sequence. Submit every original row,
    // including hidden values, in its original order so display edits cannot delete sales.
    form.addEventListener('submit', event => {
        if (!editor.hidden) {
            event.preventDefault(); event.stopImmediatePropagation();
            status.textContent = '표 설정을 저장하거나 취소한 뒤 납품 수량을 저장해주세요.';
            editor.scrollIntoView({block: 'nearest'}); return;
        }
        rows.forEach(row => table.tBodies[0].append(row));
    });
    window.addEventListener('beforeunload', event => {
        if (!changed && !saving) return;
        event.preventDefault(); event.returnValue = '';
    });
    apply(saved);
})();

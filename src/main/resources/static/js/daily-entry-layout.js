(function () {
    const form = document.querySelector('[data-daily-entry-form]');
    if (!form) return;

    const table = form.querySelector('.daily-entry-table');
    const tbody = table && table.tBodies[0];
    const header = table && table.tHead && table.tHead.rows[0];
    if (!table || !tbody || !header) return;

    const rows = Array.from(tbody.rows);
    if (rows.length === 0) return;

    const headers = Array.from(header.cells).slice(1);
    const products = headers.map((cell, index) => ({
        key: rows[0].cells[index + 1].querySelector('input').name,
        label: cell.textContent.trim()
    }));
    const vendors = rows.map(row => ({
        key: row.dataset.entrySequence,
        label: row.querySelector('[name="vendorName"]').value
    }));

    const originalCells = new Map(rows.map(row => [row, Array.from(row.cells)]));
    const originalHeaders = Array.from(header.cells);
    const editButton = document.querySelector('[data-layout-edit]');
    const actions = document.querySelector('[data-layout-actions]');
    const saveButton = document.querySelector('[data-layout-save]');
    const cancelButton = document.querySelector('[data-layout-cancel]');
    const resetButton = document.querySelector('[data-layout-reset]');
    const filterButton = document.querySelector('[data-entry-filter]');
    const status = document.querySelector('[data-layout-status]');

    if (!editButton || !actions || !saveButton || !cancelButton || !resetButton || !status) return;

    products.forEach((product, index) => {
        headers[index].dataset.layoutProductKey = product.key;
    });

    let saved = normalize(window.dailyEntryLayout || {});
    let draft = structuredClone(saved);
    let editing = false;
    let changed = false;
    let saving = false;
    let activeDrag = null;
    let pointerDrag = null;
    let dragPoint = null;
    let autoScrollFrame = null;

    function normalize(layout) {
        const result = {};
        [['vendor', vendors], ['product', products]].forEach(([type, items]) => {
            const keys = items.map(item => item.key);
            result[type + 'Order'] = [...new Set([...(layout[type + 'Order'] || []), ...keys])]
                    .filter(key => keys.includes(key));

            const hiddenKey = type === 'vendor' ? 'hiddenVendors' : 'hiddenProducts';
            result[hiddenKey] = (layout[hiddenKey] || []).filter(key => keys.includes(key));
        });
        return result;
    }

    function setStatus(message) {
        status.textContent = message || '';
    }

    function markChanged(message) {
        changed = true;
        if (message) setStatus(message);
    }

    function apply(layout) {
        const hiddenVendors = new Set(layout.hiddenVendors);
        layout.vendorOrder.forEach(key => {
            const row = rows.find(candidate => candidate.dataset.entrySequence === key);
            if (row) tbody.append(row);
        });

        rows.forEach(row => {
            const hidden = hiddenVendors.has(row.dataset.entrySequence);
            row.classList.toggle('entry-layout-hidden', hidden && !editing);
            row.classList.toggle('entry-layout-deleted-preview', hidden && editing);
        });

        const hiddenProducts = new Set(layout.hiddenProducts);
        const orderedIndexes = layout.productOrder
                .map(key => products.findIndex(product => product.key === key) + 1)
                .filter(index => index > 0);

        [header, ...rows].forEach(row => {
            const cells = row === header ? originalHeaders : originalCells.get(row);
            orderedIndexes.forEach(index => {
                const productKey = products[index - 1].key;
                const cell = cells[index];
                const hidden = hiddenProducts.has(productKey);
                cell.classList.toggle('entry-layout-hidden', hidden && !editing);
                cell.classList.toggle('entry-layout-deleted-preview', hidden && editing);
                row.append(cell);
            });
        });

        const quantityCol = table.querySelector('.entry-qty-col');
        if (quantityCol) {
            quantityCol.span = editing
                    ? products.length
                    : Math.max(1, products.length - layout.hiddenProducts.length);
        }
    }

    function restoreOriginalDomOrder() {
        rows.forEach(row => tbody.append(row));
        [header, ...rows].forEach(row => {
            const cells = row === header ? originalHeaders : originalCells.get(row);
            cells.forEach(cell => row.append(cell));
        });
    }

    function syncVendorOrderFromDom() {
        draft.vendorOrder = Array.from(tbody.rows)
                .map(row => row.dataset.entrySequence)
                .filter(Boolean);
    }

    function moveProduct(key, targetKey, after) {
        if (!editing || key === targetKey) return;

        const order = draft.productOrder.filter(value => value !== key);
        const targetIndex = order.indexOf(targetKey);
        if (targetIndex < 0) return;

        const insertIndex = targetIndex + (after ? 1 : 0);
        order.splice(insertIndex, 0, key);

        if (order.join('|') === draft.productOrder.join('|')) return;
        draft.productOrder = order;
        markChanged('품목 순서를 변경했습니다. 저장하면 다음에도 이 순서로 표시됩니다.');
        apply(draft);
        refreshControlStates();
    }

    function moveVendor(key, targetRow, after) {
        if (!editing) return;

        const source = rows.find(row => row.dataset.entrySequence === key);
        if (!source || !targetRow || source === targetRow) return;

        if (after) {
            targetRow.after(source);
        } else {
            targetRow.before(source);
        }

        const nextOrder = Array.from(tbody.rows).map(row => row.dataset.entrySequence);
        if (nextOrder.join('|') === draft.vendorOrder.join('|')) return;

        draft.vendorOrder = nextOrder;
        markChanged('거래처 순서를 변경했습니다. 저장하면 다음에도 이 순서로 표시됩니다.');
    }

    function toggleHidden(type, key) {
        if (!editing || saving) return;

        const hiddenKey = type === 'vendor' ? 'hiddenVendors' : 'hiddenProducts';
        const hidden = draft[hiddenKey].includes(key);
        const itemCount = type === 'vendor' ? vendors.length : products.length;

        if (!hidden && draft[hiddenKey].length >= itemCount - 1) {
            setStatus('표에는 거래처와 품목이 각각 하나 이상 남아 있어야 합니다.');
            return;
        }

        draft[hiddenKey] = hidden
                ? draft[hiddenKey].filter(value => value !== key)
                : [...draft[hiddenKey], key];

        markChanged(hidden ? '항목을 복원했습니다.' : '항목을 표에서 숨김 처리했습니다. 기존 납품 기록은 삭제되지 않습니다.');
        apply(draft);
        refreshControlStates();
    }

    function getScrollContext() {
        const wrap = table.closest('.daily-entry-table-wrap');
        const mobileLayout = window.matchMedia('(max-width: 720px)').matches;
        const canScrollWrap = wrap && !mobileLayout && wrap.scrollHeight > wrap.clientHeight + 4;

        if (canScrollWrap) {
            return {
                element: wrap,
                rect: wrap.getBoundingClientRect()
            };
        }

        return {
            element: null,
            rect: {
                top: 0,
                bottom: window.innerHeight
            }
        };
    }

    function autoScrollVelocity(clientY, rect) {
        const edge = 96;
        const minimum = 8;
        const maximum = 34;

        if (clientY < rect.top + edge) {
            const strength = Math.min(1, Math.max(0, (rect.top + edge - clientY) / edge));
            return -Math.round(minimum + (maximum - minimum) * strength);
        }

        if (clientY > rect.bottom - edge) {
            const strength = Math.min(1, Math.max(0, (clientY - (rect.bottom - edge)) / edge));
            return Math.round(minimum + (maximum - minimum) * strength);
        }

        return 0;
    }

    function moveVendorAtPoint(key, clientY) {
        if (!editing) return;

        const source = rows.find(row => row.dataset.entrySequence === key);
        if (!source) return;

        const candidates = Array.from(tbody.rows).filter(row => row !== source);
        if (candidates.length === 0) return;

        for (const row of candidates) {
            const rect = row.getBoundingClientRect();
            if (clientY < rect.top + rect.height / 2) {
                moveVendor(key, row, false);
                return;
            }
        }

        moveVendor(key, candidates[candidates.length - 1], true);
    }

    function activeVendorDragKey() {
        if (activeDrag && activeDrag.type === 'vendor') {
            return activeDrag.key;
        }
        if (pointerDrag && pointerDrag.active && pointerDrag.type === 'vendor') {
            return pointerDrag.key;
        }
        return null;
    }

    function stopAutoScroll() {
        dragPoint = null;
        if (autoScrollFrame !== null) {
            window.cancelAnimationFrame(autoScrollFrame);
            autoScrollFrame = null;
        }
    }

    function continueAutoScroll() {
        autoScrollFrame = null;

        const vendorKey = activeVendorDragKey();
        if (!dragPoint || !vendorKey || !editing || saving) return;

        const context = getScrollContext();
        const velocity = autoScrollVelocity(dragPoint.clientY, context.rect);
        if (velocity === 0) return;

        const before = context.element ? context.element.scrollTop : window.scrollY;

        if (context.element) {
            context.element.scrollTop += velocity;
        } else {
            window.scrollBy(0, velocity);
        }

        moveVendorAtPoint(vendorKey, dragPoint.clientY);

        const after = context.element ? context.element.scrollTop : window.scrollY;
        if (after !== before) {
            autoScrollFrame = window.requestAnimationFrame(continueAutoScroll);
        }
    }

    function trackAutoScroll(clientX, clientY) {
        dragPoint = {clientX, clientY};
        if (autoScrollFrame === null) {
            autoScrollFrame = window.requestAnimationFrame(continueAutoScroll);
        }
    }

    function dragVisual(type, key) {
        if (type === 'vendor') {
            return rows.find(row => row.dataset.entrySequence === key) || null;
        }
        return headers.find(cell => cell.dataset.layoutProductKey === key) || null;
    }

    function clearDragVisual() {
        table.querySelectorAll('.entry-layout-dragging')
                .forEach(element => element.classList.remove('entry-layout-dragging'));
    }

    function startNativeDrag(event, type, key) {
        if (!editing || saving) {
            event.preventDefault();
            return;
        }

        activeDrag = {type, key};
        const visual = dragVisual(type, key);
        if (visual) visual.classList.add('entry-layout-dragging');

        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', key);
        }
    }

    function endNativeDrag() {
        activeDrag = null;
        stopAutoScroll();
        clearDragVisual();
    }

    function attachDragHandle(handle, type, key) {
        handle.draggable = true;
        handle.dataset.layoutDragType = type;
        handle.dataset.layoutDragKey = key;

        handle.addEventListener('dragstart', event => startNativeDrag(event, type, key));
        handle.addEventListener('dragend', endNativeDrag);

        handle.addEventListener('pointerdown', event => {
            if (event.pointerType === 'mouse' || !editing || saving) return;

            pointerDrag = {
                pointerId: event.pointerId,
                type,
                key,
                startX: event.clientX,
                startY: event.clientY,
                active: false
            };
            handle.setPointerCapture(event.pointerId);
        });

        handle.addEventListener('pointermove', event => {
            if (!pointerDrag || pointerDrag.pointerId !== event.pointerId) return;

            const dx = event.clientX - pointerDrag.startX;
            const dy = event.clientY - pointerDrag.startY;
            if (!pointerDrag.active && Math.hypot(dx, dy) < 7) return;

            if (!pointerDrag.active) {
                pointerDrag.active = true;
                const visual = dragVisual(type, key);
                if (visual) visual.classList.add('entry-layout-dragging');
            }

            event.preventDefault();

            if (type === 'vendor') {
                trackAutoScroll(event.clientX, event.clientY);
                moveVendorAtPoint(key, event.clientY);
            } else {
                const target = document.elementFromPoint(event.clientX, event.clientY);
                if (!target) return;

                const targetHeader = target.closest('th[data-layout-product-key]');
                if (!targetHeader) return;
                const targetKey = targetHeader.dataset.layoutProductKey;
                const rect = targetHeader.getBoundingClientRect();
                moveProduct(key, targetKey, event.clientX > rect.left + rect.width / 2);
            }
        });

        const finishPointerDrag = event => {
            if (!pointerDrag || pointerDrag.pointerId !== event.pointerId) return;
            pointerDrag = null;
            stopAutoScroll();
            clearDragVisual();
        };
        handle.addEventListener('pointerup', finishPointerDrag);
        handle.addEventListener('pointercancel', finishPointerDrag);
    }

    function createControls() {
        rows.forEach(row => {
            const vendorKey = row.dataset.entrySequence;
            const vendor = vendors.find(item => item.key === vendorKey);
            const cell = originalCells.get(row)[0];

            const controls = document.createElement('span');
            controls.className = 'entry-layout-inline-controls';

            const grip = document.createElement('button');
            grip.type = 'button';
            grip.className = 'entry-layout-grip';
            grip.textContent = '↕';
            grip.setAttribute('aria-label', (vendor ? vendor.label : '거래처') + ' 순서 드래그');
            attachDragHandle(grip, 'vendor', vendorKey);

            const toggle = document.createElement('button');
            toggle.type = 'button';
            toggle.className = 'entry-layout-toggle';
            toggle.dataset.layoutDeleteVendor = vendorKey;
            toggle.addEventListener('click', () => toggleHidden('vendor', vendorKey));

            controls.append(grip, toggle);
            cell.append(controls);
        });

        products.forEach((product, index) => {
            const cell = originalHeaders[index + 1];

            const controls = document.createElement('span');
            controls.className = 'entry-layout-inline-controls';

            const grip = document.createElement('button');
            grip.type = 'button';
            grip.className = 'entry-layout-grip';
            grip.textContent = '↔';
            grip.setAttribute('aria-label', product.label + ' 순서 드래그');
            attachDragHandle(grip, 'product', product.key);

            const toggle = document.createElement('button');
            toggle.type = 'button';
            toggle.className = 'entry-layout-toggle';
            toggle.dataset.layoutDeleteProduct = product.key;
            toggle.addEventListener('click', () => toggleHidden('product', product.key));

            controls.append(grip, toggle);
            cell.append(controls);
        });
    }

    function refreshControlStates() {
        rows.forEach(row => {
            const key = row.dataset.entrySequence;
            const hidden = draft.hiddenVendors.includes(key);
            const button = row.querySelector('[data-layout-delete-vendor]');
            const grip = row.querySelector('.entry-layout-grip');
            if (button) {
                button.textContent = hidden ? '↺' : '×';
                button.title = hidden ? '복원' : '표에서 삭제';
                button.setAttribute('aria-label', hidden ? '거래처 복원' : '거래처를 표에서 삭제');
                button.disabled = saving;
            }
            if (grip) grip.disabled = saving;
        });

        products.forEach(product => {
            const cell = headers.find(candidate => candidate.dataset.layoutProductKey === product.key);
            if (!cell) return;

            const hidden = draft.hiddenProducts.includes(product.key);
            const button = cell.querySelector('[data-layout-delete-product]');
            const grip = cell.querySelector('.entry-layout-grip');
            if (button) {
                button.textContent = hidden ? '↺' : '×';
                button.title = hidden ? '복원' : '표에서 삭제';
                button.setAttribute('aria-label', hidden ? product.label + ' 복원' : product.label + '을 표에서 삭제');
                button.disabled = saving;
            }
            if (grip) grip.disabled = saving;
        });
    }

    function updateEditingUi() {
        table.classList.toggle('entry-layout-editing', editing);
        actions.hidden = !editing;
        editButton.hidden = editing;

        if (filterButton) {
            filterButton.disabled = editing || saving;
        }

        saveButton.disabled = saving;
        cancelButton.disabled = saving;
        resetButton.disabled = saving;
        refreshControlStates();
    }

    function beginEditing() {
        if (saving) return;

        if (filterButton && filterButton.classList.contains('daily-entry-filter-active')) {
            filterButton.click();
        }

        draft = structuredClone(saved);
        editing = true;
        changed = false;
        apply(draft);
        updateEditingUi();
        setStatus('거래처는 ↕를 잡고 위·아래로 드래그하세요. 화면 위·아래 끝으로 끌면 자동으로 스크롤됩니다. 품목은 ↔로 이동하고 ×는 표에서 숨깁니다.');
    }

    function cancelEditing() {
        if (saving) return;

        draft = structuredClone(saved);
        editing = false;
        changed = false;
        activeDrag = null;
        pointerDrag = null;
        stopAutoScroll();
        clearDragVisual();
        apply(saved);
        updateEditingUi();
        setStatus('');
    }

    editButton.addEventListener('click', beginEditing);

    cancelButton.addEventListener('click', cancelEditing);

    resetButton.addEventListener('click', () => {
        if (!editing || saving) return;
        draft = normalize({});
        markChanged('기본 표 순서로 되돌렸습니다. 저장하면 적용됩니다.');
        apply(draft);
        refreshControlStates();
    });

    saveButton.addEventListener('click', async () => {
        if (!editing || saving) return;

        saving = true;
        updateEditingUi();
        setStatus('표 설정 저장 중...');

        try {
            const token = document.querySelector('meta[name="_csrf"]').content;
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
            const response = await fetch('/daily-entry/layout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [csrfHeader]: token
                },
                body: JSON.stringify(draft)
            });

            if (!response.ok || response.redirected) {
                throw new Error('표 설정을 저장하지 못했습니다. 로그인 상태를 확인하고 다시 시도해주세요.');
            }

            saved = normalize(draft);
            draft = structuredClone(saved);
            changed = false;
            editing = false;
            apply(saved);
            setStatus('표 설정을 저장했습니다.');
        } catch (error) {
            setStatus(error.message);
        } finally {
            saving = false;
            updateEditingUi();
        }
    });

    document.addEventListener('dragover', event => {
        if (!activeDrag || activeDrag.type !== 'vendor' || !editing) return;

        event.preventDefault();
        trackAutoScroll(event.clientX, event.clientY);
        moveVendorAtPoint(activeDrag.key, event.clientY);
    });

    tbody.addEventListener('drop', event => {
        if (!activeDrag || activeDrag.type !== 'vendor') return;
        event.preventDefault();
        syncVendorOrderFromDom();
        endNativeDrag();
    });

    header.addEventListener('dragover', event => {
        if (!activeDrag || activeDrag.type !== 'product' || !editing) return;

        const targetHeader = event.target.closest('th[data-layout-product-key]');
        if (!targetHeader) return;

        event.preventDefault();
        const rect = targetHeader.getBoundingClientRect();
        moveProduct(
                activeDrag.key,
                targetHeader.dataset.layoutProductKey,
                event.clientX > rect.left + rect.width / 2
        );
    });

    header.addEventListener('drop', event => {
        if (!activeDrag || activeDrag.type !== 'product') return;
        event.preventDefault();
        endNativeDrag();
    });

    form.addEventListener('submit', event => {
        if (editing) {
            event.preventDefault();
            event.stopImmediatePropagation();
            setStatus('표 설정을 저장하거나 취소한 뒤 납품 수량을 저장해주세요.');
            table.scrollIntoView({block: 'nearest'});
            return;
        }

        restoreOriginalDomOrder();
    });

    window.addEventListener('beforeunload', event => {
        if (!changed && !saving) return;
        event.preventDefault();
        event.returnValue = '';
    });

    createControls();
    apply(saved);
    updateEditingUi();
})();

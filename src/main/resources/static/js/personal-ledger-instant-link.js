(function () {
    "use strict";

    function setup(form) {
        const select = form.querySelector('select[name="vendorId"]');
        const status = form.querySelector('[data-link-status]');
        const settlementMonth = form.querySelector('input[name="settlementMonth"]');
        if (!select || !form.dataset.quickUrl) return;

        let lastSavedVendor = select.value;
        let lastSavedMonth = settlementMonth ? settlementMonth.value : "";
        let busy = false;

        function show(message, failed) {
            if (!status) return;
            status.textContent = message;
            status.classList.toggle('ledger-save-failed', !!failed);
            status.setAttribute('role', failed ? 'alert' : 'status');
        }

        async function save() {
            if (busy) return;
            if (!select.value) {
                // The empty option is a placeholder, not an unlink action.
                select.value = lastSavedVendor;
                return;
            }

            const selectedVendor = select.value;
            const selectedMonth = settlementMonth ? settlementMonth.value : "";
            busy = true;
            select.disabled = true;
            show('저장 중…', false);

            try {
                // Capture form fields before disabling inputs (including the CSRF token).
                const data = new FormData(form);
                data.set('vendorId', selectedVendor);
                if (settlementMonth) data.set('settlementMonth', selectedMonth);

                const response = await fetch(form.dataset.quickUrl, {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Accept': 'application/json' },
                    body: data
                });
                if (!response.ok) throw new Error('HTTP ' + response.status);
                const result = await response.json();
                if (result.status !== 'saved') throw new Error('Unexpected response');

                lastSavedVendor = selectedVendor;
                lastSavedMonth = selectedMonth;

                const affectedForms = result.batch
                    ? Array.from(document.querySelectorAll('form[data-quick-url]'))
                        .filter(other => other.dataset.payer === result.counterparty)
                    : [form];
                // A repeated payer must update every visible row, not just
                // the row used to submit the selection.
                affectedForms.forEach(other => {
                    other.dispatchEvent(new CustomEvent('ledger:vendor-linked', {
                        detail: {
                            vendorId: String(result.vendorId),
                            matchStatus: result.matchStatuses
                                ? result.matchStatuses[other.dataset.bankId] : null,
                            linkedCount: result.linkedCount,
                            batch: result.batch
                        }
                    }));
                });

                const row = form.closest('tr');
                if (row) {
                    const matchingLabel = row.querySelector('[data-match-status]');
                    if (matchingLabel && result.matchStatus) matchingLabel.textContent = result.matchStatus;
                }
            } catch (error) {
                select.value = lastSavedVendor;
                if (settlementMonth) settlementMonth.value = lastSavedMonth;
                show('저장 실패 · 다시 선택해주세요', true);
            } finally {
                select.disabled = false;
                busy = false;
            }
        }

        form.addEventListener('ledger:vendor-linked', event => {
            const data = event.detail;
            select.value = data.vendorId;
            lastSavedVendor = data.vendorId;
            const row = form.closest('tr');
            if (row) {
                const categoryInput = row.querySelector('input[name="category"]');
                if (categoryInput) categoryInput.value = '사업 입금';
                const matchingLabel = row.querySelector('[data-match-status]');
                if (matchingLabel && data.matchStatus) matchingLabel.textContent = data.matchStatus;
            }
            show(data.batch
                ? '✓ 같은 이름 ' + data.linkedCount + '건 연결됨'
                : '✓ 연결 저장됨', false);
        });

        select.addEventListener('change', save);
        if (settlementMonth) settlementMonth.addEventListener('change', function () {
            if (select.value) save();
        });
    }

    document.querySelectorAll('form[data-quick-url]').forEach(setup);
})();

(function () {
    if ((window.location.pathname || '/') !== '/') return;

    async function loadAssociationCredit() {
        try {
            const response = await fetch('/association-credit/api/balance', {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) return;

            const data = await response.json();
            const cards = Array.from(document.querySelectorAll(
                '.sc-kpi-grid .sc-kpi-card, .final-dashboard-metrics .metric-card'
            ));

            cards.forEach(card => {
                const label = card.querySelector('.sc-kpi-label')
                    || Array.from(card.children).find(child => child.tagName === 'SPAN');
                if (!label || label.textContent.trim() !== '미수금') return;

                card.setAttribute('href', '/association-credit');
                card.classList.remove('sc-kpi-warning', 'warning-metric');
                card.classList.add('association-credit-kpi');
                card.dataset.balanceState = data.state;

                const amount = card.querySelector('strong');
                const note = card.querySelector('small');
                label.textContent = '두채협회 외상';

                if (amount) {
                    amount.textContent = data.displayAmount;
                    amount.style.color = data.state === 'debt'
                        ? '#dc2626'
                        : data.state === 'prepaid'
                            ? '#2563eb'
                            : '#0f172a';
                }

                if (note) note.textContent = data.note;
            });
        } catch (error) {
            // 외상 정보 조회 실패 시 기존 대시보드는 그대로 둡니다.
        }
    }

    loadAssociationCredit();
})();

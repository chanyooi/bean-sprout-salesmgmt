(function () {
    function simplifyTofuTable() {
        var table = document.querySelector('.special-tofu-table');

        if (!table) {
            return false;
        }

        var headRow = table.querySelector('thead tr');
        var bodyRow = table.querySelector('tbody tr');

        if (!headRow || !bodyRow) {
            return false;
        }

        var headCells = headRow.children;
        var bodyCells = bodyRow.children;

        /*
         * 기존 열:
         * 1 품목
         * 2 판매 수량
         * 3 판매매출
         * 4 팔공 매입원가
         * 5 판 반납
         * 6 총 반영 매출
         * 7 이익
         *
         * 화면에서는 4번만 제거.
         * 계산 로직은 SpecialItemAccountingService에 그대로 유지.
         */
        if (headCells.length === 7 && bodyCells.length === 7) {
            headRow.removeChild(headCells[3]);
            bodyRow.removeChild(bodyCells[3]);
        }

        return true;
    }

    if (!simplifyTofuTable()) {
        var tries = 0;
        var timer = setInterval(function () {
            tries++;

            if (simplifyTofuTable() || tries >= 30) {
                clearInterval(timer);
            }
        }, 100);
    }
})();

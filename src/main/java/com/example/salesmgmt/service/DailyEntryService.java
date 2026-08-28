package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.DeliveryRecord;
import com.example.salesmgmt.domain.OrderSnapshot;
import com.example.salesmgmt.domain.SaveResult;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyEntryService {

    private static final DateTimeFormatter ORDER_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * input_data.xlsx의 5~80행 거래처 순서를 그대로 유지한다.
     * 이 순서가 주문번호 뒤 3자리와 연결되므로 임의로 정렬하면 안 된다.
     */
    public static final List<String> VENDOR_ORDER = List.of(
            "산동빅",
            "옥계빅",
            "상모빅",
            "플래쉬",
            "고향가마솥",
            "명희네해장",
            "HS식자재도매유통",
            "지엠식자재마트(지산DC)",
            "더킹마트",
            "사곡구미식자재마트",
            "형곡우리마트",
            "비산DC마트",
            "비산킹마트",
            "광천킹마트",
            "팜식자재마트",
            "구포ss마트",
            "송정ss마트",
            "VIP마트",
            "내고향식자재마트",
            "문성팜식자재마트",
            "참마트",
            "왕산마트",
            "형곡드림마트",
            "문성드림마트",
            "임은DC마트",
            "형곡DC마트",
            "세운축산윤선영구미한우",
            "북삼우리푸드",
            "아름식품",
            "고놈의불향쭈꾸미",
            "올레길",
            "다온약선톳밥",
            "남통대구왕뽈찜",
            "바른마켓",
            "깜상알밥국시",
            "상모오호오쭈꾸미",
            "원골",
            "불구이돈",
            "형곡종가숯불갈비",
            "상모뒷고기",
            "상모용두동쭈꾸미",
            "형곡용두동쭈꾸미",
            "이가네족발",
            "뜨끈이",
            "황태덕장",
            "원조가오리",
            "신촌",
            "원웰빙마트",
            "아포상회",
            "양평",
            "팔도콩나물국밥",
            "형곡뒷고기",
            "원호노점",
            "다혜원",
            "아포아구찜",
            "아포dc마트",
            "아포킹마트",
            "아포팜",
            "지성이해물찜",
            "김천대구왕뽈찜",
            "신음대구왕뽈찜",
            "동해수산횟집",
            "김천DC마트",
            "김천와촌",
            "뻘떡낙지",
            "양지",
            "수영",
            "오거리",
            "합동식품",
            "팔공식품",
            "하나로",
            "축협하나로",
            "아포농협",
            "도레미",
            "로타리",
            "선산식자재마트"
    );

    private final SalesItemRepository salesItemRepository;
    private final SalesPersistenceService salesPersistenceService;
    private final VendorRuleService vendorRuleService;

    public DailyEntryService(
            SalesItemRepository salesItemRepository,
            SalesPersistenceService salesPersistenceService,
            VendorRuleService vendorRuleService
    ) {
        this.salesItemRepository = salesItemRepository;
        this.salesPersistenceService = salesPersistenceService;
        this.vendorRuleService = vendorRuleService;
    }

    @Transactional(readOnly = true)
    public DailyEntryPage load(LocalDate date) {
        Map<String, ExistingOrder> existingByOrder = new LinkedHashMap<>();

        for (SalesItemEntity item : salesItemRepository.findForDate(date)) {
            SalesOrderEntity order = item.getSalesOrder();
            ExistingOrder existing = existingByOrder.computeIfAbsent(
                    order.getOrderNumber(),
                    ignored -> new ExistingOrder(order)
            );
            existing.quantities.put(item.getItemName(), item.getQuantity());
        }

        List<DailyEntryRow> rows = new ArrayList<>(VENDOR_ORDER.size());
        int filledVendorCount = 0;

        for (int index = 0; index < VENDOR_ORDER.size(); index++) {
            int sequence = index + 1;
            String vendorName = VENDOR_ORDER.get(index);
            String orderNumber = orderNumber(date, sequence);
            ExistingOrder existing = existingByOrder.get(orderNumber);

            if (existing != null) {
                filledVendorCount++;
            }

            rows.add(toRow(
                    sequence,
                    orderNumber,
                    date,
                    vendorName,
                    existing
            ));
        }

        return new DailyEntryPage(
                date,
                List.copyOf(rows),
                filledVendorCount
        );
    }

    public SaveResult save(LocalDate date, List<RowInput> rows) {
        if (rows == null || rows.size() != VENDOR_ORDER.size()) {
            throw new IllegalArgumentException(
                    "입력 행 수가 올바르지 않습니다. 화면을 새로고침한 뒤 다시 저장해주세요."
            );
        }

        List<DeliveryRecord> records = new ArrayList<>();
        List<OrderSnapshot> snapshots = new ArrayList<>(VENDOR_ORDER.size());
        String sourceSheet = date.format(ORDER_DATE);

        for (int index = 0; index < VENDOR_ORDER.size(); index++) {
            int sequence = index + 1;
            String expectedVendor = VENDOR_ORDER.get(index);
            RowInput input = rows.get(index);

            if (!expectedVendor.equals(clean(input.vendorName()))) {
                throw new IllegalArgumentException(
                        "거래처 입력 순서가 변경되었습니다. 화면을 새로고침한 뒤 다시 저장해주세요."
                );
            }

            String orderNumber = orderNumber(date, sequence);
            int sourceRow = sequence + 4;
            String statementVendor =
                    vendorRuleService.statementVendorName(expectedVendor);

            // 화면에서는 회수통단가/전달방식/비고를 받지 않는다.
            // null/빈값을 넘기면 기존 주문 메타데이터는 SalesPersistenceService에서 보존되고,
            // 새 회수통은 거래처별 기본 단가를 사용한다.
            BigDecimal returnContainerUnitPrice = null;
            String deliveryMethod = "";
            String note = "";

            snapshots.add(new OrderSnapshot(
                    orderNumber,
                    date,
                    expectedVendor,
                    statementVendor,
                    returnContainerUnitPrice,
                    deliveryMethod,
                    note,
                    sourceSheet,
                    sourceRow
            ));

            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "두절kg", input.cutKg(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "일반콩나물", input.regular(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "소립", input.small(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "곱슬콩나물", input.curly(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "3.5kg일반", input.boxRegular(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "3.5kg곱슬", input.boxCurly(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "숙주", input.mungSprout(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "회수통", input.returnContainer(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "손두부", input.tofu(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
            addRecord(records, orderNumber, date, expectedVendor, statementVendor,
                    "두부판", input.tofuPlate(), returnContainerUnitPrice,
                    deliveryMethod, note, sourceSheet, sourceRow);
        }

        return salesPersistenceService.save(records, snapshots);
    }

    private void addRecord(
            List<DeliveryRecord> records,
            String orderNumber,
            LocalDate date,
            String vendor,
            String statementVendor,
            String item,
            String rawQuantity,
            BigDecimal returnContainerUnitPrice,
            String deliveryMethod,
            String note,
            String sourceSheet,
            int sourceRow
    ) {
        BigDecimal quantity = optionalQuantity(rawQuantity, vendor + " " + item);
        if (quantity == null || quantity.signum() == 0) {
            return;
        }

        records.add(new DeliveryRecord(
                orderNumber,
                date,
                vendor,
                statementVendor,
                item,
                quantity.stripTrailingZeros(),
                returnContainerUnitPrice,
                deliveryMethod,
                note,
                sourceSheet,
                sourceRow
        ));
    }

    private BigDecimal optionalQuantity(String raw, String label) {
        String value = clean(raw).replace(",", "");
        if (value.isBlank()) {
            return null;
        }

        try {
            BigDecimal number = new BigDecimal(value);
            if (number.signum() < 0) {
                throw new IllegalArgumentException(label + " 수량은 음수일 수 없습니다.");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " 수량을 숫자로 입력해주세요.");
        }
    }

    private DailyEntryRow toRow(
            int sequence,
            String orderNumber,
            LocalDate date,
            String vendorName,
            ExistingOrder existing
    ) {
        Map<String, BigDecimal> quantities = existing == null
                ? Map.of()
                : existing.quantities;

        return new DailyEntryRow(
                sequence,
                orderNumber,
                date,
                vendorName,
                quantities.get("두절kg"),
                quantities.get("일반콩나물"),
                quantities.get("소립"),
                quantities.get("곱슬콩나물"),
                quantities.get("3.5kg일반"),
                quantities.get("3.5kg곱슬"),
                quantities.get("숙주"),
                quantities.get("회수통"),
                quantities.get("손두부"),
                quantities.get("두부판"),
                existing != null
        );
    }

    private String orderNumber(LocalDate date, int sequence) {
        return "%s-%03d".formatted(date.format(ORDER_DATE), sequence);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ExistingOrder {
        private final SalesOrderEntity order;
        private final Map<String, BigDecimal> quantities = new LinkedHashMap<>();

        private ExistingOrder(SalesOrderEntity order) {
            this.order = order;
        }
    }

    public record DailyEntryPage(
            LocalDate date,
            List<DailyEntryRow> rows,
            int filledVendorCount
    ) {
    }

    public record DailyEntryRow(
            int sequence,
            String orderNumber,
            LocalDate date,
            String vendorName,
            BigDecimal cutKg,
            BigDecimal regular,
            BigDecimal small,
            BigDecimal curly,
            BigDecimal boxRegular,
            BigDecimal boxCurly,
            BigDecimal mungSprout,
            BigDecimal returnContainer,
            BigDecimal tofu,
            BigDecimal tofuPlate,
            boolean saved
    ) {
    }

    public record RowInput(
            String vendorName,
            String cutKg,
            String regular,
            String small,
            String curly,
            String boxRegular,
            String boxCurly,
            String mungSprout,
            String returnContainer,
            String tofu,
            String tofuPlate
    ) {
    }
}

package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;

@Service
public class StatementCacheFingerprintService {

    private final SalesItemRepository salesItemRepository;
    private final VendorRepository vendorRepository;

    public StatementCacheFingerprintService(
            SalesItemRepository salesItemRepository,
            VendorRepository vendorRepository
    ) {
        this.salesItemRepository = salesItemRepository;
        this.vendorRepository = vendorRepository;
    }

    /**
     * 명세서 결과에 영향을 주는 월별 판매 데이터와 거래처 명세서 설정을 해시한다.
     * 선산식자재마트의 전달 26일 시작 정산도 포함할 수 있도록 전월 26일부터 조회한다.
     */
    @Transactional(readOnly = true)
    public String fingerprint(YearMonth month) {
        MessageDigest digest = sha256();

        LocalDate start = month.minusMonths(1).atDay(26);
        LocalDate end = month.atEndOfMonth();

        for (SalesItemEntity item : salesItemRepository.findForMonthlyReport(start, end)) {
            update(digest, item.getId());
            update(digest, item.getSalesOrder().getDeliveryDate());
            update(digest, item.getSalesOrder().getOrderNumber());
            update(digest, item.getSalesOrder().getVendor().getId());
            update(digest, item.getItemName());
            update(digest, item.getQuantity());
            update(digest, item.getUnitPrice());
            update(digest, item.getLineAmount());
        }

        for (VendorEntity vendor : vendorRepository.findAllByOrderByInputNameAsc()) {
            update(digest, vendor.getId());
            update(digest, vendor.getInputName());
            update(digest, vendor.getStatementName());
            update(digest, vendor.isStatementTemplateAvailable());
            update(digest, vendor.getStatementDeliveryMethod());
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void update(MessageDigest digest, Object value) {
        String text = value == null ? "<null>" : value.toString();
        digest.update(text.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}

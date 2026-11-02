package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.MonthlyReceivableReport;
import com.example.salesmgmt.entity.PersonalBankTransactionEntity;
import com.example.salesmgmt.repository.PersonalBankTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only comparison of Songcheon monthly receivables and bank transactions.
 * Bank receipts are NOT business payment records. Unless a receipt has an
 * explicit settlement month, cash received in month M is attributed to usage
 * month M-1. Bank receipts never mutate business ledger payments.
 */
@Service
public class PersonalBankReceivableReconciliationService {
    private static final BigDecimal ZERO=BigDecimal.ZERO;

    private final PaymentService paymentService;
    private final PersonalBankTransactionRepository bankTransactions;

    public PersonalBankReceivableReconciliationService(PaymentService paymentService,
                                                       PersonalBankTransactionRepository bankTransactions) {
        this.paymentService=paymentService;
        this.bankTransactions=bankTransactions;
    }

    public record BankReceipt(Long id,LocalDate date,String account,String payer,
                              BigDecimal amount, String allocatedMonth,boolean assignedToSelected,
                              boolean inferredFromFollowingMonth,String comparisonStatus) {}

    public record VendorComparison(Long vendorId,String name,String cycle,
                                   BigDecimal billed,BigDecimal ledgerPaid,BigDecimal ledgerOutstanding,
                                   BigDecimal allocatedBank,BigDecimal candidateBank,BigDecimal bankShortfall,
                                   List<BankReceipt> receipts,String status) {}

    public record Comparison(YearMonth month,LocalDate from,LocalDate to,
                             BigDecimal billed,BigDecimal ledgerPaid,BigDecimal ledgerOutstanding,
                             BigDecimal allocatedBank,BigDecimal candidateBank,BigDecimal bankShortfall,
                             int missingVendors,List<VendorComparison> vendors) {}

    @Transactional(readOnly=true)
    public Comparison compare(YearMonth month,LocalDate from,LocalDate to) {
        if(month==null || from==null || to==null || from.isAfter(to) || from.plusYears(1).isBefore(to))
            throw new IllegalArgumentException("입금 조회 기간은 시작일부터 최대 1년까지 설정해주세요.");
        MonthlyReceivableReport report=paymentService.createMonthlyReport(month);
        var ledgerPayments=report.paymentRows();
        Map<Long,List<PersonalBankTransactionEntity>> bankByVendor=
                bankTransactions.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from,to).stream()
                  .filter(t->t.getIncoming().signum()>0 && t.getVendorId()!=null)
                  .filter(t->!t.isAutoTransfer())
                  .filter(t->isReceiptForUsageMonth(t,month))
                  .collect(Collectors.groupingBy(PersonalBankTransactionEntity::getVendorId));

        Map<Long,MonthlyReceivableReport.VendorRow> reportVendors=new LinkedHashMap<>();
        for(var row:report.vendorRows()){
            // Supplier payables, notably Palgong, are not bank receivables.
            if(row.billedAmount().signum()>0 || row.paidAmount().signum()>0)
                reportVendors.put(row.vendorId(),row);
        }

        // Bank receipts may belong to a vendor with no sales in the selected
        // month; keep them visible for reconciliation instead of hiding them.
        Set<Long> vendorIds=new LinkedHashSet<>(reportVendors.keySet());
        vendorIds.addAll(bankByVendor.keySet());

        List<VendorComparison> result=new ArrayList<>();
        BigDecimal billTotal=ZERO,paidTotal=ZERO,outstandingTotal=ZERO,allocatedTotal=ZERO,candidateTotal=ZERO,shortfallTotal=ZERO;
        int missing=0;
        for(Long vendorId:vendorIds){
            var vendorRow=reportVendors.get(vendorId);
            BigDecimal billed=vendorRow==null?ZERO:vendorRow.billedAmount();
            BigDecimal ledgerPaid=vendorRow==null?ZERO:vendorRow.paidAmount();
            BigDecimal ledgerOutstanding=vendorRow==null?ZERO:vendorRow.outstandingAmount().max(ZERO);
            var bankRows=bankByVendor.getOrDefault(vendorId,List.of());
            List<BankReceipt> receipts=new ArrayList<>();
            BigDecimal allocated=ZERO,candidate=ZERO;
            for(var row:bankRows){
                // Explicit allocations take precedence. Without one, a
                // following-month deposit is automatically for this usage month.
                boolean inferred=row.getSettlementMonth()==null||row.getSettlementMonth().isBlank();
                boolean assigned=!inferred;
                allocated=allocated.add(row.getIncoming());

                boolean ledgerMatch=ledgerPayments.stream().anyMatch(payment->
                    vendorId.equals(payment.vendorId()) &&
                    row.getTransactionDate().equals(payment.paymentDate()) &&
                    row.getIncoming().compareTo(payment.amount())==0);
                receipts.add(new BankReceipt(row.getId(),row.getTransactionDate(),
                    maskedAccount(row.getAccount()),row.getCounterparty(),row.getIncoming(),
                    row.getSettlementMonth(),assigned,inferred,
                    ledgerMatch?"장부 일치 후보":"장부 확인 필요"));
            }
            String name=vendorRow!=null?vendorRow.vendorName():"거래처 #"+vendorId;
            String cycle=vendorRow!=null?("WEEKLY".equals(vendorRow.paymentCycle().name())?"주별":"월별"):"매출 기록 없음";
            BigDecimal bankShortfall=billed.subtract(allocated).max(ZERO);
            String status=billed.signum()==0
                ? (allocated.signum()>0?"청구액 없이 은행 입금":"청구 내역 없음")
                : allocated.signum()==0?"은행 미입금"
                : bankShortfall.signum()>0?"은행 일부입금"
                : allocated.compareTo(billed)>0?"은행 초과입금 확인":"은행 입금 확인";
            if(bankShortfall.signum()>0)missing++;
            result.add(new VendorComparison(vendorId,name,cycle,billed,ledgerPaid,ledgerOutstanding,
                    allocated,candidate,bankShortfall,List.copyOf(receipts),status));
            billTotal=billTotal.add(billed);
            paidTotal=paidTotal.add(ledgerPaid);
            outstandingTotal=outstandingTotal.add(ledgerOutstanding);
            allocatedTotal=allocatedTotal.add(allocated);
            candidateTotal=candidateTotal.add(candidate);
            shortfallTotal=shortfallTotal.add(bankShortfall);
        }
        result.sort(Comparator.comparing(VendorComparison::bankShortfall).reversed()
                  .thenComparing(VendorComparison::name));
        return new Comparison(month,from,to,billTotal,paidTotal,outstandingTotal,
                              allocatedTotal,candidateTotal,shortfallTotal,missing,List.copyOf(result));
    }

    private static boolean isReceiptForUsageMonth(PersonalBankTransactionEntity row, YearMonth usageMonth) {
        String explicit=row.getSettlementMonth();
        if(explicit!=null&&!explicit.isBlank())return usageMonth.toString().equals(explicit);
        return YearMonth.from(row.getTransactionDate()).minusMonths(1).equals(usageMonth);
    }

    private static String maskedAccount(String account){
        if(account==null)return "미확인";
        return "•••• "+account.substring(Math.max(0,account.length()-4));
    }
}

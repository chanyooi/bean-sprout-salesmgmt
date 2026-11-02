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
 * Bank receipts are NOT business payment records. Explicit monthly attribution
 * is required to call a bank deposit confirmed for a settlement month.
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
                              String comparisonStatus) {}

    public record VendorComparison(Long vendorId,String name,String cycle,
                                   BigDecimal billed,BigDecimal ledgerPaid,BigDecimal ledgerOutstanding,
                                   BigDecimal allocatedBank,BigDecimal candidateBank,
                                   List<BankReceipt> receipts,String status) {}

    public record Comparison(YearMonth month,LocalDate from,LocalDate to,
                             BigDecimal billed,BigDecimal ledgerPaid,BigDecimal ledgerOutstanding,
                             BigDecimal allocatedBank,BigDecimal candidateBank,
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
                  .filter(t->t.getSettlementMonth()==null
                      || t.getSettlementMonth().isBlank()
                      || month.toString().equals(t.getSettlementMonth()))
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
        BigDecimal billTotal=ZERO,paidTotal=ZERO,outstandingTotal=ZERO,allocatedTotal=ZERO,candidateTotal=ZERO;
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
                boolean assigned=month.toString().equals(row.getSettlementMonth());
                if(assigned)allocated=allocated.add(row.getIncoming());
                else candidate=candidate.add(row.getIncoming());

                boolean ledgerMatch=ledgerPayments.stream().anyMatch(payment->
                    vendorId.equals(payment.vendorId()) &&
                    row.getTransactionDate().equals(payment.paymentDate()) &&
                    row.getIncoming().compareTo(payment.amount())==0);
                receipts.add(new BankReceipt(row.getId(),row.getTransactionDate(),
                    maskedAccount(row.getAccount()),row.getCounterparty(),row.getIncoming(),
                    row.getSettlementMonth(),assigned,
                    ledgerMatch?"장부 일치 후보":"장부 확인 필요"));
            }
            String name=vendorRow!=null?vendorRow.vendorName():"거래처 #"+vendorId;
            String cycle=vendorRow!=null?("WEEKLY".equals(vendorRow.paymentCycle().name())?"주별":"월별"):"매출 기록 없음";
            String status=ledgerOutstanding.signum()>0
                ? (allocated.signum()>0 || candidate.signum()>0?"입금 대조 필요":"장부 미수금")
                : (billed.signum()==0?"청구 내역 없음":"장부상 정산완료");
            if(ledgerOutstanding.signum()>0)missing++;
            result.add(new VendorComparison(vendorId,name,cycle,billed,ledgerPaid,ledgerOutstanding,
                    allocated,candidate,List.copyOf(receipts),status));
            billTotal=billTotal.add(billed);
            paidTotal=paidTotal.add(ledgerPaid);
            outstandingTotal=outstandingTotal.add(ledgerOutstanding);
            allocatedTotal=allocatedTotal.add(allocated);
            candidateTotal=candidateTotal.add(candidate);
        }
        result.sort(Comparator.comparing(VendorComparison::ledgerOutstanding).reversed()
                  .thenComparing(VendorComparison::name));
        return new Comparison(month,from,to,billTotal,paidTotal,outstandingTotal,
                              allocatedTotal,candidateTotal,missing,List.copyOf(result));
    }

    private static String maskedAccount(String account){
        if(account==null)return "미확인";
        return "•••• "+account.substring(Math.max(0,account.length()-4));
    }
}

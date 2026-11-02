package com.example.salesmgmt.controller;

import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import com.example.salesmgmt.service.PersonalBankReceivableReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalBankReceivableReconciliationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired VendorRepository vendors;
    @Autowired SalesOrderRepository orders;
    @Autowired SalesItemRepository items;
    @Autowired PaymentRepository payments;
    @Autowired PersonalBankTransactionRepository bank;
    @Autowired PersonalBankReceivableReconciliationService service;

    private VendorEntity sale(String name, String bill) {
        var vendor=vendors.save(new VendorEntity(name,name,true));
        var order=orders.save(new SalesOrderEntity("r-2026-"+vendor.getId(),
            LocalDate.of(2026,9,12),vendor,null,null,null,"test",1));
        items.save(new SalesItemEntity(order,"일반콩나물",BigDecimal.ONE,new BigDecimal(bill)));
        return vendor;
    }

    private PersonalBankTransactionEntity receipt(String id,Long vendorId,String date,
                                                   String amount,String account,String month){
        var row=new PersonalBankTransactionEntity(id,account,LocalDate.parse(date),
                new BigDecimal(amount),BigDecimal.ZERO,"김철수","농협 입금");
        row.link(vendorId,month);
        row.classify("사업 입금");
        return bank.save(row);
    }

    @Test void septemberBillingIsComparedWithOctoberBankDepositsWithoutDoubleCountingLedger() throws Exception {
        var vendor=sale("행복마트","200000");
        payments.save(new PaymentEntity(vendor,"2026-09",LocalDate.of(2026,10,2),
            new BigDecimal("70000"),"previous manual payment"));
        receipt("recon-sep-allocated",vendor.getId(),"2026-10-02","70000","351-0000-1111-22","2026-09");
        receipt("recon-sep-candidate",vendor.getId(),"2026-10-04","30000","302-0000-2222-33",null);
        receipt("recon-nonsep",vendor.getId(),"2026-10-06","10000","302-0000-2222-33","2026-08");
        // Another bank receipt without a known business payer is not counted.
        bank.save(new PersonalBankTransactionEntity("recon-unlinked","351-0000-1111-22",
            LocalDate.of(2026,10,4),new BigDecimal("20000"),BigDecimal.ZERO,"미등록 입금",""));

        var report=service.compare(YearMonth.of(2026,9),LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));
        var row=report.vendors().stream().filter(v->vendor.getId().equals(v.vendorId())).findFirst().orElseThrow();
        assertThat(row.billed()).isEqualByComparingTo("200000");
        assertThat(row.ledgerPaid()).isEqualByComparingTo("70000");
        assertThat(row.ledgerOutstanding()).isEqualByComparingTo("130000");
        assertThat(row.allocatedBank()).isEqualByComparingTo("100000");
        assertThat(row.candidateBank()).isEqualByComparingTo("0");
        assertThat(row.bankShortfall()).isEqualByComparingTo("100000");
        assertThat(row.receipts()).hasSize(2);
        assertThat(report.ledgerOutstanding()).isEqualByComparingTo("130000");
        assertThat(report.bankShortfall()).isEqualByComparingTo("100000");
        assertThat(payments.count()).isEqualTo(1);

        mvc.perform(get("/personal-ledger/reconciliation").param("month","2026-09")
            .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(view().name("personal-bank-reconciliation"))
            .andExpect(model().attributeExists("report"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("행복마트")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("정산월 확정")));
    }

    @Test void assigningBankReceiptToSeptemberUpdatesOnlyBankAllocationAndCanBeUndone() throws Exception {
        var vendor=sale("한가람식당","100000");
        var tx=receipt("assign-sep-candidate",vendor.getId(),"2026-10-04","40000",
                "302-0000-2222-33",null);
        long ledgerCount=payments.count();

        mvc.perform(post("/personal-ledger/reconciliation/"+tx.getId()+"/assign")
            .with(user("admin").roles("ADMIN")).with(csrf())
            .param("month","2026-09").param("from","2026-09-01").param("to","2026-10-31")
            .param("assign","true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/personal-ledger/reconciliation?month=2026-09&from=2026-09-01&to=2026-10-31"));
        assertThat(bank.findById(tx.getId()).orElseThrow().getSettlementMonth()).isEqualTo("2026-09");
        assertThat(payments.count()).isEqualTo(ledgerCount);
        var matched=service.compare(YearMonth.of(2026,9),LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));
        assertThat(matched.vendors().stream().filter(v->v.vendorId().equals(vendor.getId()))
            .findFirst().orElseThrow().allocatedBank()).isEqualByComparingTo("40000");
        assertThat(matched.ledgerOutstanding()).isEqualByComparingTo("100000");

        mvc.perform(post("/personal-ledger/reconciliation/"+tx.getId()+"/assign")
            .with(user("admin").roles("ADMIN")).with(csrf())
            .param("month","2026-09").param("assign","false"))
            .andExpect(status().is3xxRedirection());
        assertThat(bank.findById(tx.getId()).orElseThrow().getSettlementMonth()).isNull();
        assertThat(payments.count()).isEqualTo(ledgerCount);
    }

    @Test void cannotReallocateAnotherMonthOrExpenseAndNonAdminCannotAccess() throws Exception {
        var vendor=sale("금빛마트","100000");
        var other=receipt("assign-other-month",vendor.getId(),"2026-10-04","40000",
            "302-0000-2222-33","2026-08");
        var out=bank.save(new PersonalBankTransactionEntity("assign-expense","302-0000-2222-33",
            LocalDate.of(2026,10,4),BigDecimal.ZERO,new BigDecimal("5000"),"가계비",""));

        mvc.perform(post("/personal-ledger/reconciliation/"+other.getId()+"/assign")
            .with(user("admin").roles("ADMIN")).with(csrf())
            .param("month","2026-09")).andExpect(status().isConflict());
        mvc.perform(post("/personal-ledger/reconciliation/"+out.getId()+"/assign")
            .with(user("admin").roles("ADMIN")).with(csrf())
            .param("month","2026-09")).andExpect(status().isBadRequest());
        mvc.perform(get("/personal-ledger/reconciliation").param("month","2026-09")
            .with(user("work").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/personal-ledger/reconciliation/"+other.getId()+"/assign")
            .with(user("work").roles("USER")).with(csrf()).param("month","2026-09"))
            .andExpect(status().isForbidden());
        assertThat(other.getSettlementMonth()).isEqualTo("2026-08");
    }

    @Test void octoberBankDepositsAutomaticallyApplyToSeptemberWithoutManualAssignment() throws Exception {
        var vendor=sale("10월입금은9월사용","300000");
        var october1=receipt("oct-auto-1",vendor.getId(),"2026-10-03","110000",
            "351-0000-1111-22",null);
        receipt("oct-auto-2",vendor.getId(),"2026-10-18","90000",
            "302-0000-2222-33",null);
        receipt("sept-unassigned-not-sept",vendor.getId(),"2026-09-30","50000",
            "302-0000-2222-33",null);
        receipt("nov-unassigned-not-sept",vendor.getId(),"2026-11-01","40000",
            "351-0000-1111-22",null);
        receipt("oct-explicit-august",vendor.getId(),"2026-10-19","70000",
            "351-0000-1111-22","2026-08");

        var actual=service.compare(YearMonth.of(2026,9),LocalDate.of(2026,9,1),
            LocalDate.of(2026,11,30));
        var row=actual.vendors().stream().filter(v->vendor.getId().equals(v.vendorId()))
            .findFirst().orElseThrow();
        assertThat(row.billed()).isEqualByComparingTo("300000");
        assertThat(row.allocatedBank()).isEqualByComparingTo("200000");
        assertThat(row.bankShortfall()).isEqualByComparingTo("100000");
        assertThat(row.receipts()).hasSize(2);
        assertThat(row.receipts()).allSatisfy(deposit->{
            assertThat(deposit.inferredFromFollowingMonth()).isTrue();
            assertThat(deposit.date().getMonthValue()).isEqualTo(10);
        });
        assertThat(october1.getSettlementMonth()).isNull();
        assertThat(payments.count()).isZero();

        // Search fields default to the following month, not Sep + Oct.
        mvc.perform(get("/personal-ledger/reconciliation").param("month","2026-09")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("from","2026-10-01"))
            .andExpect(model().attribute("to","2026-10-31"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("정산월 확정")));
    }

    @Test void yearBoundaryAndExplicitAllocationStayCorrect() throws Exception {
        var vendor=vendors.save(new VendorEntity("연말 정산 가맹점","연말 정산 가맹점",true));
        var order=orders.save(new SalesOrderEntity("year-boundary-"+vendor.getId(),
            LocalDate.of(2025,12,31),vendor,null,null,null,"test",1));
        items.save(new SalesItemEntity(order,"일반콩나물",BigDecimal.ONE,new BigDecimal("150000")));
        var auto=receipt("newyear-jan",vendor.getId(),"2026-01-07","100000",
            "351-0000-1111-22",null);
        var explicitlyReassigned=receipt("newyear-other",vendor.getId(),"2026-01-08","20000",
            "351-0000-1111-22","2026-02");

        var report=service.compare(YearMonth.of(2025,12),LocalDate.of(2026,1,1),
            LocalDate.of(2026,1,31));
        var row=report.vendors().stream().filter(v->vendor.getId().equals(v.vendorId()))
            .findFirst().orElseThrow();
        assertThat(row.allocatedBank()).isEqualByComparingTo("100000");
        assertThat(row.bankShortfall()).isEqualByComparingTo("50000");
        assertThat(auto.getSettlementMonth()).isNull();
        assertThat(explicitlyReassigned.getSettlementMonth()).isEqualTo("2026-02");
        mvc.perform(get("/personal-ledger/reconciliation").param("month","2025-12")
              .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("from","2026-01-01"))
            .andExpect(model().attribute("to","2026-01-31"));
    }

    @Test void excludesPalgongSupplierPayableFromReceivableComparison(){
        var palgong=vendors.save(new VendorEntity("팔공","팔공",true));
        var order=orders.save(new SalesOrderEntity("recon-palgong-order",
            LocalDate.of(2026,9,7),palgong,null,null,null,"test",1));
        items.save(new SalesItemEntity(order,"손두부",BigDecimal.ONE,new BigDecimal("100000")));
        var report=service.compare(YearMonth.of(2026,9),LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));
        assertThat(report.vendors()).noneSatisfy(v->assertThat(v.vendorId()).isEqualTo(palgong.getId()));
    }
}

package com.example.salesmgmt.controller;

import com.example.salesmgmt.entity.PersonalBankTransactionEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.PersonalBankTransactionRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalLedgerControllerTest {
    @Autowired MockMvc mvc;
    @Autowired PersonalBankTransactionRepository transactions;
    @Autowired VendorRepository vendors;
    @Autowired com.example.salesmgmt.repository.PaymentRepository payments;

    private String bankSelector(String account) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(account.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .substring(0,24);
    }

    private MockMultipartFile bankFile(String account) throws Exception {
        try(Workbook wb=new HSSFWorkbook();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            Sheet sheet=wb.createSheet("입출금거래내역");
            Row accountRow=sheet.createRow(6);
            accountRow.createCell(1).setCellValue("계좌번호");
            accountRow.createCell(3).setCellValue(account);
            Row hdr=sheet.createRow(11);
            String[] names={"","순번","거래일시","출금금액","입금금액","거래후잔액","거래내용","거래기록사항","거래점","거래메모"};
            for(int i=1;i<names.length;i++)hdr.createCell(i).setCellValue(names[i]);
            Row received=sheet.createRow(12);
            received.createCell(1).setCellValue("1");
            received.createCell(2).setCellValue("2026/10/28 17:19:54");
            received.createCell(4).setCellValue(850000);
            received.createCell(5).setCellValue(1850000);
            received.createCell(6).setCellValue("인터넷");
            received.createCell(7).setCellValue("김철수");
            received.createCell(9).setCellValue("송금");
            Row spent=sheet.createRow(13);
            spent.createCell(1).setCellValue("2");
            spent.createCell(2).setCellValue("2026/10/29 11:02:12");
            spent.createCell(3).setCellValue(60000);
            spent.createCell(5).setCellValue(1790000);
            spent.createCell(6).setCellValue("전자금융");
            spent.createCell(7).setCellValue("주유소");
            wb.write(bytes);
            return new MockMultipartFile("file","nh.xls","application/vnd.ms-excel",bytes.toByteArray());
        }
    }

    @Test void acceptsTwoAccountsAndIgnoresExactReimports() throws Exception {
        for(String account:List.of("351-0000-1111-22","302-0000-2222-33"))
            mvc.perform(multipart("/personal-ledger/upload").file(bankFile(account))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(transactions.count()).isEqualTo(4L);
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-1111-22"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(transactions.count()).isEqualTo(4L);
        var all=transactions.findAll();
        assertThat(all).anySatisfy(t -> {
            assertThat(t.getCounterparty()).isEqualTo("김철수");
            assertThat(t.getIncoming()).isEqualByComparingTo(new BigDecimal("850000"));
            assertThat(t.getDescription()).contains("인터넷","송금");
        });
    }

    @Test void savesVendorAliasButDoesNotWriteBusinessPayments() throws Exception {
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-3333-44"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        VendorEntity vendor=vendors.save(new VendorEntity("행복마트","행복마트",true));
        PersonalBankTransactionEntity income=transactions.findAll().stream().filter(t->t.getIncoming().signum()>0).findFirst().orElseThrow();
        mvc.perform(post("/personal-ledger/"+income.getId()+"/link")
            .with(user("admin").roles("ADMIN")).with(csrf())
            .param("month","2026-10").param("vendorId",vendor.getId().toString())
            .param("settlementMonth","2026-09").param("remember","true"))
            .andExpect(status().is3xxRedirection());
        assertThat(transactions.findById(income.getId()).orElseThrow().getVendorId()).isEqualTo(vendor.getId());
    }

    @Test void choosingVendorImmediatelyLinksAndRemembersPayerWithoutDuplicatingPayments() throws Exception {
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-4444-55"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        VendorEntity vendor=vendors.save(new VendorEntity("즉시연결마트","즉시연결마트",true));
        var income=transactions.findAll().stream()
                .filter(tx->tx.getIncoming().signum()>0).findFirst().orElseThrow();
        long paymentsBefore=payments.count();

        mvc.perform(post("/personal-ledger/"+income.getId()+"/link-instant")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .param("vendorId",vendor.getId().toString())
                .param("settlementMonth","2026-09").param("remember","true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("saved"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.category").value("사업 입금"));

        var saved=transactions.findById(income.getId()).orElseThrow();
        assertThat(saved.getVendorId()).isEqualTo(vendor.getId());
        assertThat(saved.getSettlementMonth()).isEqualTo("2026-09");
        assertThat(saved.getCategory()).isEqualTo("사업 입금");
        assertThat(payments.count()).isEqualTo(paymentsBefore);

        // A later bank file from the other account learns the same payer alias.
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("302-0000-4444-55"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(transactions.findAll().stream()
                .filter(tx->"302-0000-4444-55".equals(tx.getAccount()))
                .filter(tx->tx.getIncoming().signum()>0)
                .findFirst().orElseThrow().getVendorId()).isEqualTo(vendor.getId());
        assertThat(payments.count()).isEqualTo(paymentsBefore);
    }

    @Test void instantLinkRejectsExpenseAndUnauthorizedUsers() throws Exception {
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-6666-77"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var expense=transactions.findAll().stream()
                .filter(tx->tx.getOutgoing().signum()>0).findFirst().orElseThrow();
        VendorEntity vendor=vendors.save(new VendorEntity("거래처테스트","거래처테스트",true));

        mvc.perform(post("/personal-ledger/"+expense.getId()+"/link-instant")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .param("vendorId",vendor.getId().toString()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/personal-ledger/"+expense.getId()+"/link-instant")
                .with(user("work").roles("USER")).with(csrf())
                .param("vendorId",vendor.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test void ledgerShowsInstantLinkControlWithoutConnectButton() throws Exception {
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-8888-99"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/personal-ledger").param("month","2026-10")
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("data-quick-url")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("personal-ledger-instant-link.js")));
    }

    @Test void samePayerBulkLinksEveryPastIncomingButPreservesIndividualSettlementMonths() throws Exception {
        VendorEntity target=vendors.save(new VendorEntity("하루매일마트","하루매일마트",true));
        VendorEntity previous=vendors.save(new VendorEntity("기존연결마트","기존연결마트",true));

        var selected=transactions.save(new PersonalBankTransactionEntity(
            "bulk-same-name-selected","351-0000-1111-22",LocalDate.of(2026,9,10),
            new BigDecimal("120000"),BigDecimal.ZERO,"김철수","입금"));
        var older=transactions.save(new PersonalBankTransactionEntity(
            "bulk-same-name-older","302-0000-2222-33",LocalDate.of(2026,8,25),
            new BigDecimal("80000"),BigDecimal.ZERO,"김철수","다른 계좌 입금"));
        older.link(previous.getId(),"2026-07");
        older.classify("사업 입금");
        var outgoing=transactions.save(new PersonalBankTransactionEntity(
            "bulk-same-name-outgoing","351-0000-1111-22",LocalDate.of(2026,9,11),
            BigDecimal.ZERO,new BigDecimal("50000"),"김철수","출금"));
        outgoing.classify("차량");
        var unrelated=transactions.save(new PersonalBankTransactionEntity(
            "bulk-unrelated-payer","351-0000-1111-22",LocalDate.of(2026,9,12),
            new BigDecimal("70000"),BigDecimal.ZERO,"김철수상사","다른 사람"));
        var sameDate=transactions.save(new PersonalBankTransactionEntity(
            "bulk-same-name-same-date","351-0000-1111-22",LocalDate.of(2026,9,13),
            new BigDecimal("35000"),BigDecimal.ZERO,"김철수","매일 입금"));
        long beforePayments=payments.count();

        mvc.perform(post("/personal-ledger/"+selected.getId()+"/link-instant")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .param("vendorId",target.getId().toString())
                .param("settlementMonth","2026-09")
                .param("month","2026-09").param("remember","true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.linkedCount").value(3))
            .andExpect(jsonPath("$.batch").value(true))
            .andExpect(jsonPath("$.counterparty").value("김철수"))
            .andExpect(jsonPath("$.vendorId").value(target.getId()));

        assertThat(transactions.findById(selected.getId()).orElseThrow().getVendorId()).isEqualTo(target.getId());
        assertThat(transactions.findById(selected.getId()).orElseThrow().getSettlementMonth()).isEqualTo("2026-09");
        var historical=transactions.findById(older.getId()).orElseThrow();
        assertThat(historical.getVendorId()).isEqualTo(target.getId());
        assertThat(historical.getSettlementMonth()).isEqualTo("2026-07");
        assertThat(transactions.findById(sameDate.getId()).orElseThrow().getVendorId()).isEqualTo(target.getId());
        assertThat(transactions.findById(sameDate.getId()).orElseThrow().getCategory()).isEqualTo("사업 입금");
        assertThat(transactions.findById(outgoing.getId()).orElseThrow().getVendorId()).isNull();
        assertThat(transactions.findById(outgoing.getId()).orElseThrow().getCategory()).isEqualTo("차량");
        assertThat(transactions.findById(unrelated.getId()).orElseThrow().getVendorId()).isNull();
        assertThat(payments.count()).isEqualTo(beforePayments);

        // Existing batch mapping must also apply to new uploads.
        mvc.perform(multipart("/personal-ledger/upload")
                .file(bankFile("351-0000-8888-77"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection());
        var future=transactions.findAll().stream()
                .filter(tx->"351-0000-8888-77".equals(tx.getAccount()))
                .filter(tx->tx.getIncoming().signum()>0)
                .findFirst().orElseThrow();
        assertThat(future.getVendorId()).isEqualTo(target.getId());
        assertThat(payments.count()).isEqualTo(beforePayments);
    }

    @Test void disablingRememberLinksOnlyOneIncome() throws Exception {
        VendorEntity vendor=vendors.save(new VendorEntity("개별입금처","개별입금처",true));
        var one=transactions.save(new PersonalBankTransactionEntity(
            "single-only-first","351-0000-1111-22",LocalDate.of(2026,9,10),
            new BigDecimal("10000"),BigDecimal.ZERO,"박영희",""));
        var other=transactions.save(new PersonalBankTransactionEntity(
            "single-only-second","351-0000-1111-22",LocalDate.of(2026,9,11),
            new BigDecimal("20000"),BigDecimal.ZERO,"박영희",""));
        mvc.perform(post("/personal-ledger/"+one.getId()+"/link-instant")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .param("vendorId",vendor.getId().toString())
                .param("month","2026-09"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.linkedCount").value(1))
            .andExpect(jsonPath("$.batch").value(false));
        assertThat(transactions.findById(one.getId()).orElseThrow().getVendorId()).isEqualTo(vendor.getId());
        assertThat(transactions.findById(other.getId()).orElseThrow().getVendorId()).isNull();
    }

    @Test void categoryEditDoesNotAutomaticallyChangeFutureImports() throws Exception {
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-5555-66"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var expense=transactions.findAll().stream().filter(t->t.getOutgoing().signum()>0).findFirst().orElseThrow();
        mvc.perform(post("/personal-ledger/"+expense.getId()+"/classify")
            .with(user("admin").roles("ADMIN")).with(csrf())
            .param("month","2026-10").param("category","차량유지").param("remember","true"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("302-0000-7777-88"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(transactions.findAll().stream()
            .filter(t->t.getAccount().equals("302-0000-7777-88")&&t.getOutgoing().signum()>0)
            .findFirst().orElseThrow().getCategory()).isEqualTo("차량");
    }

    @Test void adminCanRenderLedgerDashboardWithImportedData() throws Exception {
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-8899-00"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/personal-ledger").param("month","2026-10")
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("personal-ledger"));
    }

    @Test void sidebarPermissionProbeIsAdminOnly() throws Exception {
        mvc.perform(get("/personal-ledger/access").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/personal-ledger/access").with(user("work").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedSecondAccountDoesNotPartiallyStoreFirstAccountAndAvoidsHttp500() throws Exception {
        long before = transactions.count();
        MockMultipartFile invalid = new MockMultipartFile(
                "file", "broken.xls", "application/vnd.ms-excel", "invalid".getBytes());
        mvc.perform(multipart("/personal-ledger/upload")
                .file(bankFile("351-0000-1111-22"))
                .file(invalid)
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personal-ledger"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertThat(transactions.count()).isEqualTo(before);
    }

    @Test void separateBankAccountsShowIndividualTotalsAndAccountFilteredTransactions() throws Exception {
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-1212-13"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("302-0000-3434-35"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/personal-ledger").param("month","2026-10")
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("accountCount",2))
                .andExpect(model().attribute("rows",org.hamcrest.Matchers.hasSize(4)))
                .andExpect(model().attribute("income",org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("1700000"))))
                .andExpect(model().attribute("expense",org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("120000"))));

        var selection=bankSelector("351-0000-1212-13");
        mvc.perform(get("/personal-ledger").param("month","2026-10").param("account",selection)
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("accountCount",2))
                .andExpect(model().attribute("rows",org.hamcrest.Matchers.hasSize(2)))
                .andExpect(model().attribute("selectedAccount",selection))
                .andExpect(model().attribute("income",org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("850000"))))
                .andExpect(model().attribute("expense",org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("60000"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("•••• 2-13")));
    }

    @Test void unclassifiedExpenseIsNotShownAsHouseholdSpending() throws Exception {
        var unclassified=transactions.save(new PersonalBankTransactionEntity(
            "unclassified-bank-spending","351-0000-1212-13",LocalDate.of(2026,9,16),
            BigDecimal.ZERO,new BigDecimal("9000000"),"불명 거래","미확인 출금"));
        // The entity defaults to an unclassified category.
        mvc.perform(get("/personal-ledger").param("month","2026-09")
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pendingExpense",org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("9000000"))))
                .andExpect(model().attribute("personalExpense",org.hamcrest.Matchers.comparesEqualTo(BigDecimal.ZERO)));
    }

    @Test void categoryEditKeepsSelectedBankViewAndDoesNotRequireRememberCheckbox() throws Exception {
        var tx=transactions.save(new PersonalBankTransactionEntity(
            "category-selected-account","351-0000-1212-13",LocalDate.of(2026,9,16),
            BigDecimal.ZERO,new BigDecimal("40000"),"생활비","출금"));
        var selection=bankSelector("351-0000-1212-13");
        mvc.perform(post("/personal-ledger/"+tx.getId()+"/classify")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .param("category","식비").param("month","2026-09").param("account",selection))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personal-ledger?month=2026-09&account="+selection));
        assertThat(transactions.findById(tx.getId()).orElseThrow().getCategory()).isEqualTo("식비");
    }

    @Test void removedSettlementMonthFieldDoesNotErasePreviousAllocation() throws Exception {
        var tx=transactions.save(new PersonalBankTransactionEntity(
            "linked-bank-month-persist","351-0000-1212-13",LocalDate.of(2026,9,16),
            new BigDecimal("100000"),BigDecimal.ZERO,"다른입금자","입금"));
        var a=vendors.save(new VendorEntity("기존정산처","기존정산처",true));
        var b=vendors.save(new VendorEntity("변경정산처","변경정산처",true));
        tx.link(a.getId(),"2026-08");
        mvc.perform(post("/personal-ledger/"+tx.getId()+"/link-instant")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .param("vendorId",b.getId().toString()).param("remember","false").param("month","2026-09"))
                .andExpect(status().isOk());
        var saved=transactions.findById(tx.getId()).orElseThrow();
        assertThat(saved.getVendorId()).isEqualTo(b.getId());
        assertThat(saved.getSettlementMonth()).isEqualTo("2026-08");
    }

    @Test void nonAdminCannotReadOrUploadPrivateBankData() throws Exception {
        mvc.perform(get("/personal-ledger").with(user("work").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-1111-22"))
            .with(user("work").roles("USER")).with(csrf())).andExpect(status().isForbidden());
    }
}

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

    @Test void userApprovedCategoryRuleAppliesToFutureImport() throws Exception {
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
            .findFirst().orElseThrow().getCategory()).isEqualTo("차량유지");
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

    @Test void nonAdminCannotReadOrUploadPrivateBankData() throws Exception {
        mvc.perform(get("/personal-ledger").with(user("work").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/personal-ledger/upload").file(bankFile("351-0000-1111-22"))
            .with(user("work").roles("USER")).with(csrf())).andExpect(status().isForbidden());
    }
}

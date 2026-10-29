package com.example.salesmgmt.controller;

import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import com.example.salesmgmt.service.PaymentService;
import com.example.salesmgmt.service.NonghyupXlsCompatibility;
import com.example.salesmgmt.service.PersonalTransferMatchingService;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/personal-ledger")
@PreAuthorize("hasRole('ADMIN')")
public class PersonalLedgerController {
    private final PersonalBankTransactionRepository transactions;
    private final PersonalBankAliasRepository aliases;
    private final PersonalBankCategoryRuleRepository categoryRules;
    private final VendorRepository vendors;
    private final PaymentService payments;
    private final PaymentRepository businessPayments;
    private final WeeklyPaymentRepository weeklyPayments;
    private final PersonalTransferMatchingService transferMatcher;
    public PersonalLedgerController(PersonalBankTransactionRepository transactions,PersonalBankAliasRepository aliases,PersonalBankCategoryRuleRepository categoryRules,VendorRepository vendors,PaymentService payments,PaymentRepository businessPayments,WeeklyPaymentRepository weeklyPayments,PersonalTransferMatchingService transferMatcher) {
        this.transactions=transactions;this.aliases=aliases;this.categoryRules=categoryRules;this.vendors=vendors;this.payments=payments;
        this.businessPayments=businessPayments;this.weeklyPayments=weeklyPayments;
        this.transferMatcher=transferMatcher;
    }
    @GetMapping("/access")
    public org.springframework.http.ResponseEntity<Void> access() {
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping
    @Transactional(readOnly=true)
    public String page(@RequestParam(required=false) String month,
                       @RequestParam(required=false) String account, Model model) {
        YearMonth ym=month==null||month.isBlank()?YearMonth.now():YearMonth.parse(month);
        var accountNumbers=transactions.findDistinctAccounts();
        String selectedAccount=account==null?"":account.trim();
        String accountNumber="";
        if(!selectedAccount.isBlank()) {
            accountNumber=accountNumbers.stream()
                .filter(number->accountKey(number).equals(selectedAccount))
                .findFirst()
                .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,"존재하지 않는 계좌입니다."));
        }

        var allMonthRows=transactions.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(ym.atDay(1),ym.atEndOfMonth());
        final String selectedNumber=accountNumber;
        var rows=selectedNumber.isBlank()?allMonthRows:allMonthRows.stream()
            .filter(tx->selectedNumber.equals(tx.getAccount())).toList();

        List<AccountSummary> accountSummaries=new ArrayList<>();
        for(String number:accountNumbers) {
            var list=allMonthRows.stream().filter(tx->number.equals(tx.getAccount())).toList();
            BigDecimal in=list.stream().map(PersonalBankTransactionEntity::getIncoming)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal out=list.stream().map(PersonalBankTransactionEntity::getOutgoing)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);
            accountSummaries.add(new AccountSummary(accountKey(number),maskedAccount(number),
                    in,out,in.subtract(out),list.size()));
        }

        BigDecimal income=BigDecimal.ZERO,expense=BigDecimal.ZERO;
        BigDecimal personalExpense=BigDecimal.ZERO,personalIncome=BigDecimal.ZERO;
        BigDecimal pendingExpense=BigDecimal.ZERO,pendingIncome=BigDecimal.ZERO;
        BigDecimal businessExpense=BigDecimal.ZERO,businessIncome=BigDecimal.ZERO;
        BigDecimal transferExpense=BigDecimal.ZERO,transferIncome=BigDecimal.ZERO;
        Map<String,BigDecimal> categories=new TreeMap<>();
        for(var tx:rows) {
            BigDecimal in=tx.getIncoming(),out=tx.getOutgoing();
            income=income.add(in);
            expense=expense.add(out);
            String category=tx.getCategory();
            if(isTransfer(category)) {
                transferIncome=transferIncome.add(in);
                transferExpense=transferExpense.add(out);
            } else if(isBusiness(category)) {
                businessIncome=businessIncome.add(in);
                businessExpense=businessExpense.add(out);
            } else if(isUnclassified(category)) {
                pendingIncome=pendingIncome.add(in);
                pendingExpense=pendingExpense.add(out);
            } else {
                personalIncome=personalIncome.add(in);
                personalExpense=personalExpense.add(out);
                if(out.signum()>0)categories.merge(category,out,BigDecimal::add);
            }
        }

        var monthlyBankPayments=businessPayments.findForPaymentDateRange(ym.atDay(1),ym.atEndOfMonth());
        var weeklyBankPayments=weeklyPayments.findForPaymentDateRange(ym.atDay(1),ym.atEndOfMonth());
        Map<Long,String> matches=new HashMap<>();
        for(var tx:rows) {
            if(tx.getIncoming().signum()==0)continue;
            if(tx.getVendorId()==null){matches.put(tx.getId(),"거래처 미연결");continue;}
            boolean monthlyMatch=monthlyBankPayments.stream().anyMatch(p->p.getVendor().getId().equals(tx.getVendorId())
                &&p.getPaymentDate().equals(tx.getTransactionDate())&&p.getAmount().compareTo(tx.getIncoming())==0);
            boolean weeklyMatch=weeklyBankPayments.stream().anyMatch(p->p.getVendor().getId().equals(tx.getVendorId())
                &&p.getPaymentDate().equals(tx.getTransactionDate())&&p.getAmount().compareTo(tx.getIncoming())==0);
            matches.put(tx.getId(),monthlyMatch||weeklyMatch?"송천 기록 일치 후보":"송천 입금 확인 필요");
        }

        List<ChartRow> categoryBars=new ArrayList<>();
        for(var entry:categories.entrySet())categoryBars.add(
            new ChartRow(entry.getKey(),entry.getValue(),percentage(entry.getValue(),personalExpense)));
        Map<YearMonth,BigDecimal> monthlyTotals=new LinkedHashMap<>();
        BigDecimal maxMonth=BigDecimal.ZERO;
        for(int offset=5;offset>=0;offset--){
            YearMonth current=ym.minusMonths(offset);
            var monthRows=current.equals(ym)?rows:transactions.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                    current.atDay(1),current.atEndOfMonth());
            BigDecimal total=monthRows.stream()
                .filter(tx->selectedNumber.isBlank()||selectedNumber.equals(tx.getAccount()))
                .filter(tx->!isUnclassified(tx.getCategory())&&!isBusiness(tx.getCategory())&&!isTransfer(tx.getCategory()))
                .map(PersonalBankTransactionEntity::getOutgoing).reduce(BigDecimal.ZERO,BigDecimal::add);
            monthlyTotals.put(current,total);
            if(total.compareTo(maxMonth)>0)maxMonth=total;
        }
        List<ChartRow> monthlyBars=new ArrayList<>();
        for(var entry:monthlyTotals.entrySet())
            monthlyBars.add(new ChartRow(entry.getKey().toString(),entry.getValue(),percentage(entry.getValue(),maxMonth)));

        model.addAttribute("selectedAccount",selectedAccount);
        model.addAttribute("accountSummaries",accountSummaries);
        model.addAttribute("accountCount",accountNumbers.size());
        model.addAttribute("scopeTitle",selectedAccount.isBlank()?"전체 계좌 합산":"농협 "+maskedAccount(selectedNumber));
        model.addAttribute("categoryBars",categoryBars);
        model.addAttribute("monthlyBars",monthlyBars);
        model.addAttribute("month",ym.toString());
        model.addAttribute("previousMonth",ym.minusMonths(1).toString());
        model.addAttribute("nextMonth",ym.plusMonths(1).toString());
        model.addAttribute("rows",rows);
        model.addAttribute("income",income);
        model.addAttribute("expense",expense);
        model.addAttribute("net",income.subtract(expense));
        model.addAttribute("personalExpense",personalExpense);
        model.addAttribute("personalIncome",personalIncome);
        model.addAttribute("pendingExpense",pendingExpense);
        model.addAttribute("pendingIncome",pendingIncome);
        model.addAttribute("businessExpense",businessExpense);
        model.addAttribute("businessIncome",businessIncome);
        model.addAttribute("transferExpense",transferExpense);
        model.addAttribute("transferIncome",transferIncome);
        model.addAttribute("autoTransferRows",rows.stream().filter(PersonalBankTransactionEntity::isAutoTransfer).count());
        model.addAttribute("matches",matches);
        model.addAttribute("vendors",vendors.findAllByOrderByInputNameAsc());
        return "personal-ledger";
    }

    private static boolean isUnclassified(String category) {
        return category==null||category.isBlank()||category.startsWith("미분류");
    }
    private static boolean isTransfer(String category) {
        return "계좌간 이체".equals(category)||"계좌 간 이체".equals(category);
    }
    private static boolean isBusiness(String category) {
        return "사업 입금".equals(category)||"사업 지출".equals(category);
    }
    private static String maskedAccount(String number) {
        return "•••• "+number.substring(Math.max(0,number.length()-4));
    }
    private static String accountKey(String number) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(number.getBytes(StandardCharsets.UTF_8))).substring(0,24);
        } catch(java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable",ex);
        }
    }
    public record AccountSummary(String key,String label,BigDecimal income,
                                 BigDecimal expense,BigDecimal net,int count) {}

    @PostMapping("/upload")
    @Transactional
    public String upload(@RequestParam("file") List<MultipartFile> files,RedirectAttributes flash){
        int inserted=0,skipped=0;
        try{
            for(MultipartFile file:files){
                if(file.isEmpty())continue;
                try(Workbook wb=NonghyupXlsCompatibility.openWorkbook(file.getBytes())){
                    DataFormatter fmt=new DataFormatter(Locale.KOREA);
                    for(Sheet sheet:wb){
                        int header=-1; Map<String,Integer> cols=null;String account="";
                        for(int i=0;i<Math.min(sheet.getLastRowNum()+1,35);i++){
                            Row row=sheet.getRow(i);if(row==null)continue;
                            String line=readLine(row,fmt);
                            if(line.contains("계좌번호")&&account.isBlank())account=extractAccount(line);
                            Map<String,Integer> attempt=headerColumns(row,fmt);
                            if(attempt.containsKey("date")&&attempt.containsKey("in")&&attempt.containsKey("out")&&attempt.containsKey("who")) {header=i;cols=attempt;break;}
                        }
                        if(cols==null) throw new IllegalArgumentException("지원하지 않는 은행 거래내역 양식입니다. 거래일시·입금금액·출금금액·거래기록사항 열이 필요합니다.");
                        if(account.isBlank()) throw new IllegalArgumentException("엑셀에서 계좌번호를 읽지 못했습니다. 다른 계좌와 혼합되지 않도록 가져오기를 취소합니다.");
                        Map<String,Integer> repetitions=new HashMap<>();
                        for(int i=header+1;i<=sheet.getLastRowNum();i++){
                            Row row=sheet.getRow(i);if(row==null)continue;
                            String d=value(row,cols.get("date"),fmt);
                            LocalDate date=parseDate(d,row.getCell(cols.get("date")));
                            if(date==null)continue;
                            BigDecimal incoming=money(value(row,cols.get("in"),fmt));
                            BigDecimal outgoing=money(value(row,cols.get("out"),fmt));
                            if(incoming.signum()==0&&outgoing.signum()==0)continue;
                            String who=value(row,cols.get("who"),fmt);
                            String memo=value(row,cols.get("channel"),fmt)+" "+value(row,cols.get("memo"),fmt);
                            String balance=value(row,cols.get("balance"),fmt);
                            String key=account+"|"+d+"|"+incoming+"|"+outgoing+"|"+balance+"|"+who+"|"+memo;
                            int occurrence=repetitions.merge(key,1,Integer::sum);
                            String hash=sha256(key+"|"+occurrence);
                            if(transactions.existsByFingerprint(hash)){skipped++;continue;}
                            var entry=new PersonalBankTransactionEntity(hash,account,date,incoming,outgoing,who,memo);
                            if(incoming.signum()>0){
                                var alias=aliases.findByAlias(who);
                                alias.ifPresent(a->entry.link(a.getVendorId(),null));
                                entry.classify(alias.isPresent()?"사업 입금":categoryRules.findByCounterparty(who).map(PersonalBankCategoryRuleEntity::getCategory).orElseGet(()->categoryFor(who,memo,true)));
                            } else entry.classify(categoryRules.findByCounterparty(who).map(PersonalBankCategoryRuleEntity::getCategory).orElseGet(()->categoryFor(who,memo,false)));
                            transactions.save(entry);inserted++;
                        }
                    }
                }
            }
            transactions.flush();
            var summary=transferMatcher.reconcile();
            flash.addFlashAttribute("message","가져온 거래 "+inserted+"건, 중복 제외 "+skipped+
                    "건 · 계좌 간 이체 신규 "+summary.newPairs()+"쌍 자동 인식");
        }catch(Exception ex){
            // A multipart upload can contain multiple accounts. Do not commit
            // the first file if parsing a later one fails.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            flash.addFlashAttribute("errorMessage",
                    "엑셀을 가져오지 못했습니다. 파일 형식을 확인해주세요. 변경 사항은 저장되지 않았습니다. 문제가 반복되면 농협 입출금거래내역 원본(.xls)을 다시 첨부해주세요.");
            return "redirect:/personal-ledger";
        }
        return "redirect:/personal-ledger";
    }
    @PostMapping("/{id}/classify")
    @Transactional
    public String classify(@PathVariable Long id,@RequestParam String category,
                           @RequestParam String month,@RequestParam(required=false) String account){
        var row=transactions.findById(id).orElseThrow();
        String sanitized=category.trim();
        if(sanitized.isBlank()) throw new IllegalArgumentException("분류를 입력해주세요.");
        if(sanitized.length()>80)sanitized=sanitized.substring(0,80);
        transferMatcher.releaseForManualChoice(row);
        row.classify(sanitized);
        // Category changes are per transaction. No hidden "remember" action.
        return "redirect:/personal-ledger?month="+month+
                (account==null||account.isBlank()?"":"&account="+account);
    }
    @PostMapping("/{id}/link")
    @Transactional
    public String link(@PathVariable Long id,
                       @RequestParam Long vendorId,
                       @RequestParam(required=false) String settlementMonth,
                       @RequestParam(defaultValue="false") boolean remember,
                       @RequestParam String month,
                       @RequestParam(required=false) String account) {
        saveVendorLink(id, vendorId, settlementMonth, remember);
        return "redirect:/personal-ledger?month="+month+
                (account==null||account.isBlank()?"":"&account="+account);
    }

    /**
     * The same bank payer can deposit daily and through multiple accounts.
     * A remembered mapping is applied to all existing INCOMING transactions
     * with the same exact payer name, and saved for future imports.
     * This never creates Songcheon business payment entries.
     */
    @PostMapping("/{id}/link-instant")
    @ResponseBody
    @Transactional
    public Map<String,Object> linkInstant(@PathVariable Long id,
                                           @RequestParam Long vendorId,
                                           @RequestParam(required=false) String settlementMonth,
                                           @RequestParam(required=false) String month,
                                           @RequestParam(defaultValue="false") boolean remember) {
        LinkOutcome outcome=saveVendorLink(id,vendorId,settlementMonth,remember);
        var row=outcome.row();
        var samePayer=outcome.affectedRows();

        YearMonth displayMonth;
        try {
            displayMonth=month==null||month.isBlank()
                    ?YearMonth.from(row.getTransactionDate()) : YearMonth.parse(month);
        } catch(DateTimeParseException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "조회 월 형식이 올바르지 않습니다.");
        }
        var monthlyLedger=businessPayments.findForPaymentDateRange(
                displayMonth.atDay(1),displayMonth.atEndOfMonth());
        var weeklyLedger=weeklyPayments.findForPaymentDateRange(
                displayMonth.atDay(1),displayMonth.atEndOfMonth());

        Map<String,String> matchStatuses=new LinkedHashMap<>();
        for (var entry : samePayer) {
            if (!YearMonth.from(entry.getTransactionDate()).equals(displayMonth)) continue;
            boolean monthlyMatch=monthlyLedger.stream().anyMatch(p ->
                    p.getVendor().getId().equals(vendorId)
                    && p.getPaymentDate().equals(entry.getTransactionDate())
                    && p.getAmount().compareTo(entry.getIncoming())==0);
            boolean weeklyMatch=weeklyLedger.stream().anyMatch(p ->
                    p.getVendor().getId().equals(vendorId)
                    && p.getPaymentDate().equals(entry.getTransactionDate())
                    && p.getAmount().compareTo(entry.getIncoming())==0);
            matchStatuses.put(entry.getId().toString(),
                    monthlyMatch||weeklyMatch ? "송천 기록 일치 후보" : "송천 입금 확인 필요");
        }
        return Map.of(
            "status","saved",
            "category",row.getCategory(),
            "matchStatus", matchStatuses.getOrDefault(row.getId().toString(),"송천 입금 확인 필요"),
            "linkedCount",samePayer.size(),
            "counterparty",row.getCounterparty()==null?"":row.getCounterparty(),
            "vendorId",vendorId,
            "batch",remember && row.getCounterparty()!=null && !row.getCounterparty().isBlank(),
            "matchStatuses",matchStatuses
        );
    }

    private record LinkOutcome(PersonalBankTransactionEntity row,
                               List<PersonalBankTransactionEntity> affectedRows) {}

    private LinkOutcome saveVendorLink(Long id,Long vendorId,
                                               String settlementMonth,boolean remember) {
        var row=transactions.findById(id).orElseThrow(
            ()->new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,"거래내역을 찾을 수 없습니다."));
        if(row.getIncoming().signum()<=0)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"입금 거래만 연결할 수 있습니다.");
        vendors.findById(vendorId).orElseThrow(
            ()->new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"거래처를 찾을 수 없습니다."));

        String ym;
        try {
            // No settlement month field in the bank ledger UI. Preserve prior
            // month allocations if a payer is associated with another vendor.
            ym=settlementMonth==null||settlementMonth.isBlank()
                ?row.getSettlementMonth():YearMonth.parse(settlementMonth).toString();
        } catch(DateTimeParseException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"정산월 형식이 올바르지 않습니다.");
        }
        // Do not reset the reconciliation flag on a no-op selection.
        transferMatcher.releaseForManualChoice(row);
        if(!Objects.equals(row.getVendorId(),vendorId)
                || !Objects.equals(row.getSettlementMonth(),ym)) row.link(vendorId,ym);
        row.classify("사업 입금");

        String payer=row.getCounterparty();
        if(!remember || payer==null || payer.isBlank()) {
            return new LinkOutcome(row,List.of(row));
        }

        // Reconcile only exact-name incoming records; other payers and
        // outgoing transactions are deliberately not touched.
        var existing=transactions.findByCounterpartyAndIncomingGreaterThan(payer,BigDecimal.ZERO);
        for(var entry:existing) {
            if(entry.getId().equals(row.getId())) continue;
            transferMatcher.releaseForManualChoice(entry);
            // Preserve the individual historical settlement-month allocation.
            if(!Objects.equals(entry.getVendorId(),vendorId))
                entry.link(vendorId,entry.getSettlementMonth());
            entry.classify("사업 입금");
        }

        var alias=aliases.findByAlias(payer).orElse(null);
        if(alias==null) aliases.save(new PersonalBankAliasEntity(payer,vendorId));
        else alias.changeVendor(vendorId);
        return new LinkOutcome(row,existing);
    }

    private static int percentage(BigDecimal value,BigDecimal total){
        if(total==null||total.signum()==0)return 0;
        return value.multiply(new BigDecimal("100")).divide(total,0,java.math.RoundingMode.HALF_UP).intValue();
    }
    public record ChartRow(String label,BigDecimal amount,int percent){}

    private static String categoryFor(String who,String memo,boolean income){
        String content=((who==null?"":who)+" "+(memo==null?"":memo)).toUpperCase(Locale.ROOT);
        if(content.contains("팔공"))return income?"사업 입금":"사업 지출";
        if(income)return "미분류 입금";
        if(content.contains("SKT")||content.contains("KT ")||content.contains("LGU")||content.contains("SKYLIFE")||content.contains("통신"))return "통신비";
        if(content.contains("보험")||content.contains("삼성화재")||content.contains("현대해상"))return "보험";
        if(content.contains("주유")||content.contains("GS칼텍스")||content.contains("SK에너지"))return "차량";
        if(content.contains("한전")||content.contains("한국전력")||content.contains("도시가스")||content.contains("수도요금"))return "공과금";
        if(content.contains("병원")||content.contains("약국"))return "의료";
        if(content.contains("마트")||content.contains("편의점")||content.contains("GS25")||content.contains("CU ")||content.contains("식당"))return "식비·생활";
        return "미분류 지출";
    }
    private static Map<String,Integer> headerColumns(Row row,DataFormatter fmt){
        Map<String,Integer> m=new HashMap<>();
        for(Cell cell:row){
            String s=fmt.formatCellValue(cell).replace(" ","").toLowerCase(Locale.ROOT);
            int i=cell.getColumnIndex();
            if(s.contains("거래일")||s.equals("일자")||s.equals("거래일시"))m.put("date",i);
            if(s.contains("입금액")||s.contains("입금금액")||s.equals("입금"))m.put("in",i);
            if(s.contains("출금액")||s.contains("출금금액")||s.equals("출금"))m.put("out",i);
            if(s.contains("거래기록사항")||s.contains("입금자")||s.contains("상대방")||s.contains("의뢰인"))m.put("who",i);
            if(s.contains("거래내용"))m.put("channel",i);
            if(s.contains("거래후잔액"))m.put("balance",i);
            if(s.contains("적요")||s.contains("거래메모")||s.contains("기재내용"))m.put("memo",i);
        }
        return m;
    }
    private static String readLine(Row r,DataFormatter fmt){StringBuilder s=new StringBuilder();for(Cell c:r)s.append(" ").append(fmt.formatCellValue(c));return s.toString();}
    private static String extractAccount(String s){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:\\d{3,6}-){1,4}\\d{2,8}").matcher(s);
        return m.find()?m.group():"";
    }
    private static String value(Row row,Integer index,DataFormatter f){if(index==null)return "";Cell c=row.getCell(index);return c==null?"":f.formatCellValue(c).trim();}
    private static BigDecimal money(String s){try{return new BigDecimal(s.replace(",","").replace("원","").replace(" ","").replace("▲","-").replace("−","-").trim());}catch(Exception e){return BigDecimal.ZERO;}}
    private static LocalDate parseDate(String s,Cell c){
        try{if(c!=null&&c.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(c))return c.getLocalDateTimeCellValue().toLocalDate();}catch(Exception ignored){}
        s=s.trim().replace(".","-").replace("/","-");
        try{if(s.length()>=10)return LocalDate.parse(s.substring(0,10));}catch(Exception ignored){}
        try{return LocalDate.parse(s,DateTimeFormatter.ofPattern("yyyy-M-d"));}catch(Exception ignored){}
        try{return LocalDate.parse(s,DateTimeFormatter.ofPattern("yyyyMMdd"));}catch(Exception ignored){}
        return null;
    }
    private static String sha256(String s)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}

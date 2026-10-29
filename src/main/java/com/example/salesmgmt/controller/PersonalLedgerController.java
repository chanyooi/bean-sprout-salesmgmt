package com.example.salesmgmt.controller;

import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import com.example.salesmgmt.service.PaymentService;
import com.example.salesmgmt.service.NonghyupXlsCompatibility;
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
    public PersonalLedgerController(PersonalBankTransactionRepository transactions,PersonalBankAliasRepository aliases,PersonalBankCategoryRuleRepository categoryRules,VendorRepository vendors,PaymentService payments,PaymentRepository businessPayments,WeeklyPaymentRepository weeklyPayments) {
        this.transactions=transactions;this.aliases=aliases;this.categoryRules=categoryRules;this.vendors=vendors;this.payments=payments;
        this.businessPayments=businessPayments;this.weeklyPayments=weeklyPayments;
    }
    @GetMapping("/access")
    public org.springframework.http.ResponseEntity<Void> access() {
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping
    @Transactional(readOnly=true)
    public String page(@RequestParam(required=false) String month,Model model){
        YearMonth ym=month==null||month.isBlank()?YearMonth.now():YearMonth.parse(month);
        var rows=transactions.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(ym.atDay(1),ym.atEndOfMonth());
        BigDecimal income=rows.stream().map(PersonalBankTransactionEntity::getIncoming).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal expense=rows.stream().map(PersonalBankTransactionEntity::getOutgoing).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<String,BigDecimal> categories=new TreeMap<>();
        BigDecimal personalExpense=BigDecimal.ZERO;
        BigDecimal personalIncome=BigDecimal.ZERO;
        for(var r:rows) {
            if(r.getOutgoing().signum()>0&&!List.of("사업 지출","계좌간 이체").contains(r.getCategory())){
                categories.merge(r.getCategory(),r.getOutgoing(),BigDecimal::add);
                personalExpense=personalExpense.add(r.getOutgoing());
            }
            if(r.getIncoming().signum()>0&&!List.of("사업 입금","계좌간 이체").contains(r.getCategory()))
                personalIncome=personalIncome.add(r.getIncoming());
        }
        var monthlyBankPayments=businessPayments.findForPaymentDateRange(ym.atDay(1),ym.atEndOfMonth());
        var weeklyBankPayments=weeklyPayments.findForPaymentDateRange(ym.atDay(1),ym.atEndOfMonth());
        Map<Long,String> matches=new HashMap<>();
        for(var r:rows) {
            if(r.getIncoming().signum()==0) continue;
            if(r.getVendorId()==null){matches.put(r.getId(),"거래처 미연결");continue;}
            boolean monthlyMatch=monthlyBankPayments.stream().anyMatch(p->p.getVendor().getId().equals(r.getVendorId())
                &&p.getPaymentDate().equals(r.getTransactionDate())&&p.getAmount().compareTo(r.getIncoming())==0);
            boolean weeklyMatch=weeklyBankPayments.stream().anyMatch(p->p.getVendor().getId().equals(r.getVendorId())
                &&p.getPaymentDate().equals(r.getTransactionDate())&&p.getAmount().compareTo(r.getIncoming())==0);
            matches.put(r.getId(),monthlyMatch||weeklyMatch?"송천 기록 일치 후보":"송천 입금 확인 필요");
        }

        List<ChartRow> categoryBars=new ArrayList<>();
        for(var entry:categories.entrySet())categoryBars.add(
            new ChartRow(entry.getKey(),entry.getValue(),percentage(entry.getValue(),personalExpense)));
        List<YearMonth> periods=new ArrayList<>();
        Map<YearMonth,BigDecimal> monthlyTotals=new LinkedHashMap<>();
        BigDecimal maxMonth=BigDecimal.ZERO;
        for(int offset=5;offset>=0;offset--){
            YearMonth current=ym.minusMonths(offset);
            periods.add(current);
            var monthlyRows=current.equals(ym)?rows:transactions.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(current.atDay(1),current.atEndOfMonth());
            BigDecimal total=monthlyRows.stream()
                .filter(t->!List.of("사업 지출","계좌간 이체").contains(t.getCategory()))
                .map(PersonalBankTransactionEntity::getOutgoing).reduce(BigDecimal.ZERO,BigDecimal::add);
            monthlyTotals.put(current,total);
            if(total.compareTo(maxMonth)>0)maxMonth=total;
        }
        List<ChartRow> monthlyBars=new ArrayList<>();
        for(YearMonth current:periods)
            monthlyBars.add(new ChartRow(current.toString(),monthlyTotals.get(current),percentage(monthlyTotals.get(current),maxMonth)));
        model.addAttribute("categoryBars",categoryBars);
        model.addAttribute("monthlyBars",monthlyBars);
        model.addAttribute("month",ym.toString());
        model.addAttribute("previousMonth",ym.minusMonths(1).toString());
        model.addAttribute("nextMonth",ym.plusMonths(1).toString());
        model.addAttribute("rows",rows);model.addAttribute("income",income);model.addAttribute("expense",expense);
        model.addAttribute("net",income.subtract(expense));model.addAttribute("categories",categories);
        model.addAttribute("personalExpense",personalExpense);model.addAttribute("personalIncome",personalIncome);
        model.addAttribute("matches",matches);
        model.addAttribute("vendors",vendors.findAllByOrderByInputNameAsc());
        return "personal-ledger";
    }
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
            flash.addFlashAttribute("message","가져온 거래 "+inserted+"건, 중복 제외 "+skipped+"건");
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
    public String classify(@PathVariable Long id,@RequestParam String category,@RequestParam(defaultValue="false") boolean remember,@RequestParam String month){
        var row=transactions.findById(id).orElseThrow();
        String sanitized=category.trim();
        if(sanitized.isBlank()) throw new IllegalArgumentException("분류를 입력해주세요.");
        if(sanitized.length()>80)sanitized=sanitized.substring(0,80);
        row.classify(sanitized);
        if(remember&&row.getCounterparty()!=null&&!row.getCounterparty().isBlank()){
            var rule=categoryRules.findByCounterparty(row.getCounterparty()).orElse(null);
            if(rule==null)categoryRules.save(new PersonalBankCategoryRuleEntity(row.getCounterparty(),sanitized));
            else rule.updateCategory(sanitized);
        }
        return "redirect:/personal-ledger?month="+month;
    }
    @PostMapping("/{id}/link")
    @Transactional
    public String link(@PathVariable Long id,
                       @RequestParam Long vendorId,
                       @RequestParam(required=false) String settlementMonth,
                       @RequestParam(defaultValue="false") boolean remember,
                       @RequestParam String month) {
        saveVendorLink(id, vendorId, settlementMonth, remember);
        return "redirect:/personal-ledger?month="+month;
    }

    /**
     * Inline linking: returns a small JSON response so selecting a vendor
     * does not reload the entire ledger and lose the user's scroll position.
     * Does not create or modify Songcheon payment records.
     */
    @PostMapping("/{id}/link-instant")
    @ResponseBody
    @Transactional
    public Map<String,String> linkInstant(@PathVariable Long id,
                                           @RequestParam Long vendorId,
                                           @RequestParam(required=false) String settlementMonth,
                                           @RequestParam(defaultValue="true") boolean remember) {
        var row=saveVendorLink(id,vendorId,settlementMonth,remember);
        LocalDate date=row.getTransactionDate();
        boolean monthlyMatch=businessPayments.findForPaymentDateRange(date,date).stream()
            .anyMatch(p->p.getVendor().getId().equals(vendorId)
                &&p.getAmount().compareTo(row.getIncoming())==0);
        boolean weeklyMatch=weeklyPayments.findForPaymentDateRange(date,date).stream()
            .anyMatch(p->p.getVendor().getId().equals(vendorId)
                &&p.getAmount().compareTo(row.getIncoming())==0);
        return Map.of("status","saved",
                      "category",row.getCategory(),
                      "matchStatus",monthlyMatch||weeklyMatch?"송천 기록 일치 후보":"송천 입금 확인 필요");
    }

    private PersonalBankTransactionEntity saveVendorLink(Long id,Long vendorId,
                                               String settlementMonth,boolean remember) {
        var row=transactions.findById(id).orElseThrow(
            ()->new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,"거래내역을 찾을 수 없습니다."));
        if(row.getIncoming().signum()<=0)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"입금 거래만 연결할 수 있습니다.");
        var vendor=vendors.findById(vendorId).orElseThrow(
            ()->new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"거래처를 찾을 수 없습니다."));
        if(vendor.getInputName().contains("팔공")||vendor.getStatementName().contains("팔공"))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"팔공은 미지급금 거래처이므로 은행 입금으로 연결할 수 없습니다.");
        String ym;
        try {
            ym=settlementMonth==null||settlementMonth.isBlank()
                ?null:YearMonth.parse(settlementMonth).toString();
        }catch(java.time.format.DateTimeParseException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"정산월 형식이 올바르지 않습니다.");
        }
        row.link(vendorId,ym);
        row.classify("사업 입금");
        if(remember&&row.getCounterparty()!=null&&!row.getCounterparty().isBlank()){
            var alias=aliases.findByAlias(row.getCounterparty()).orElse(null);
            if(alias==null) aliases.save(new PersonalBankAliasEntity(row.getCounterparty(),vendorId));
            else alias.changeVendor(vendorId);
        }
        return row;
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

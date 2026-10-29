package com.example.salesmgmt.controller;

import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import com.example.salesmgmt.service.PaymentService;
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
    private final VendorRepository vendors;
    private final PaymentService payments;
    public PersonalLedgerController(PersonalBankTransactionRepository transactions,PersonalBankAliasRepository aliases,VendorRepository vendors,PaymentService payments) {
        this.transactions=transactions;this.aliases=aliases;this.vendors=vendors;this.payments=payments;
    }
    @GetMapping
    @Transactional(readOnly=true)
    public String page(@RequestParam(required=false) String month,Model model){
        YearMonth ym=month==null||month.isBlank()?YearMonth.now():YearMonth.parse(month);
        var rows=transactions.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(ym.atDay(1),ym.atEndOfMonth());
        BigDecimal income=rows.stream().map(PersonalBankTransactionEntity::getIncoming).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal expense=rows.stream().map(PersonalBankTransactionEntity::getOutgoing).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<String,BigDecimal> categories=new TreeMap<>();
        for(var r:rows) if(r.getOutgoing().signum()>0) categories.merge(r.getCategory(),r.getOutgoing(),BigDecimal::add);
        model.addAttribute("month",ym.toString());
        model.addAttribute("previousMonth",ym.minusMonths(1).toString());
        model.addAttribute("nextMonth",ym.plusMonths(1).toString());
        model.addAttribute("rows",rows);model.addAttribute("income",income);model.addAttribute("expense",expense);
        model.addAttribute("net",income.subtract(expense));model.addAttribute("categories",categories);
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
                try(InputStream in=file.getInputStream();Workbook wb=WorkbookFactory.create(in)){
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
                                entry.classify(alias.isPresent()?"사업 입금":"미분류 입금");
                            } else entry.classify("미분류 지출");
                            transactions.save(entry);inserted++;
                        }
                    }
                }
            }
            flash.addFlashAttribute("message","가져온 거래 "+inserted+"건, 중복 제외 "+skipped+"건");
        }catch(Exception ex){throw new IllegalArgumentException("은행 엑셀 가져오기 실패: "+ex.getMessage(),ex);}
        return "redirect:/personal-ledger";
    }
    @PostMapping("/{id}/classify")
    @Transactional
    public String classify(@PathVariable Long id,@RequestParam String category,@RequestParam String month){
        var row=transactions.findById(id).orElseThrow();
        row.classify(category.trim().substring(0,Math.min(80,category.trim().length())));
        return "redirect:/personal-ledger?month="+month;
    }
    @PostMapping("/{id}/link")
    @Transactional
    public String link(@PathVariable Long id,@RequestParam Long vendorId,@RequestParam(required=false)String settlementMonth,
                       @RequestParam(defaultValue="false")boolean remember,@RequestParam String month){
        var row=transactions.findById(id).orElseThrow();
        if(row.getIncoming().signum()<=0)throw new IllegalArgumentException("입금 거래만 연결할 수 있습니다.");
        vendors.findById(vendorId).orElseThrow();
        String ym=settlementMonth==null||settlementMonth.isBlank()?null:YearMonth.parse(settlementMonth).toString();
        row.link(vendorId,ym);row.classify("사업 입금");
        if(remember&&row.getCounterparty()!=null&&!row.getCounterparty().isBlank()){
            var a=aliases.findByAlias(row.getCounterparty()).orElse(null);
            if(a==null)aliases.save(new PersonalBankAliasEntity(row.getCounterparty(),vendorId));
            else a.changeVendor(vendorId);
        }
        return "redirect:/personal-ledger?month="+month;
    }
    private static Map<String,Integer> headerColumns(Row row,DataFormatter fmt){
        Map<String,Integer> m=new HashMap<>();
        for(Cell cell:row){
            String s=fmt.formatCellValue(cell).replace(" ","").toLowerCase(Locale.ROOT);
            int i=cell.getColumnIndex();
            if(s.contains("거래일")||s.equals("일자")||s.equals("거래일시"))m.put("date",i);
            if(s.contains("입금액")||s.equals("입금"))m.put("in",i);
            if(s.contains("출금액")||s.equals("출금"))m.put("out",i);
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

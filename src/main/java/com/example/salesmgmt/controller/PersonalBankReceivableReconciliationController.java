package com.example.salesmgmt.controller;

import com.example.salesmgmt.repository.PersonalBankTransactionRepository;
import com.example.salesmgmt.service.PersonalBankReceivableReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/personal-ledger/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class PersonalBankReceivableReconciliationController {
    private final PersonalBankReceivableReconciliationService reconciliationService;
    private final PersonalBankTransactionRepository transactions;

    public PersonalBankReceivableReconciliationController(
             PersonalBankReceivableReconciliationService reconciliationService,
             PersonalBankTransactionRepository transactions){
        this.reconciliationService=reconciliationService;
        this.transactions=transactions;
    }

    @GetMapping
    public String view(@RequestParam(defaultValue="2026-09") String month,
                       @RequestParam(required=false) String from,
                       @RequestParam(required=false) String to, Model model){
        YearMonth ym=parseMonth(month);
        LocalDate start=parseDate(from,ym.atDay(1));
        LocalDate end=parseDate(to,ym.plusMonths(1).atEndOfMonth());
        var report=reconciliationService.compare(ym,start,end);
        model.addAttribute("report",report);
        model.addAttribute("month",ym.toString());
        model.addAttribute("from",start.toString());
        model.addAttribute("to",end.toString());
        return "personal-bank-reconciliation";
    }

    @PostMapping("/{transactionId}/assign")
    @Transactional
    public String assign(@PathVariable Long transactionId,
                         @RequestParam String month,
                         @RequestParam(required=false) String from,
                         @RequestParam(required=false) String to,
                         @RequestParam(defaultValue="true") boolean assign,
                         RedirectAttributes messages){
        YearMonth selected=parseMonth(month);
        LocalDate start=parseDate(from,selected.atDay(1));
        LocalDate end=parseDate(to,selected.plusMonths(1).atEndOfMonth());
        // Validate parameters before altering any bank record.
        reconciliationService.compare(selected,start,end);

        var bank=transactions.findById(transactionId).orElseThrow(()->
            new ResponseStatusException(HttpStatus.NOT_FOUND,"입금 거래를 찾을 수 없습니다."));
        if(bank.getIncoming().signum()<=0 || bank.getVendorId()==null || bank.isAutoTransfer())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"거래처에 연결된 정상 입금만 정산월을 지정할 수 있습니다.");
        if(bank.getTransactionDate().isBefore(start)||bank.getTransactionDate().isAfter(end))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"선택한 입금 조회 기간의 거래가 아닙니다.");
        String current=bank.getSettlementMonth();
        if(assign){
            if(current!=null&&!current.isBlank()&&!current.equals(selected.toString()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 정산월에 배정된 입금입니다.");
            if(!selected.toString().equals(current))bank.link(bank.getVendorId(),selected.toString());
            messages.addFlashAttribute("message",selected+" 대금으로 지정했습니다. 기존 송천 장부에는 입금을 추가하지 않았습니다.");
        } else {
            if(!selected.toString().equals(current))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"선택한 정산월에 배정된 입금이 아닙니다.");
            bank.link(bank.getVendorId(),null);
            messages.addFlashAttribute("message","정산월 지정을 해제했습니다. 은행 입금 기록과 거래처 연결은 유지됩니다.");
        }
        return "redirect:/personal-ledger/reconciliation?month="+selected+
               "&from="+start+"&to="+end;
    }

    private static YearMonth parseMonth(String text){
        try{return YearMonth.parse(text);}catch(DateTimeParseException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"정산월 형식이 올바르지 않습니다.");
        }
    }
    private static LocalDate parseDate(String raw,LocalDate fallback){
        if(raw==null||raw.isBlank())return fallback;
        try{return LocalDate.parse(raw);}catch(DateTimeParseException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"입금 조회 날짜가 올바르지 않습니다.");
        }
    }
}

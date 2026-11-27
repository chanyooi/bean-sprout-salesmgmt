package com.example.salesmgmt.controller;
import com.example.salesmgmt.entity.VendorProfileEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorProfileRepository;
import com.example.salesmgmt.service.ReceivableBillingAdjustmentService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Controller
public class RegionalSalesController {
    private final SalesItemRepository items;
    private final VendorProfileRepository profiles;
    public RegionalSalesController(SalesItemRepository items, VendorProfileRepository profiles) {
        this.items=items; this.profiles=profiles;
    }
    public record Row(Long id,String name,String address,BigDecimal lat,BigDecimal lng,BigDecimal current,BigDecimal previous,long orders) {}
    private static class Amount {
        String name; BigDecimal sales=BigDecimal.ZERO; Set<String> orders=new HashSet<>();
        Amount(String name){this.name=name;}
    }
    private record Period(Map<Long,Amount> values,boolean available,long missing){}
    private Period period(YearMonth month,int days){
        Map<Long,Amount> values=new HashMap<>(); long missing=0;
        var records=items.findForMonthlyReport(month.atDay(1),month.atDay(Math.min(days,month.lengthOfMonth())));
        for(var item:records){
            var order=item.getSalesOrder();var vendor=order.getVendor();
            var a=values.computeIfAbsent(vendor.getId(),id->new Amount(vendor.getInputName()));
            a.orders.add(order.getOrderNumber());
            if(ReceivableBillingAdjustmentService.isPalgongTofuTray(item)) a.sales=a.sales.add(ReceivableBillingAdjustmentService.billingAmount(item));
            else if(item.getUnitPrice()==null||item.getLineAmount()==null) missing++;
            else a.sales=a.sales.add(item.getLineAmount());
        }
        return new Period(values,!records.isEmpty(),missing);
    }
    @Transactional(readOnly=true)
    @GetMapping("/region-sales")
    public String view(@RequestParam(required=false) String month,@RequestParam(defaultValue="previous") String compare,Model model){
        LocalDate latest=items.findLatestSalesDate(); YearMonth selected;
        try{selected=month==null||month.isBlank()?(latest==null?YearMonth.now():YearMonth.from(latest)):YearMonth.parse(month);}
        catch(DateTimeException ex){selected=YearMonth.now();}
        if(!Set.of("previous","year","average").contains(compare))compare="previous";
        int days=selected.lengthOfMonth();
        if(selected.equals(YearMonth.now())) days=latest!=null&&selected.equals(YearMonth.from(latest))?Math.min(latest.getDayOfMonth(),LocalDate.now().getDayOfMonth()):LocalDate.now().getDayOfMonth();
        Period current=period(selected,days); List<Period> baselines=new ArrayList<>();
        // Full past months use full comparison months; unfinished months use the same day cutoff.
        int baselineDays=selected.equals(YearMonth.now())?days:31;
        if(compare.equals("average")){for(int i=1;i<=3;i++)baselines.add(period(selected.minusMonths(i),baselineDays));}
        else baselines.add(period(selected.minusMonths(compare.equals("year")?12:1),baselineDays));
        boolean comparable=current.available()&&baselines.stream().allMatch(Period::available);
        Map<Long,VendorProfileEntity> byId=new HashMap<>();
        for(var p:profiles.findAllWithVendor())byId.put(p.getVendor().getId(),p);
        Set<Long> ids=new HashSet<>(current.values().keySet());
        if(comparable)for(var b:baselines)ids.addAll(b.values().keySet());
        List<Row> rows=new ArrayList<>();
        for(Long id:ids){
            Amount now=current.values().get(id),sample=now;BigDecimal old=BigDecimal.ZERO;
            for(var b:baselines){Amount a=b.values().get(id);if(a!=null){old=old.add(a.sales);if(sample==null)sample=a;}}
            var p=byId.get(id);
            BigDecimal prior=comparable?old.divide(BigDecimal.valueOf(baselines.size()),2,RoundingMode.HALF_UP):null;
            rows.add(new Row(id,sample.name,p==null?null:p.getAddress(),p==null?null:p.getLatitude(),p==null?null:p.getLongitude(),now==null?BigDecimal.ZERO:now.sales,prior,now==null?0:now.orders.size()));
        }
        rows.sort(Comparator.comparing(Row::name));
        model.addAttribute("rows",rows);model.addAttribute("selectedMonth",selected.toString());model.addAttribute("compare",compare);
        model.addAttribute("comparable",comparable);model.addAttribute("periodLabel",selected+" 1~"+days+"일");
        model.addAttribute("missing",current.missing()+baselines.stream().mapToLong(Period::missing).sum());
        return "region-sales";
    }
}
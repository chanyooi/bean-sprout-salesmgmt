package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.SaveResult;
import com.example.salesmgmt.exception.SalesDataConflictException;
import com.example.salesmgmt.service.DailyEntryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
public class DailyEntryController {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN);

    private final DailyEntryService dailyEntryService;

    public DailyEntryController(DailyEntryService dailyEntryService) {
        this.dailyEntryService = dailyEntryService;
    }

    @GetMapping("/daily-entry")
    public String dailyEntry(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            Model model
    ) {
        LocalDate selectedDate = date == null
                ? LocalDate.now(KOREA_ZONE)
                : date;

        var page = dailyEntryService.load(selectedDate);
        model.addAttribute("page", page);
        model.addAttribute("selectedDate", selectedDate.toString());
        model.addAttribute("dateLabel", selectedDate.format(DATE_LABEL));
        model.addAttribute("previousDate", selectedDate.minusDays(1).toString());
        model.addAttribute("nextDate", selectedDate.plusDays(1).toString());
        model.addAttribute("today", LocalDate.now(KOREA_ZONE).toString());
        model.addAttribute("vendorCount", DailyEntryService.VENDOR_ORDER.size());
        return "daily-entry";
    }

    @PostMapping("/daily-entry")
    public String saveDailyEntry(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam("vendorName") String[] vendorNames,
            @RequestParam("cutKg") String[] cutKg,
            @RequestParam("regular") String[] regular,
            @RequestParam("small") String[] small,
            @RequestParam("curly") String[] curly,
            @RequestParam("boxRegular") String[] boxRegular,
            @RequestParam("boxCurly") String[] boxCurly,
            @RequestParam("mungSprout") String[] mungSprout,
            @RequestParam("returnContainer") String[] returnContainer,
            @RequestParam("tofu") String[] tofu,
            @RequestParam("tofuPlate") String[] tofuPlate,
            @RequestParam("returnContainerUnitPrice") String[] returnContainerUnitPrice,
            @RequestParam("deliveryMethod") String[] deliveryMethod,
            @RequestParam("note") String[] note,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int expected = DailyEntryService.VENDOR_ORDER.size();
            validateLength("거래처", vendorNames, expected);
            validateLength("두절", cutKg, expected);
            validateLength("일반콩나물", regular, expected);
            validateLength("소립", small, expected);
            validateLength("곱슬콩나물", curly, expected);
            validateLength("3.5kg일반", boxRegular, expected);
            validateLength("3.5kg곱슬", boxCurly, expected);
            validateLength("숙주", mungSprout, expected);
            validateLength("회수통", returnContainer, expected);
            validateLength("손두부", tofu, expected);
            validateLength("두부판", tofuPlate, expected);
            validateLength("회수통단가", returnContainerUnitPrice, expected);
            validateLength("전달방식", deliveryMethod, expected);
            validateLength("비고", note, expected);

            List<DailyEntryService.RowInput> rows = new ArrayList<>(expected);
            for (int i = 0; i < expected; i++) {
                rows.add(new DailyEntryService.RowInput(
                        vendorNames[i],
                        cutKg[i],
                        regular[i],
                        small[i],
                        curly[i],
                        boxRegular[i],
                        boxCurly[i],
                        mungSprout[i],
                        returnContainer[i],
                        tofu[i],
                        tofuPlate[i],
                        returnContainerUnitPrice[i],
                        deliveryMethod[i],
                        note[i]
                ));
            }

            SaveResult result = dailyEntryService.save(date, rows);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "저장 완료 · 신규 " + result.savedItems()
                            + "건 · 수정 " + result.updatedItems()
                            + "건 · 삭제 " + result.deletedItems() + "건"
            );
        } catch (SalesDataConflictException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("saveError", exception.getMessage());
        }

        return "redirect:/daily-entry?date=" + date;
    }

    private void validateLength(String label, String[] values, int expected) {
        if (values == null || values.length != expected) {
            throw new IllegalArgumentException(
                    label + " 입력 행이 누락되었습니다. 화면을 새로고침한 뒤 다시 저장해주세요."
            );
        }
    }
}

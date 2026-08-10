package com.example.salesmgmt.domain;

import java.util.List;
import java.util.Map;

public final class ItemCatalog {

    public static final List<String> ALL_ITEMS = List.of(
            "두절kg",
            "일반콩나물",
            "소립",
            "곱슬콩나물",
            "3.5kg일반",
            "3.5kg곱슬",
            "숙주",
            "회수통",
            "손두부",
            "두부판"
    );

    private static final Map<String, String> TEMPLATE_LABEL_ALIASES = Map.ofEntries(
            Map.entry("두절kg", "두절kg"),
            Map.entry("두절1kg", "두절kg"),
            Map.entry("두절", "두절kg"),
            Map.entry("일반콩나물", "일반콩나물"),
            Map.entry("일반", "일반콩나물"),
            Map.entry("일반(소)콩나물", "일반콩나물"),
            Map.entry("일반소콩나물", "소립"),
            Map.entry("일반소", "소립"),
            Map.entry("소립", "소립"),
            Map.entry("곱슬콩나물", "곱슬콩나물"),
            Map.entry("곱슬", "곱슬콩나물"),
            Map.entry("3.5kg일반", "3.5kg일반"),
            Map.entry("3.5일반", "3.5kg일반"),
            Map.entry("3.5kg곱슬", "3.5kg곱슬"),
            Map.entry("3.5곱슬", "3.5kg곱슬"),
            Map.entry("숙주", "숙주"),
            Map.entry("회수통", "회수통"),
            Map.entry("일소", "회수통"),
            Map.entry("손두부", "손두부"),
            Map.entry("두부판", "두부판")
    );

    private ItemCatalog() {
    }

    public static String normalizeTemplateLabel(String rawLabel) {
        if (rawLabel == null) {
            return null;
        }

        String normalized = rawLabel
                .replaceAll("\\s+", "")
                .trim();

        return TEMPLATE_LABEL_ALIASES.get(normalized);
    }
}

package com.example.salesmgmt.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class VendorRuleService {

    private static final Map<String, String> STATEMENT_NAME_ALIASES = Map.of(
            "명희네해장", "명희네"
    );

    private static final Map<String, String> INPUT_NAME_ALIASES = Map.of(
            "명희네", "명희네해장"
    );

    private static final Set<String> VENDORS_WITHOUT_TEMPLATE = Set.of(
            "산동빅"
    );

    public String statementVendorName(String inputVendorName) {
        String trimmedName = normalize(inputVendorName);
        return STATEMENT_NAME_ALIASES.getOrDefault(trimmedName, trimmedName);
    }

    public String inputVendorName(String statementVendorName) {
        String trimmedName = normalize(statementVendorName);
        return INPUT_NAME_ALIASES.getOrDefault(trimmedName, trimmedName);
    }

    public boolean hasStatementTemplate(String inputVendorName) {
        return !VENDORS_WITHOUT_TEMPLATE.contains(normalize(inputVendorName));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

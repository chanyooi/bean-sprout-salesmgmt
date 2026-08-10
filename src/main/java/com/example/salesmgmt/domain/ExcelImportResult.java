package com.example.salesmgmt.domain;

import java.util.List;

public record ExcelImportResult(
        int sheetCount,
        int salesRowCount,
        List<DeliveryRecord> records,
        List<OrderSnapshot> orderSnapshots,
        List<ImportIssue> issues
) {
    public long errorCount() {
        return issues.stream()
                .filter(issue -> issue.level() == ImportIssue.IssueLevel.ERROR)
                .count();
    }

    public long warningCount() {
        return issues.stream()
                .filter(issue -> issue.level() == ImportIssue.IssueLevel.WARNING)
                .count();
    }

    public long vendorCount() {
        return orderSnapshots.stream()
                .map(OrderSnapshot::inputVendor)
                .distinct()
                .count();
    }
}

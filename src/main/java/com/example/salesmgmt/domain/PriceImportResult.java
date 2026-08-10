package com.example.salesmgmt.domain;

import java.util.List;

public record PriceImportResult(
        int sheetCount,
        List<PriceImportRow> rows,
        List<ImportIssue> issues
) {
    public long vendorCount() {
        return rows.stream()
                .map(PriceImportRow::inputVendor)
                .distinct()
                .count();
    }

    public long warningCount() {
        return issues.stream()
                .filter(issue -> issue.level() == ImportIssue.IssueLevel.WARNING)
                .count();
    }

    public long errorCount() {
        return issues.stream()
                .filter(issue -> issue.level() == ImportIssue.IssueLevel.ERROR)
                .count();
    }
}

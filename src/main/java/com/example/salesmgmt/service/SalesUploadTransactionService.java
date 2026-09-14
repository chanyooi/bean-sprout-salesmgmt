package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ExcelImportResult;
import com.example.salesmgmt.domain.SaveResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesUploadTransactionService {
    private final UploadHistoryService history;
    private final SalesPersistenceService sales;
    public SalesUploadTransactionService(UploadHistoryService history, SalesPersistenceService sales) {
        this.history = history;
        this.sales = sales;
    }
    @Transactional
    public SaveResult save(String filename, ExcelImportResult result) {
        String before = history.captureSalesSnapshot(result.orderSnapshots());
        SaveResult saved = sales.save(result.records(), result.orderSnapshots());
        history.recordSuccess(filename, before, saved);
        return saved;
    }
}

package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.SavedSaleRow;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SalesQueryService {

    private final SalesItemRepository salesItemRepository;

    public SalesQueryService(SalesItemRepository salesItemRepository) {
        this.salesItemRepository = salesItemRepository;
    }

    @Transactional(readOnly = true)
    public List<SavedSaleRow> findRecent(int limit) {
        return salesItemRepository.findRecent(PageRequest.of(0, limit))
                .stream()
                .map(item -> new SavedSaleRow(
                        item.getSalesOrder().getOrderNumber(),
                        item.getSalesOrder().getDeliveryDate(),
                        item.getSalesOrder().getVendor().getInputName(),
                        item.getSalesOrder().getVendor().getStatementName(),
                        item.getItemName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineAmount(),
                        item.getSalesOrder().getReturnContainerUnitPrice(),
                        item.getSalesOrder().getDeliveryMethod(),
                        item.getSalesOrder().getNote()
                ))
                .toList();
    }
}

package com.example.salesmgmt.domain;

import java.time.LocalDateTime;

public record StatementSendRow(
        Long vendorId,
        String vendorName,
        String phone,
        String memo,
        boolean sent,
        LocalDateTime sentAt
) {}

package com.example.salesmgmt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReturnContainerDataCorrectionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(
            ReturnContainerDataCorrectionRunner.class
    );

    private final ReturnContainerDataCorrectionService correctionService;

    public ReturnContainerDataCorrectionRunner(
            ReturnContainerDataCorrectionService correctionService
    ) {
        this.correctionService = correctionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int corrected = correctionService.correctExistingSales();

        if (corrected > 0) {
            log.info(
                    "회수통 판매금액 부호/무보증금 데이터 {}건을 자동 보정했습니다.",
                    corrected
            );
        }
    }
}

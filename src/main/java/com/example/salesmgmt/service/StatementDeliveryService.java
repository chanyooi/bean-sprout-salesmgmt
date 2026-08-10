package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StatementSendRow;
import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

@Service
public class StatementDeliveryService {

    private final StatementDeliverySettingRepository settingRepository;
    private final StatementDeliveryLogRepository logRepository;
    private final VendorRepository vendorRepository;
    private final VendorProfileRepository vendorProfileRepository;

    public StatementDeliveryService(
            StatementDeliverySettingRepository settingRepository,
            StatementDeliveryLogRepository logRepository,
            VendorRepository vendorRepository,
            VendorProfileRepository vendorProfileRepository
    ) {
        this.settingRepository = settingRepository;
        this.logRepository = logRepository;
        this.vendorRepository = vendorRepository;
        this.vendorProfileRepository = vendorProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<StatementSendRow> queue(YearMonth month) {
        return settingRepository
                .findAllByOrderByVendor_InputNameAsc()
                .stream()
                .map(setting -> {
                    Long vendorId =
                            setting.getVendor().getId();

                    var log = logRepository
                            .findByVendor_IdAndMonthKey(
                                    vendorId,
                                    month.toString()
                            );

                    return new StatementSendRow(
                            vendorId,
                            setting.getVendor().getInputName(),
                            resolvedPhone(setting),
                            setting.getMemo(),
                            log.isPresent(),
                            log.map(
                                    StatementDeliveryLogEntity::getSentAt
                            ).orElse(null)
                    );
                })
                .toList();
    }

    @Transactional
    public void upsert(
            Long vendorId,
            String phone,
            String memo
    ) {
        VendorEntity vendor =
                vendorRepository.findById(vendorId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "거래처를 찾을 수 없습니다."
                                )
                        );

        StatementDeliverySettingEntity setting =
                settingRepository
                        .findByVendor_Id(vendorId)
                        .orElseGet(() ->
                                new StatementDeliverySettingEntity(
                                        vendor,
                                        phone,
                                        memo
                                )
                        );

        setting.update(phone, memo);
        settingRepository.save(setting);
    }

    @Transactional
    public void remove(Long vendorId) {
        settingRepository
                .findByVendor_Id(vendorId)
                .ifPresent(settingRepository::delete);
    }

    @Transactional
    public void markSent(
            Long vendorId,
            YearMonth month
    ) {
        if (logRepository
                .findByVendor_IdAndMonthKey(
                        vendorId,
                        month.toString()
                )
                .isPresent()) {
            return;
        }

        VendorEntity vendor =
                vendorRepository.findById(vendorId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "거래처를 찾을 수 없습니다."
                                )
                        );

        logRepository.save(
                new StatementDeliveryLogEntity(
                        vendor,
                        month.toString()
                )
        );
    }

    @Transactional
    public void markUnsent(
            Long vendorId,
            YearMonth month
    ) {
        logRepository.deleteByVendor_IdAndMonthKey(
                vendorId,
                month.toString()
        );
    }

    @Transactional(readOnly = true)
    public String phoneForVendor(Long vendorId) {
        return settingRepository
                .findByVendor_Id(vendorId)
                .map(this::resolvedPhone)
                .orElseGet(() ->
                        vendorProfileRepository
                                .findByVendor_Id(vendorId)
                                .map(VendorProfileEntity::getPhone)
                                .orElse(null)
                );
    }

    @Transactional(readOnly = true)
    public boolean isManaged(Long vendorId) {
        return vendorId != null
                && settingRepository
                .findByVendor_Id(vendorId)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isSent(
            Long vendorId,
            YearMonth month
    ) {
        return vendorId != null
                && logRepository
                .findByVendor_IdAndMonthKey(
                        vendorId,
                        month.toString()
                )
                .isPresent();
    }

    private String resolvedPhone(
            StatementDeliverySettingEntity setting
    ) {
        if (setting.getPhone() != null
                && !setting.getPhone().isBlank()) {
            return setting.getPhone();
        }

        return vendorProfileRepository
                .findByVendor_Id(
                        setting.getVendor().getId()
                )
                .map(VendorProfileEntity::getPhone)
                .orElse(null);
    }
}

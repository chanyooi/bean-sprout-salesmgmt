package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.MonthlyClosureEntity;
import com.example.salesmgmt.repository.MonthlyClosureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class MonthlyCloseService {

    private final MonthlyClosureRepository repository;

    public MonthlyCloseService(MonthlyClosureRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isClosed(YearMonth month) {
        return repository.existsByMonthKey(month.toString());
    }

    @Transactional(readOnly = true)
    public void assertOpen(LocalDate date) {
        if (date == null) return;
        assertOpen(YearMonth.from(date));
    }

    @Transactional(readOnly = true)
    public void assertOpen(YearMonth month) {
        if (isClosed(month)) {
            throw new IllegalArgumentException(
                    month + "은(는) 마감된 월입니다. 월 마감을 해제한 뒤 수정해주세요."
            );
        }
    }

    @Transactional
    public void close(YearMonth month) {
        if (!repository.existsByMonthKey(month.toString())) {
            repository.save(new MonthlyClosureEntity(month.toString()));
        }
    }

    @Transactional
    public void reopen(YearMonth month) {
        repository.deleteByMonthKey(month.toString());
    }

    @Transactional(readOnly = true)
    public List<MonthlyClosureEntity> findClosures() {
        return repository.findAllByOrderByMonthKeyDesc();
    }
}

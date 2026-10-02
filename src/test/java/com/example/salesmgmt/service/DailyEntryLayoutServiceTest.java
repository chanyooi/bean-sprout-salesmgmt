package com.example.salesmgmt.service;

import com.example.salesmgmt.repository.DailyEntryLayoutRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
class DailyEntryLayoutServiceTest {
    @Autowired DailyEntryLayoutRepository repository;

    private DailyEntryLayoutService service() {
        DailyEntryService entries = mock(DailyEntryService.class);
        when(entries.vendorOrder()).thenReturn(List.of("거래처A", "거래처B"));
        return new DailyEntryLayoutService(repository, entries);
    }

    @Test void layoutPersistsAcrossServiceInstancesAndCanBeRestored() {
        var layout = new DailyEntryLayoutService.Layout(List.of("2", "1"), List.of("1"),
                List.of("regular", "cutKg"), List.of("cutKg"));
        service().save(layout);
        repository.flush();
        assertThat(service().load()).isEqualTo(layout);
        var defaultLayout = new DailyEntryLayoutService.Layout(List.of(), List.of(), List.of(), List.of());
        service().save(defaultLayout);
        assertThat(service().load()).isEqualTo(defaultLayout);
    }

    @Test void invalidOrFullyHiddenLayoutsDoNotReplaceSavedSettings() {
        var initial = service().load();
        assertThatThrownBy(() -> service().save(new DailyEntryLayoutService.Layout(
                List.of("1", "1"), List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().save(new DailyEntryLayoutService.Layout(
                List.of("3"), List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().save(new DailyEntryLayoutService.Layout(
                List.of(), List.of("1", "2"), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().save(new DailyEntryLayoutService.Layout(
                List.of(), List.of(), java.util.Arrays.asList((String) null), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service().load()).isEqualTo(initial);
    }
}

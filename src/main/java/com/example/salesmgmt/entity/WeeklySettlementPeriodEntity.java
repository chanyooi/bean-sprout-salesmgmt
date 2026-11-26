package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "weekly_settlement_periods", uniqueConstraints = @UniqueConstraint(
        name = "uk_weekly_period_vendor_anchor", columnNames = {"vendor_id", "week_anchor"}))
public class WeeklySettlementPeriodEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "week_anchor", nullable = false)
    private LocalDate weekAnchor;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    protected WeeklySettlementPeriodEntity() {}

    public WeeklySettlementPeriodEntity(VendorEntity vendor, LocalDate weekAnchor, LocalDate startsOn, LocalDate endsOn) {
        this.vendor = vendor;
        this.weekAnchor = weekAnchor;
        changeDates(startsOn, endsOn);
    }

    public VendorEntity getVendor() { return vendor; }
    public LocalDate getWeekAnchor() { return weekAnchor; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }

    public void changeDates(LocalDate start, LocalDate end) {
        startsOn = start;
        endsOn = end;
    }
}

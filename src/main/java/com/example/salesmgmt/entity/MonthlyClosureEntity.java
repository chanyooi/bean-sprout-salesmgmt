package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "monthly_closures",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_monthly_closures_month",
                columnNames = "month_key"
        )
)
public class MonthlyClosureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    protected MonthlyClosureEntity() {}

    public MonthlyClosureEntity(String monthKey) {
        this.monthKey = monthKey;
        this.closedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getMonthKey() { return monthKey; }
    public LocalDateTime getClosedAt() { return closedAt; }
}

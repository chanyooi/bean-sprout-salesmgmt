package com.example.salesmgmt.entity;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.domain.RouteCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "vendor_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vendor_profiles_vendor",
                columnNames = "vendor_id"
        )
)
public class VendorProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_code", nullable = false, length = 10)
    private RouteCode routeCode = RouteCode.NONE;

    @Column(name = "route_order")
    private Integer routeOrder;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "phone", length = 100)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_cycle", nullable = false, length = 20)
    private PaymentCycle paymentCycle = PaymentCycle.MONTHLY;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    protected VendorProfileEntity() {
    }

    public VendorProfileEntity(VendorEntity vendor) {
        this.vendor = vendor;
    }

    public Long getId() {
        return id;
    }

    public VendorEntity getVendor() {
        return vendor;
    }

    public boolean isActive() {
        return active;
    }

    public RouteCode getRouteCode() {
        return routeCode;
    }

    public Integer getRouteOrder() {
        return routeOrder;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public PaymentCycle getPaymentCycle() {
        return paymentCycle;
    }

    public String getMemo() {
        return memo;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void update(
            boolean active,
            RouteCode routeCode,
            Integer routeOrder,
            String address,
            String phone,
            PaymentCycle paymentCycle,
            String memo,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.active = active;
        this.routeCode = routeCode == null ? RouteCode.NONE : routeCode;
        this.routeOrder = this.routeCode == RouteCode.NONE ? null : routeOrder;
        this.address = blankToNull(address);
        this.phone = blankToNull(phone);
        this.paymentCycle = paymentCycle == null
                ? PaymentCycle.MONTHLY
                : paymentCycle;
        this.memo = blankToNull(memo);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

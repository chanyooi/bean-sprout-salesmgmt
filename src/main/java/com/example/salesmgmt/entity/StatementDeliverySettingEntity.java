package com.example.salesmgmt.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "statement_delivery_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_statement_delivery_vendor",
                columnNames = "vendor_id"
        )
)
public class StatementDeliverySettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "phone", length = 100)
    private String phone;

    @Column(name = "memo", length = 500)
    private String memo;

    protected StatementDeliverySettingEntity() {}

    public StatementDeliverySettingEntity(
            VendorEntity vendor,
            String phone,
            String memo
    ) {
        this.vendor = vendor;
        update(phone, memo);
    }

    public Long getId() { return id; }
    public VendorEntity getVendor() { return vendor; }
    public String getPhone() { return phone; }
    public String getMemo() { return memo; }

    public void update(String phone, String memo) {
        this.phone = blankToNull(phone);
        this.memo = blankToNull(memo);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}

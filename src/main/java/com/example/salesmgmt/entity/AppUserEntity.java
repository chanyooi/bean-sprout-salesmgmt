package com.example.salesmgmt.entity;

import com.example.salesmgmt.domain.AppUserRole;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "app_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_app_users_username",
                columnNames = "username"
        )
)
public class AppUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "username",
            nullable = false,
            length = 60
    )
    private String username;

    @Column(
            name = "password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;

    @Column(
            name = "display_name",
            nullable = false,
            length = 80
    )
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "role",
            nullable = false,
            length = 20
    )
    private AppUserRole role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AppUserEntity() {
    }

    public AppUserEntity(
            String username,
            String passwordHash,
            String displayName,
            AppUserRole role
    ) {
        this.username = normalizeUsername(username);
        this.passwordHash = passwordHash;
        this.displayName = normalizeDisplayName(
                displayName,
                username
        );
        this.role = role;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AppUserRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static String normalizeUsername(
            String username
    ) {
        return username == null
                ? null
                : username.trim().toLowerCase();
    }

    private static String normalizeDisplayName(
            String displayName,
            String username
    ) {
        if (
                displayName == null
                        || displayName.isBlank()
        ) {
            return username == null
                    ? "사용자"
                    : username.trim();
        }

        return displayName.trim();
    }
}

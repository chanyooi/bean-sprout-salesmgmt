package com.example.salesmgmt.repository;

import com.example.salesmgmt.domain.AppUserRole;
import com.example.salesmgmt.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository
        extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByUsername(
            String username
    );

    boolean existsByUsername(String username);

    long countByRoleAndEnabledTrue(AppUserRole role);

    List<AppUserEntity> findAllByOrderByCreatedAtAsc();
}

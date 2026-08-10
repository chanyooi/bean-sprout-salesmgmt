package com.example.salesmgmt.security;

import com.example.salesmgmt.entity.AppUserEntity;
import com.example.salesmgmt.repository.AppUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DatabaseUserDetailsService
        implements UserDetailsService {

    private final AppUserRepository repository;

    public DatabaseUserDetailsService(
            AppUserRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(
            String username
    ) throws UsernameNotFoundException {

        String normalized =
                username == null
                        ? ""
                        : username
                        .trim()
                        .toLowerCase(Locale.ROOT);

        AppUserEntity user = repository
                .findByUsername(normalized)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        return User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}

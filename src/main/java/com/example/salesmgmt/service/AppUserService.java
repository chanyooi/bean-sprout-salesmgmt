package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.AppUserRole;
import com.example.salesmgmt.entity.AppUserEntity;
import com.example.salesmgmt.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AppUserService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean setupRequired() {
        return repository.count() == 0;
    }

    @Transactional(readOnly = true)
    public List<AppUserEntity> findAll() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public AppUserEntity findByUsername(
            String username
    ) {
        return repository
                .findByUsername(normalizeUsername(username))
                .orElse(null);
    }

    @Transactional
    public void createInitialAdmin(
            String username,
            String displayName,
            String password,
            String passwordConfirm
    ) {
        if (repository.count() != 0) {
            throw new IllegalArgumentException(
                    "초기 설정은 이미 완료되었습니다."
            );
        }

        validatePassword(
                password,
                passwordConfirm
        );

        createInternal(
                username,
                displayName,
                password,
                AppUserRole.ADMIN
        );
    }

    @Transactional
    public void createUser(
            String username,
            String displayName,
            String password,
            String passwordConfirm,
            AppUserRole role
    ) {
        validatePassword(
                password,
                passwordConfirm
        );

        createInternal(
                username,
                displayName,
                password,
                role
        );
    }

    @Transactional
    public void resetPassword(
            Long userId,
            String password,
            String passwordConfirm
    ) {
        validatePassword(
                password,
                passwordConfirm
        );

        AppUserEntity user = repository
                .findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        user.changePassword(
                passwordEncoder.encode(password)
        );
    }

    @Transactional
    public void toggleEnabled(
            Long userId,
            String currentUsername
    ) {
        AppUserEntity user = repository
                .findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        if (
                user.getUsername().equals(
                        normalizeUsername(currentUsername)
                )
        ) {
            throw new IllegalArgumentException(
                    "현재 로그인 중인 본인 계정은 비활성화할 수 없습니다."
            );
        }

        boolean nextEnabled =
                !user.isEnabled();

        if (
                !nextEnabled
                        && user.getRole()
                        == AppUserRole.ADMIN
                        && repository
                        .countByRoleAndEnabledTrue(
                                AppUserRole.ADMIN
                        ) <= 1
        ) {
            throw new IllegalArgumentException(
                    "마지막 관리자 계정은 비활성화할 수 없습니다."
            );
        }

        user.setEnabled(nextEnabled);
    }

    private void createInternal(
            String username,
            String displayName,
            String password,
            AppUserRole role
    ) {
        String normalized =
                normalizeUsername(username);

        if (
                normalized == null
                        || normalized.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "아이디를 입력해주세요."
            );
        }

        if (!normalized.matches(
                "[a-z0-9._-]{3,30}"
        )) {
            throw new IllegalArgumentException(
                    "아이디는 영문 소문자·숫자·점·밑줄·하이픈으로 3~30자만 사용할 수 있습니다."
            );
        }

        if (repository.existsByUsername(normalized)) {
            throw new IllegalArgumentException(
                    "이미 사용 중인 아이디입니다."
            );
        }

        if (role == null) {
            throw new IllegalArgumentException(
                    "권한을 선택해주세요."
            );
        }

        repository.save(
                new AppUserEntity(
                        normalized,
                        passwordEncoder.encode(password),
                        displayName,
                        role
                )
        );
    }

    private void validatePassword(
            String password,
            String passwordConfirm
    ) {
        if (
                password == null
                        || password.length() < 8
        ) {
            throw new IllegalArgumentException(
                    "비밀번호는 8자 이상으로 설정해주세요."
            );
        }

        if (!password.equals(passwordConfirm)) {
            throw new IllegalArgumentException(
                    "비밀번호 확인이 일치하지 않습니다."
            );
        }
    }

    private String normalizeUsername(
            String username
    ) {
        return username == null
                ? null
                : username
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}

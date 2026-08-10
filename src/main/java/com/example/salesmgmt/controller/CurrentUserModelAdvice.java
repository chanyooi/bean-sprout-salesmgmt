package com.example.salesmgmt.controller;

import com.example.salesmgmt.entity.AppUserEntity;
import com.example.salesmgmt.service.AppUserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserModelAdvice {

    private final AppUserService appUserService;

    public CurrentUserModelAdvice(
            AppUserService appUserService
    ) {
        this.appUserService = appUserService;
    }

    @ModelAttribute("authenticated")
    public boolean authenticated(
            Authentication authentication
    ) {
        return isRealAuthentication(authentication);
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(
            Authentication authentication
    ) {
        if (!isRealAuthentication(authentication)) {
            return false;
        }

        return authentication
                .getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );
    }

    @ModelAttribute("currentUsername")
    public String currentUsername(
            Authentication authentication
    ) {
        return isRealAuthentication(authentication)
                ? authentication.getName()
                : null;
    }

    @ModelAttribute("currentUserDisplayName")
    public String currentUserDisplayName(
            Authentication authentication
    ) {
        if (!isRealAuthentication(authentication)) {
            return null;
        }

        AppUserEntity user =
                appUserService.findByUsername(
                        authentication.getName()
                );

        return user == null
                ? authentication.getName()
                : user.getDisplayName();
    }

    private boolean isRealAuthentication(
            Authentication authentication
    ) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication
                instanceof AnonymousAuthenticationToken);
    }
}

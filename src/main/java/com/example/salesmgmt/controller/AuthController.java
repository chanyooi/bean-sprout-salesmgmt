package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final AppUserService appUserService;

    public AuthController(
            AppUserService appUserService
    ) {
        this.appUserService = appUserService;
    }

    @GetMapping("/login")
    public String login(
            Authentication authentication,
            Model model
    ) {
        if (
                authentication != null
                        && authentication.isAuthenticated()
                        && !"anonymousUser".equals(
                                authentication.getPrincipal()
                        )
        ) {
            return "redirect:/";
        }

        model.addAttribute(
                "setupRequired",
                appUserService.setupRequired()
        );

        return "login";
    }

    @GetMapping("/setup")
    public String setup(
            HttpServletRequest request,
            Model model
    ) {
        if (!appUserService.setupRequired()) {
            return "redirect:/login";
        }

        if (!isLocalRequest(request)) {
            model.addAttribute(
                    "message",
                    "초기 관리자 계정 생성은 보안을 위해 이 컴퓨터의 localhost에서만 가능합니다."
            );
            return "setup-locked";
        }

        return "setup";
    }

    @PostMapping("/setup")
    public String createInitialAdmin(
            HttpServletRequest request,
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam String password,
            @RequestParam String passwordConfirm,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (!isLocalRequest(request)) {
            model.addAttribute(
                    "message",
                    "초기 관리자 계정 생성은 localhost에서만 가능합니다."
            );
            return "setup-locked";
        }

        try {
            appUserService.createInitialAdmin(
                    username,
                    displayName,
                    password,
                    passwordConfirm
            );

            redirectAttributes.addFlashAttribute(
                    "setupSuccess",
                    "관리자 계정을 만들었습니다. 방금 만든 아이디와 비밀번호로 로그인해주세요."
            );

            return "redirect:/login";
        } catch (RuntimeException exception) {
            model.addAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
            model.addAttribute(
                    "username",
                    username
            );
            model.addAttribute(
                    "displayName",
                    displayName
            );
            return "setup";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    private boolean isLocalRequest(
            HttpServletRequest request
    ) {
        String remoteAddress =
                request.getRemoteAddr();

        return "127.0.0.1".equals(remoteAddress)
                || "::1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1"
                .equals(remoteAddress);
    }
}

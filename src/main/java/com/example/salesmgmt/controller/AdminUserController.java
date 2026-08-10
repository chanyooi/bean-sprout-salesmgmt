package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.AppUserRole;
import com.example.salesmgmt.service.AppUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AppUserService appUserService;

    public AdminUserController(
            AppUserService appUserService
    ) {
        this.appUserService = appUserService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute(
                "users",
                appUserService.findAll()
        );
        model.addAttribute(
                "roles",
                AppUserRole.values()
        );

        return "admin-users";
    }

    @PostMapping
    public String create(
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam String password,
            @RequestParam String passwordConfirm,
            @RequestParam AppUserRole role,
            RedirectAttributes redirectAttributes
    ) {
        try {
            appUserService.createUser(
                    username,
                    displayName,
                    password,
                    passwordConfirm,
                    role
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "새 사용자 계정을 만들었습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(
            @PathVariable Long id,
            @RequestParam String password,
            @RequestParam String passwordConfirm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            appUserService.resetPassword(
                    id,
                    password,
                    passwordConfirm
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "비밀번호를 변경했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/toggle")
    public String toggleEnabled(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            appUserService.toggleEnabled(
                    id,
                    authentication.getName()
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "계정 사용 상태를 변경했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/admin/users";
    }
}

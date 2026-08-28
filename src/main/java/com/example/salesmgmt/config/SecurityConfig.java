package com.example.salesmgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers(
                                        "/css/**",
                                        "/login",
                                        "/setup",
                                        "/access-denied",
                                        "/error",
                                        "/favicon.ico"
                                )
                                .permitAll()

                                // 관리자만 사용할 위험/설정 기능
                                .requestMatchers(
                                        "/admin/**",
                                        "/upload/**",
                                        "/excel/import/**",
                                        "/daily-entry/**",
                                        "/input-template/**",
                                        "/prices/**",
                                        "/promotions/**"
                                )
                                .hasRole("ADMIN")

                                // 화면 URL과 실제 저장 URL이 다른 관리자 작업도 명시적으로 보호합니다.
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/vendor-management/*/prices",
                                        "/vendor-management/*/prices/*"
                                )
                                .hasRole("ADMIN")

                                // 나머지 업무 화면은 로그인 사용자 모두 허용
                                .anyRequest()
                                .authenticated()
                )
                .formLogin(form ->
                        form
                                .loginPage("/login")
                                .loginProcessingUrl("/login")
                                .defaultSuccessUrl("/", true)
                                .failureUrl("/login?error")
                                .permitAll()
                )
                .logout(logout ->
                        logout
                                .logoutUrl("/logout")
                                .logoutSuccessUrl(
                                        "/login?logout"
                                )
                                .invalidateHttpSession(true)
                                .clearAuthentication(true)
                                .deleteCookies("JSESSIONID")
                                .permitAll()
                )
                .exceptionHandling(exceptions ->
                        exceptions.accessDeniedPage(
                                "/access-denied"
                        )
                );

        return http.build();
    }
}

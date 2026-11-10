package com.clinicready.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinicready.security.dto.CurrentUserResponse;
import com.clinicready.security.dto.LoginRequest;
import com.clinicready.security.dto.LoginResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String ACCESS_TOKEN_COOKIE = "cr_access_token";
    static final String COOKIE_PATH = "/api";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        String token = result.token().value();
        long expiresIn = result.token().expiresInSeconds();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie(token, expiresIn).toString())
                .body(new LoginResponse(token, "Bearer", expiresIn, result.user()));
    }

    @GetMapping("/me")
    CurrentUserResponse me() {
        return authService.me();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie("", 0).toString())
                .build();
    }

    private ResponseCookie accessTokenCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}

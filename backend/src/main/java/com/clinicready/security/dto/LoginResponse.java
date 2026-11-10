package com.clinicready.security.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, CurrentUserResponse user) {
}

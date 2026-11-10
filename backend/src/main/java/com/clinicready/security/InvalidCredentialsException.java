package com.clinicready.security;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

class InvalidCredentialsException extends ApiException {

    InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
    }
}

package com.clinicready.security;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

class UnauthenticatedException extends ApiException {

    UnauthenticatedException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }
}

package com.clinicready.security;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

/** The user is authenticated but their role may not perform the action. */
public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not allowed");
    }
}

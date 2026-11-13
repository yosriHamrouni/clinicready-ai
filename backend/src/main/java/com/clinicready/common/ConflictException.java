package com.clinicready.common;

import org.springframework.http.HttpStatus;

/** The request is valid but clashes with the current state of the resource. */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}

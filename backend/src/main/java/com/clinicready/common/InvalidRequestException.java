package com.clinicready.common;

import org.springframework.http.HttpStatus;

/** A request that passed bean validation but breaks a rule that depends on several fields. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}

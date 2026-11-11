package com.clinicready.common;

import org.springframework.http.HttpStatus;

/**
 * The resource does not exist or belongs to another clinic. Both cases look the same, so callers
 * cannot probe for other clinics' ids.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String resource) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found");
    }
}

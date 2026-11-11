package com.clinicready.document;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

/** The upload request itself is unusable, for example an empty file. */
public class InvalidFileException extends ApiException {

    public InvalidFileException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}

package com.clinicready.document;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

/** Two uploads of the same patient and category raced for the same version number. */
public class UploadConflictException extends ApiException {

    public UploadConflictException() {
        super(HttpStatus.CONFLICT, "UPLOAD_CONFLICT", "Another upload is in progress, please retry");
    }
}

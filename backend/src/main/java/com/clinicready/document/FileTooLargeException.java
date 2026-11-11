package com.clinicready.document;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

/** The uploaded file is bigger than {@code clinicready.documents.max-size}. */
public class FileTooLargeException extends ApiException {

    public FileTooLargeException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the maximum allowed size");
    }
}

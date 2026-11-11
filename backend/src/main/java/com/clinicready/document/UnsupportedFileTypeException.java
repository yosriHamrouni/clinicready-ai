package com.clinicready.document;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

/** Only PDF, JPEG and PNG files are accepted, judged by content and not by file name. */
public class UnsupportedFileTypeException extends ApiException {

    public UnsupportedFileTypeException() {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                "Only PDF, JPEG and PNG files are accepted");
    }
}

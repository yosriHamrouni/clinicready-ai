package com.clinicready.common;

/**
 * Error body returned by every API endpoint. See docs/api/auth.md.
 */
public record ApiError(String code, String message, Object details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}

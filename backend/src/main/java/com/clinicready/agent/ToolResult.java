package com.clinicready.agent;

import com.clinicready.common.ApiException;

/**
 * What every agent tool returns: a status code the model can branch on, a short message, and typed data
 * (null unless {@code OK}). Messages never carry patient identity details or document text.
 */
public record ToolResult<T>(ToolStatus status, String message, T data) {

    public enum ToolStatus {
        OK,
        /** Not in the user's clinic, or not visible to the user (another clinic is reported the same way). */
        NOT_FOUND,
        FORBIDDEN,
        INVALID_INPUT,
        /** The state does not allow it (illegal transition, missing or used approval). */
        CONFLICT,
        ERROR
    }

    public static <T> ToolResult<T> ok(T data) {
        return new ToolResult<>(ToolStatus.OK, "ok", data);
    }

    public static <T> ToolResult<T> invalid(String message) {
        return new ToolResult<>(ToolStatus.INVALID_INPUT, message, null);
    }

    static <T> ToolResult<T> of(ApiException e) {
        ToolStatus status = switch (e.status()) {
            case NOT_FOUND -> ToolStatus.NOT_FOUND;
            case FORBIDDEN -> ToolStatus.FORBIDDEN;
            case BAD_REQUEST -> ToolStatus.INVALID_INPUT;
            case CONFLICT -> ToolStatus.CONFLICT;
            default -> ToolStatus.ERROR;
        };
        return new ToolResult<>(status, e.getMessage(), null);
    }
}

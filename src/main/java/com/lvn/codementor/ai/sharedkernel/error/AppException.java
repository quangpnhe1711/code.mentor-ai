package com.lvn.codementor.ai.sharedkernel.error;

import java.util.List;

/**
 * Base application exception. Application code throws this (or a subclass) carrying a stable
 * {@link ErrorCode}; the web layer translates it into the API error envelope (doc 08 §1: custom
 * exceptions only — never generic or HTTP-typed exceptions in the domain/data layers).
 */
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final List<String> details;

    public AppException(ErrorCode code, String message) {
        this(code, message, List.of(), null);
    }

    public AppException(ErrorCode code, String message, Throwable cause) {
        this(code, message, List.of(), cause);
    }

    public AppException(ErrorCode code, String message, List<String> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<String> details() {
        return details;
    }
}

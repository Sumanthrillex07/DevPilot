package com.devpilot.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation requires an authenticated user but none is present
 * in the security context. The {@code @ResponseStatus} annotation causes Spring MVC
 * to map this exception to HTTP 401 automatically.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

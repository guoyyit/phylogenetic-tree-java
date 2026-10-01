package com.github.guoyyit.phylogenetictreejava.web;

import com.github.guoyyit.phylogenetictreejava.model.InputValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps domain validation failures to HTTP 400 responses carrying the
 * machine-readable rejection reason.
 */
@RestControllerAdvice
public final class ApiExceptionHandler {

    @ExceptionHandler(InputValidationException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(
            InputValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "reason", exception.reason().name(),
                        "message", exception.getMessage() == null ? ""
                                : exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "reason", "INVALID_REQUEST",
                        "message", exception.getMessage() == null ? ""
                                : exception.getMessage()));
    }
}

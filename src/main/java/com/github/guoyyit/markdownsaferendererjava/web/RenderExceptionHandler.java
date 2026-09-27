package com.github.guoyyit.markdownsaferendererjava.web;

import com.github.guoyyit.markdownsaferendererjava.limits.LimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps render failures to distinguishable HTTP responses.
 */
@RestControllerAdvice
public class RenderExceptionHandler {

    @ExceptionHandler(LimitExceededException.class)
    public ResponseEntity<ErrorResponse> onLimit(LimitExceededException e) {
        HttpStatus status = switch (e.getReason()) {
            case INPUT_TOO_LONG -> HttpStatus.PAYLOAD_TOO_LARGE;
            case PROCESSING_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(e.getReason().name(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> onBadOptions(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_OPTIONS", e.getMessage()));
    }
}

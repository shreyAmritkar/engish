package com.stylecommunicator.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Error response shape ──────────────────────────────────────────────────

    public record ErrorResponse(
            int status,
            String error,
            String message,
            List<String> details,
            String path,
            Instant timestamp
    ) {
        static ErrorResponse of(HttpStatus status, String message, String path) {
            return new ErrorResponse(status.value(), status.getReasonPhrase(), message, List.of(), path, Instant.now());
        }

        static ErrorResponse of(HttpStatus status, String message, List<String> details, String path) {
            return new ErrorResponse(status.value(), status.getReasonPhrase(), message, details, path, Instant.now());
        }
    }

    // ── Explicit app errors (ResponseStatusException) ─────────────────────────

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        // Don't log 404s or 401s as errors — they're expected
        if (status.is5xxServerError()) {
            log.error("ResponseStatusException [{}] {}", status, ex.getReason(), ex);
        } else if (status != HttpStatus.NOT_FOUND && status != HttpStatus.UNAUTHORIZED) {
            log.warn("ResponseStatusException [{}] {}", status, ex.getReason());
        }

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(), req.getRequestURI()));
    }

    // ── Bean validation (@Valid) ───────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", details, req.getRequestURI()));
    }

    // ── Constraint violations ─────────────────────────────────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest req) {

        List<String> details = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.toList());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", details, req.getRequestURI()));
    }

    // ── Malformed JSON body ───────────────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Malformed or missing request body", req.getRequestURI()));
    }

    // ── Wrong path variable type (e.g. non-UUID) ──────────────────────────────

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, msg, req.getRequestURI()));
    }

    // ── Missing required query param ──────────────────────────────────────────

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), req.getRequestURI()));
    }

    // ── Spring Security: 401 ─────────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(
            AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Authentication required", req.getRequestURI()));
    }

    // ── Spring Security: 403 ─────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN, "You don't have permission to access this resource", req.getRequestURI()));
    }

    // ── LLM / service errors ──────────────────────────────────────────────────

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleLlmUnavailable(
            LlmUnavailableException ex, HttpServletRequest req) {
        log.warn("LLM unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE,
                        "The AI service is temporarily unavailable. Please try again in a moment.",
                        req.getRequestURI()));
    }

    // ── Rate limit (app-level, not OpenRouter) ────────────────────────────────

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            RateLimitException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req.getRequestURI()));
    }

    // ── Catch-all — hide internal details from client ─────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. Please try again.",
                        req.getRequestURI()));
    }
}

package ru.practicum.ewm.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Collections;

@RestControllerAdvice
@Slf4j
public class ErrorHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(NotFoundException e) {
        log.error("Not found error: {}", e.getMessage());
        return new ApiError(
                "NOT_FOUND",
                "The required object was not found.",
                e.getMessage(),
                LocalDateTime.now(),
                Collections.emptyList()
        );
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(ConflictException e) {
        log.error("Conflict error: {}", e.getMessage());
        return new ApiError(
                "CONFLICT",
                "Integrity constraint has been violated.",
                e.getMessage(),
                LocalDateTime.now(),
                Collections.emptyList()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException e) {
        log.error("Validation error: {}", e.getMessage());
        return new ApiError(
                "BAD_REQUEST",
                "Incorrectly made request.",
                "Validation failed: " + e.getBindingResult().getAllErrors(),
                LocalDateTime.now(),
                Collections.emptyList()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleException(Exception e) {
        log.error("Internal server error", e);
        return new ApiError(
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                e.getMessage(),
                LocalDateTime.now(),
                Collections.emptyList()
        );
    }
}
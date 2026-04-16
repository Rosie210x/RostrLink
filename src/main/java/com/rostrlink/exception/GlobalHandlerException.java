package com.rostrlink.exception;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.rostrlink.api.JsonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalHandlerException {


    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<JsonResponse<Object>> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity.notFound().build();
    }
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<JsonResponse<Object>> handleConflict(Exception ex) {
        JsonResponse<Object> body = JsonResponse.builder().status("error")
                .message(ex.getMessage())
                .errorCode("CONFLICT")
                .data(null)
                .build();
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<JsonResponse<Object>> handleValidation(Exception ex) {
        JsonResponse<Object> body = JsonResponse.builder().status("error")
                .message(ex.getMessage())
                .errorCode("VALIDATION_ERROR")
                .data(null)
                .build();
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<JsonResponse<Object>> handleMethodArgNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, replacement) -> existing
                ));
        JsonResponse<Object> body = JsonResponse.builder()
                .status("error")
                .message("Validation failed")
                .errorCode("VALIDATION_ERROR")
                .data(errors)
                .build();
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    public ResponseEntity<JsonResponse<Object>> handleException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        JsonResponse<Object> body = JsonResponse.builder()
                .status("error")
                .message("Internal server error")
                .errorCode("INTERNAL_SERVER_ERROR")
                .data(null)
                .build();
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }



}


package com.projet.sigrh.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("status", ex.getStatus().value(), "message", ex.getMessage()));
    }

    /** Une contrainte de la base (CHECK, UNIQUE, FK) a refusé l'écriture. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("status", 400, "message", "Donnée refusée par la base : " + ex.getMostSpecificCause().getMessage()));
    }
}
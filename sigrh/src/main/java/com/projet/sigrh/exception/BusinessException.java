package com.projet.sigrh.exception;

import org.springframework.http.HttpStatus;

/** Erreur de règle de gestion : le message est renvoyé tel quel au navigateur. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
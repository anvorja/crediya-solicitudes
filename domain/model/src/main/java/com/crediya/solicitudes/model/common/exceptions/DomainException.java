package com.crediya.solicitudes.model.common.exceptions;

/**
 * Base de las excepciones de negocio. Lleva un codigo estable que el entry point traduce
 * a una respuesta HTTP; el dominio no conoce HTTP.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

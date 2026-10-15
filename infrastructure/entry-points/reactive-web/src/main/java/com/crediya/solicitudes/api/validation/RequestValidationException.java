package com.crediya.solicitudes.api.validation;

import java.util.Map;

/**
 * Error de entrada detectado en el entry point (antes de llegar al dominio).
 */
public class RequestValidationException extends RuntimeException {

    private final transient Map<String, String> detalles;

    public RequestValidationException(Map<String, String> detalles) {
        super("La peticion contiene datos invalidos");
        this.detalles = Map.copyOf(detalles);
    }

    public Map<String, String> getDetalles() {
        return detalles;
    }
}

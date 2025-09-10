// domain/model/src/main/java/com/crediya/solicitudes/model/solicitud/exception/TipoPrestamoNoValidoException.java
package com.crediya.solicitudes.model.solicitud.exception;

/**
 * Excepción del dominio cuando el tipo de préstamo no es válido
 * HU2: El sistema debe validar que el tipo de préstamo seleccionado sea uno de los tipos existentes
 */
public class TipoPrestamoNoValidoException extends RuntimeException {

    /**
     * Constructor con mensaje personalizado
     */
    public TipoPrestamoNoValidoException(String message) {
        super(message);
    }

    /**
     * Constructor con mensaje y causa
     */
    public TipoPrestamoNoValidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
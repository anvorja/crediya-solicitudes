package com.crediya.solicitudes.model.seguridad;

import java.util.UUID;

/**
 * Quien esta invocando la operacion, segun el token validado en el entry point.
 */
public record UsuarioAutenticado(UUID id, String documentoIdentidad, String email, Rol rol) {

    public boolean esCliente() {
        return rol == Rol.CLIENTE;
    }
}

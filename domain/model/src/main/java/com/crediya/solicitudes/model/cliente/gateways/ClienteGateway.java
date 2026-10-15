package com.crediya.solicitudes.model.cliente.gateways;

import com.crediya.solicitudes.model.cliente.Cliente;
import reactor.core.publisher.Mono;

public interface ClienteGateway {

    /**
     * Devuelve el cliente o un Mono vacio si no existe.
     */
    Mono<Cliente> buscarPorDocumento(String documentoIdentidad);
}

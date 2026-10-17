package com.crediya.solicitudes.model.cliente.gateways;

import com.crediya.solicitudes.model.cliente.Cliente;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface ClienteGateway {

    /**
     * Devuelve el cliente o un Mono vacio si no existe.
     */
    Mono<Cliente> buscarPorDocumento(String documentoIdentidad);

    /**
     * Busca varios clientes por correo en una sola consulta; los que no existen se omiten.
     */
    Flux<Cliente> buscarPorEmails(Collection<String> emails);
}

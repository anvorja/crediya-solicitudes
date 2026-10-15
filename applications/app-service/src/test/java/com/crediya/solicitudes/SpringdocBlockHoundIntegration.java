package com.crediya.solicitudes;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * springdoc escanea el classpath (lectura de archivos) en varios metodos la primera vez que se pide /v3/api-docs.
 * Es un costo unico de la libreria y no ocurre en el flujo de negocio, por eso se permite aqui y solo en pruebas.
 */
public class SpringdocBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("org.springdoc.api.AbstractOpenApiResource", "getOpenApi");
    }
}

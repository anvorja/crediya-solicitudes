package com.crediya.solicitudes;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * La autenticacion SCRAM de PostgreSQL genera un nonce con SecureRandom, que lee /dev/urandom la primera vez que el proceso
 * lo necesita. Ocurre dentro del driver al abrir una conexion nueva, no en el flujo de negocio, y solo si ningun test anterior
 * sembro ya el generador (por eso fallaba de forma intermitente en CI). Se permite aqui y solo en pruebas.
 */
public class ScramBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("com.ongres.scram.common.util.CryptoUtil", "nonce");
    }
}

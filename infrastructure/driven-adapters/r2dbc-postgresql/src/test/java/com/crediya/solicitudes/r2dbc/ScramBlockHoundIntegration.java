package com.crediya.solicitudes.r2dbc;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * La autenticacion SCRAM de PostgreSQL genera un nonce con SecureRandom, que lee /dev/urandom la primera vez. Ocurre una vez por
 * conexion nueva, dentro del driver, y no en el flujo de negocio; por eso se permite aqui y solo en pruebas.
 */
public class ScramBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("com.ongres.scram.common.util.CryptoUtil", "nonce");
    }
}

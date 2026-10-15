package com.crediya.solicitudes.model.evento;

import com.crediya.solicitudes.model.solicitud.CuotaPlanDePago;
import com.crediya.solicitudes.model.solicitud.Solicitud;

import java.time.Instant;
import java.util.List;

/**
 * Una solicitud recibio su decision final (Aprobada o Rechazada): hay que avisarle al solicitante.
 * {@code nombreCliente} puede ser nulo si AUTENTICACION no lo conoce; {@code planDePago} puede estar vacio.
 */
public record SolicitudDecidida(Instant ocurridoEn, Solicitud solicitud, String nombreCliente, List<CuotaPlanDePago> planDePago)
        implements EventoDeDominio {

    public SolicitudDecidida {
        planDePago = planDePago == null ? List.of() : List.copyOf(planDePago);
    }
}

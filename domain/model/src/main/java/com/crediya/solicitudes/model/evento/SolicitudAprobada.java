package com.crediya.solicitudes.model.evento;

import com.crediya.solicitudes.model.solicitud.Solicitud;

import java.time.Instant;

/**
 * Aviso para el servicio de reportes: una solicitud quedo aprobada. Solo lleva lo necesario para contarla, sin datos personales.
 */
public record SolicitudAprobada(Instant ocurridoEn, Solicitud solicitud) implements EventoDeDominio {
}

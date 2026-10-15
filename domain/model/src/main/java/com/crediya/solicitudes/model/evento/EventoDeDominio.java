package com.crediya.solicitudes.model.evento;

/**
 * Hechos del negocio que otros servicios deben conocer. Se guardan en el outbox en la misma transaccion que el cambio que
 * los origina y se publican despues (ver {@code EventoSalienteRepository}).
 */
public sealed interface EventoDeDominio permits SolicitudDecidida {
}

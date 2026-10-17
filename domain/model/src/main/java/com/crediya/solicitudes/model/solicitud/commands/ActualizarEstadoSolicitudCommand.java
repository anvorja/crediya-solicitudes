package com.crediya.solicitudes.model.solicitud.commands;

/**
 * Datos crudos para decidir una solicitud. Se validan en el dominio.
 */
public record ActualizarEstadoSolicitudCommand(String idSolicitud, String estado) {
}

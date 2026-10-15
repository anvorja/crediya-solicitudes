package com.crediya.solicitudes.model.solicitud;

public enum EstadoSolicitud {
    PENDIENTE_REVISION("Pendiente de revision"),
    REVISION_MANUAL("Revision manual"),
    APROBADA("Aprobada"),
    RECHAZADA("Rechazada");

    private final String descripcion;

    EstadoSolicitud(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}

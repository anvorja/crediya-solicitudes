package com.crediya.solicitudes.model.evento;

import java.util.UUID;

/**
 * Un evento ya guardado en el outbox, listo para publicarse. {@code payload} es el mensaje tal como viajara (JSON).
 */
public record EventoSaliente(UUID id, Destino destino, String payload, int intentos) {
}

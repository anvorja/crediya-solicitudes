package com.crediya.solicitudes.model.evento;

import com.crediya.solicitudes.model.solicitud.PrestamoAprobado;
import com.crediya.solicitudes.model.solicitud.Solicitud;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Una solicitud de un tipo con validacion automatica fue registrada: hay que evaluar la capacidad de endeudamiento del
 * solicitante. Lleva todo lo que el evaluador necesita (salario, deuda vigente) para que no tenga que consultar otros servicios.
 */
public record ValidacionAutomaticaSolicitada(Instant ocurridoEn,
                                             Solicitud solicitud,
                                             String nombreCliente,
                                             BigDecimal salarioBase,
                                             List<PrestamoAprobado> prestamosAprobados) implements EventoDeDominio {

    public ValidacionAutomaticaSolicitada {
        prestamosAprobados = prestamosAprobados == null ? List.of() : List.copyOf(prestamosAprobados);
    }
}

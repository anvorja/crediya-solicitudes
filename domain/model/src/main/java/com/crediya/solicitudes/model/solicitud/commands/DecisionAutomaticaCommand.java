package com.crediya.solicitudes.model.solicitud.commands;

import com.crediya.solicitudes.model.solicitud.CuotaPlanDePago;

import java.util.List;

/**
 * Resultado de la validacion automatica, tal como llega del evaluador. {@code nombreCliente} y {@code planDePago} son opcionales.
 */
public record DecisionAutomaticaCommand(String idSolicitud, String decision, String nombreCliente, List<CuotaPlanDePago> planDePago) {

    public DecisionAutomaticaCommand {
        planDePago = planDePago == null ? List.of() : List.copyOf(planDePago);
    }
}

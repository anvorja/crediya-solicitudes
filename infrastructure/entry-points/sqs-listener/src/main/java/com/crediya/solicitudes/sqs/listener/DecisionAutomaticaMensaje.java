package com.crediya.solicitudes.sqs.listener;

import com.crediya.solicitudes.model.solicitud.CuotaPlanDePago;
import com.crediya.solicitudes.model.solicitud.commands.DecisionAutomaticaCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Contrato del mensaje que publica la Lambda de capacidad de endeudamiento (version 1). Es un lector tolerante: ignora los
 * campos que no conoce y solo exige los que necesita.
 * <pre>
 * {"version":1,"id_evento":"..","tipo":"DECISION_AUTOMATICA","id_solicitud":"..","decision":"APROBADA",
 *  "destinatario":{"email":"..","nombre":".."},"plan_de_pago":[{"numero":1,"cuota":..,"capital":..,"interes":..,"saldo":..}]}
 * </pre>
 */
final class DecisionAutomaticaMensaje {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int VERSION_SOPORTADA = 1;

    private DecisionAutomaticaMensaje() {
    }

    /**
     * @throws IllegalArgumentException si el mensaje no es JSON o no cumple el contrato
     */
    static DecisionAutomaticaCommand aComando(String cuerpo) {
        JsonNode raiz = leer(cuerpo);
        if (raiz.path("version").asInt(VERSION_SOPORTADA) != VERSION_SOPORTADA) {
            throw new IllegalArgumentException("Version del mensaje no soportada: " + raiz.path("version").asText());
        }
        String idSolicitud = texto(raiz, "id_solicitud");
        String decision = texto(raiz, "decision");
        if (idSolicitud == null || decision == null) {
            throw new IllegalArgumentException("El mensaje debe traer id_solicitud y decision");
        }
        return new DecisionAutomaticaCommand(idSolicitud, decision, texto(raiz.path("destinatario"), "nombre"), plan(raiz.path("plan_de_pago")));
    }

    private static JsonNode leer(String cuerpo) {
        try {
            JsonNode raiz = MAPPER.readTree(cuerpo);
            if (raiz == null || !raiz.isObject()) {
                throw new IllegalArgumentException("El mensaje no es un objeto JSON");
            }
            return raiz;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("El mensaje no es JSON valido", e);
        }
    }

    private static List<CuotaPlanDePago> plan(JsonNode nodo) {
        List<CuotaPlanDePago> cuotas = new ArrayList<>();
        if (nodo.isArray()) {
            for (JsonNode fila : nodo) {
                cuotas.add(new CuotaPlanDePago(fila.path("numero").asInt(), dinero(fila, "cuota"), dinero(fila, "capital"),
                        dinero(fila, "interes"), dinero(fila, "saldo")));
            }
        }
        return cuotas;
    }

    private static BigDecimal dinero(JsonNode fila, String campo) {
        JsonNode valor = fila.path(campo);
        if (!valor.isNumber()) {
            throw new IllegalArgumentException("El campo " + campo + " del plan de pago debe ser numerico");
        }
        return valor.decimalValue();
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        return valor.isTextual() && !valor.asText().isBlank() ? valor.asText() : null;
    }
}

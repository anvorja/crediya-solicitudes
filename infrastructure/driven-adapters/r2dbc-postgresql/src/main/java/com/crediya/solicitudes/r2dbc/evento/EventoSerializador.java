package com.crediya.solicitudes.r2dbc.evento;

import com.crediya.solicitudes.model.evento.Destino;
import com.crediya.solicitudes.model.evento.EventoDeDominio;
import com.crediya.solicitudes.model.evento.SolicitudAprobada;
import com.crediya.solicitudes.model.evento.SolicitudDecidida;
import com.crediya.solicitudes.model.evento.ValidacionAutomaticaSolicitada;
import com.crediya.solicitudes.model.solicitud.CuotaPlanDePago;
import com.crediya.solicitudes.model.solicitud.PrestamoAprobado;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Traduce los eventos del dominio al contrato JSON que consumen las Lambdas (snake_case, version 1). El campo
 * {@code id_evento} lo agrega la base de datos al insertar la fila, para que coincida con el id del outbox.
 */
final class EventoSerializador {

    private static final int VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventoSerializador() {
    }

    static Destino destinoDe(EventoDeDominio evento) {
        return switch (evento) {
            case SolicitudDecidida e -> Destino.NOTIFICACIONES;
            case SolicitudAprobada e -> Destino.REPORTES;
            case ValidacionAutomaticaSolicitada e -> Destino.VALIDACION;
        };
    }

    static String aJson(EventoDeDominio evento) {
        return switch (evento) {
            case SolicitudDecidida e -> solicitudDecidida(e).toString();
            case SolicitudAprobada e -> solicitudAprobada(e).toString();
            case ValidacionAutomaticaSolicitada e -> validacionSolicitada(e).toString();
        };
    }

    private static ObjectNode validacionSolicitada(ValidacionAutomaticaSolicitada evento) {
        var solicitud = evento.solicitud();
        ObjectNode raiz = MAPPER.createObjectNode();
        raiz.put("version", VERSION);
        raiz.put("tipo", "VALIDACION_AUTOMATICA_SOLICITADA");
        raiz.put("ocurrido_en", evento.ocurridoEn().toString());

        ObjectNode datos = raiz.putObject("solicitud");
        datos.put("id_solicitud", solicitud.id().toString());
        datos.put("monto", solicitud.monto());
        datos.put("plazo", solicitud.plazo());
        datos.put("tasa_interes", solicitud.tipoPrestamo().tasaInteres());
        datos.put("tipo_prestamo", solicitud.tipoPrestamo().nombre());

        ObjectNode solicitante = raiz.putObject("solicitante");
        solicitante.put("email", solicitud.email());
        if (evento.nombreCliente() != null) {
            solicitante.put("nombre", evento.nombreCliente());
        }
        solicitante.put("salario_base", evento.salarioBase());

        ArrayNode aprobados = raiz.putArray("prestamos_aprobados");
        for (PrestamoAprobado prestamo : evento.prestamosAprobados()) {
            ObjectNode fila = aprobados.addObject();
            fila.put("monto", prestamo.monto());
            fila.put("plazo", prestamo.plazo());
            fila.put("tasa_interes", prestamo.tasaInteres());
        }
        return raiz;
    }

    private static ObjectNode solicitudAprobada(SolicitudAprobada evento) {
        var solicitud = evento.solicitud();
        ObjectNode raiz = MAPPER.createObjectNode();
        raiz.put("version", VERSION);
        raiz.put("tipo", "SOLICITUD_APROBADA");
        raiz.put("ocurrido_en", evento.ocurridoEn().toString());
        ObjectNode datos = raiz.putObject("solicitud");
        datos.put("id_solicitud", solicitud.id().toString());
        datos.put("monto", solicitud.monto());
        datos.put("plazo", solicitud.plazo());
        datos.put("tipo_prestamo", solicitud.tipoPrestamo().nombre());
        return raiz;
    }

    private static ObjectNode solicitudDecidida(SolicitudDecidida evento) {
        var solicitud = evento.solicitud();
        ObjectNode raiz = MAPPER.createObjectNode();
        raiz.put("version", VERSION);
        raiz.put("tipo", "SOLICITUD_DECIDIDA");
        raiz.put("ocurrido_en", evento.ocurridoEn().toString());

        ObjectNode destinatario = raiz.putObject("destinatario");
        destinatario.put("email", solicitud.email());
        if (evento.nombreCliente() != null) {
            destinatario.put("nombre", evento.nombreCliente());
        }

        ObjectNode datos = raiz.putObject("solicitud");
        datos.put("id_solicitud", solicitud.id().toString());
        datos.put("estado", solicitud.estado().name());
        datos.put("monto", solicitud.monto());
        datos.put("plazo", solicitud.plazo());
        datos.put("tipo_prestamo", solicitud.tipoPrestamo().nombre());

        if (!evento.planDePago().isEmpty()) {
            ArrayNode plan = raiz.putArray("plan_de_pago");
            for (CuotaPlanDePago cuota : evento.planDePago()) {
                ObjectNode fila = plan.addObject();
                fila.put("numero", cuota.numero());
                fila.put("cuota", cuota.cuota());
                fila.put("capital", cuota.capital());
                fila.put("interes", cuota.interes());
                fila.put("saldo", cuota.saldo());
            }
        }
        return raiz;
    }
}

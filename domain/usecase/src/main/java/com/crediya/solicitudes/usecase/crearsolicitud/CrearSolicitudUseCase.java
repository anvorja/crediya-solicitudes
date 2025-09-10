// domain/usecase/src/main/java/com/crediya/solicitudes/usecase/crearsolicitud/CrearSolicitudUseCase.java
package com.crediya.solicitudes.usecase.crearsolicitud;

import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.exception.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exception.SolicitudDuplicadaException;
import com.crediya.solicitudes.model.solicitud.exception.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Use Case para crear solicitudes de préstamo (HU2)
 * Implementa validaciones de negocio y flujo reactivo completo
 * CUMPLE: Campos final para concurrencia segura (Rule_2.5)
 */
@RequiredArgsConstructor
public class CrearSolicitudUseCase {

    // ✅ CORRECTO: Campo final para evitar concurrencia (ArchUnit Rule_2.5)
    private final SolicitudRepository solicitudRepository;

    /**
     * Ejecuta el caso de uso de creación de solicitud
     * HU2: Como cliente, quiero enviar mi solicitud de préstamo
     * FLUJO REACTIVO: Usa operadores reactivos para mantener la cadena
     */
    public Mono<Solicitud> ejecutar(Solicitud solicitud) {
        return Mono.just(solicitud)
                .transform(this::validarDatosBasicos)
                .transform(this::validarTipoPrestamo)
                .transform(this::validarSolicitudDuplicada)
                .transform(this::establecerEstadoInicial)
                .flatMap(solicitudRepository::guardar);
    }

    // ============================================
    // MÉTODOS PRIVADOS DE VALIDACIÓN REACTIVA
    // ============================================

    /**
     * Validación 1: Datos básicos obligatorios
     * CUMPLE: filter + switchIfEmpty para validación reactiva
     */
    private Mono<Solicitud> validarDatosBasicos(Mono<Solicitud> solicitudMono) {
        return solicitudMono
                .filter(s -> s.getNumeroDocumento() != null && !s.getNumeroDocumento().trim().isEmpty())
                .switchIfEmpty(Mono.error(new DatosSolicitudInvalidosException("Número de documento es obligatorio")))
                .filter(s -> s.getNombres() != null && !s.getNombres().trim().isEmpty())
                .switchIfEmpty(Mono.error(new DatosSolicitudInvalidosException("Nombres son obligatorios")))
                .filter(s -> s.getApellidos() != null && !s.getApellidos().trim().isEmpty())
                .switchIfEmpty(Mono.error(new DatosSolicitudInvalidosException("Apellidos son obligatorios")))
                .filter(s -> s.getEmail() != null && !s.getEmail().trim().isEmpty())
                .switchIfEmpty(Mono.error(new DatosSolicitudInvalidosException("Email es obligatorio")))
                .filter(s -> s.getMontoSolicitado() != null && s.getMontoSolicitado().compareTo(java.math.BigDecimal.ZERO) > 0)
                .switchIfEmpty(Mono.error(new DatosSolicitudInvalidosException("Monto solicitado debe ser mayor a cero")));
    }

    /**
     * Validación 2: Tipo de préstamo válido
     * CUMPLE: Criterio de aceptación - sistema valida tipos existentes
     */
    private Mono<Solicitud> validarTipoPrestamo(Mono<Solicitud> solicitudMono) {
        return solicitudMono
                .flatMap(solicitud -> {
                    if (!esTipoPrestamoValido(solicitud.getTipoCredito())) {
                        return Mono.error(new TipoPrestamoNoValidoException(
                                "Tipo de crédito no válido: " + solicitud.getTipoCredito() +
                                        ". Tipos permitidos: PERSONAL, VEHICULO, VIVIENDA, EDUCATIVO"));
                    }
                    return Mono.just(solicitud);
                });
    }

    /**
     * Validación 3: No debe existir solicitud duplicada
     * CUMPLE: Criterio de aceptación - cliente no puede tener solicitudes activas
     */
    private Mono<Solicitud> validarSolicitudDuplicada(Mono<Solicitud> solicitudMono) {
        return solicitudMono
                .flatMap(solicitud ->
                        solicitudRepository.existeSolicitudActivaPorDocumento(solicitud.getNumeroDocumento())
                                .flatMap(existe -> existe
                                        ? Mono.error(new SolicitudDuplicadaException(
                                        "Cliente ya tiene una solicitud activa con documento: " + solicitud.getNumeroDocumento()))
                                        : Mono.just(solicitud))
                );
    }

    /**
     * Establecer estado inicial
     * CUMPLE: Criterio de aceptación - estado inicial "Pendiente de revisión"
     */
    private Mono<Solicitud> establecerEstadoInicial(Mono<Solicitud> solicitudMono) {
        return solicitudMono
                .map(solicitud -> {
                    solicitud.setEstado(EstadoSolicitud.PENDIENTE_REVISION);
                    solicitud.setFechaCreacion(LocalDateTime.now());
                    solicitud.setFechaActualizacion(LocalDateTime.now());
                    // Generar ID si no existe
                    if (solicitud.getId() == null || solicitud.getId().isEmpty()) {
                        solicitud.setId(java.util.UUID.randomUUID().toString());
                    }
                    return solicitud;
                });
    }

    /**
     * Valida si el tipo de préstamo es uno de los permitidos
     * CUMPLE: Reglas de negocio específicas del dominio
     */
    private boolean esTipoPrestamoValido(String tipoCredito) {
        if (tipoCredito == null || tipoCredito.trim().isEmpty()) {
            return false;
        }

        return java.util.Set.of("PERSONAL", "VEHICULO", "VIVIENDA", "EDUCATIVO")
                .contains(tipoCredito.toUpperCase().trim());
    }
}
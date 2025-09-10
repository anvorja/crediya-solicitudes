// domain/model/src/main/java/com/crediya/solicitudes/model/solicitud/gateways/SolicitudRepository.java
package com.crediya.solicitudes.model.solicitud.gateways;

import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * Gateway REACTIVO para persistencia de solicitudes
 * Define las operaciones de persistencia reactivas que necesita el dominio
 * CUMPLE con WebFlux y manejo reactivo de transacciones
 */
public interface SolicitudRepository {

    /**
     * Guarda una solicitud de forma reactiva (crear o actualizar)
     * @param solicitud la solicitud a guardar
     * @return Mono<Solicitud> con la solicitud guardada con ID generado
     */
    Mono<Solicitud> guardar(Solicitud solicitud);

    /**
     * Verifica si existe una solicitud activa para un documento de forma reactiva
     * Una solicitud activa es aquella que está en PENDIENTE_REVISION o EN_REVISION
     * @param numeroDocumento el número de documento
     * @return Mono<Boolean> true si existe una solicitud activa, false en caso contrario
     */
    Mono<Boolean> existeSolicitudActivaPorDocumento(String numeroDocumento);

    /**
     * Busca una solicitud por ID único de forma reactiva
     * @param id el ID de la solicitud
     * @return Mono<Solicitud> con la solicitud si existe, Mono.empty() si no existe
     */
    Mono<Solicitud> buscarPorId(String id);

    /**
     * Busca una solicitud por número de documento (máximo una activa) de forma reactiva
     * @param numeroDocumento el número de documento del cliente
     * @return Mono<Solicitud> con la solicitud más reciente para ese documento
     */
    Mono<Solicitud> buscarPorNumeroDocumento(String numeroDocumento);

    /**
     * Busca TODAS las solicitudes asociadas a un documento de forma reactiva
     * @param numeroDocumento el número de documento del cliente
     * @return Flux<Solicitud> con todas las solicitudes históricas para ese documento
     */
    Flux<Solicitud> buscarPorDocumento(String numeroDocumento);

    /**
     * Lista solicitudes por estado específico de forma reactiva
     * @param estado el estado de la solicitud
     * @return Flux<Solicitud> con las solicitudes en ese estado
     */
    Flux<Solicitud> buscarPorEstado(EstadoSolicitud estado);

    /**
     * Lista todas las solicitudes sin paginación de forma reactiva
     * @return Flux<Solicitud> con todas las solicitudes
     */
    Flux<Solicitud> buscarTodas();

    /**
     * Lista todas las solicitudes con paginación de forma reactiva
     * @param pagina número de página (base 0)
     * @param tamanio tamaño de la página
     * @return Flux<Solicitud> con la lista paginada de solicitudes
     */
    Flux<Solicitud> listarTodas(int pagina, int tamanio);

    /**
     * Cuenta solicitudes por estado de forma reactiva
     * @param estado el estado a contar
     * @return Mono<Long> con el número de solicitudes en ese estado
     */
    Mono<Long> contarPorEstado(EstadoSolicitud estado);

    /**
     * Lista solicitudes por rango de fechas de forma reactiva
     * @param fechaInicio fecha de inicio del rango
     * @param fechaFin fecha final del rango
     * @return Flux<Solicitud> con las solicitudes en ese rango
     */
    Flux<Solicitud> listarPorRangoFechas(LocalDateTime fechaInicio, LocalDateTime fechaFin);

    /**
     * Busca solicitudes por término de búsqueda (nombres, apellidos, documento) de forma reactiva
     * @param termino término a buscar
     * @param pagina número de página
     * @param tamanio tamaño de página
     * @return Flux<Solicitud> con las solicitudes que coinciden con el término
     */
    Flux<Solicitud> buscarPorTermino(String termino, int pagina, int tamanio);

    /**
     * Elimina una solicitud por ID de forma reactiva
     * @param id el ID de la solicitud a eliminar
     * @return Mono<Void> que se completa cuando la eliminación termina
     */
    Mono<Void> eliminar(String id);

    // ========================================
    // MÉTODOS ADICIONALES PARA FUNCIONALIDADES FUTURAS
    // ========================================

    /**
     * Actualiza el estado de una solicitud de forma reactiva
     * @param id ID de la solicitud
     * @param nuevoEstado nuevo estado a asignar
     * @return Mono<Solicitud> con la solicitud actualizada
     */
    Mono<Solicitud> actualizarEstado(String id, EstadoSolicitud nuevoEstado);

    /**
     * Busca solicitudes por múltiples estados de forma reactiva
     * @param estados lista de estados a buscar
     * @return Flux<Solicitud> con las solicitudes que tienen alguno de esos estados
     */
    Flux<Solicitud> buscarPorEstados(EstadoSolicitud... estados);

    /**
     * Cuenta el total de solicitudes de forma reactiva
     * @return Mono<Long> con el número total de solicitudes
     */
    Mono<Long> contarTodas();
}
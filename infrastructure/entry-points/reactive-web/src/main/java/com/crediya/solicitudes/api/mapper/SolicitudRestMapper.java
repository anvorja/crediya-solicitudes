package com.crediya.solicitudes.api.mapper;

import com.crediya.solicitudes.api.dto.request.CrearSolicitudRequest;
import com.crediya.solicitudes.api.dto.response.PaginaResponse;
import com.crediya.solicitudes.api.dto.response.SolicitudResponse;
import com.crediya.solicitudes.api.dto.response.SolicitudResumenResponse;
import com.crediya.solicitudes.model.common.Pagina;
import com.crediya.solicitudes.model.solicitud.ResumenSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.CrearSolicitudCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SolicitudRestMapper {

    CrearSolicitudCommand toCommand(CrearSolicitudRequest request);

    @Mapping(source = "id", target = "idSolicitud")
    @Mapping(source = "tipoPrestamo.nombre", target = "tipoPrestamo")
    @Mapping(source = "tipoPrestamo.tasaInteres", target = "tasaInteres")
    SolicitudResponse toResponse(Solicitud solicitud);

    @Mapping(source = "solicitud.id", target = "idSolicitud")
    @Mapping(source = "solicitud.monto", target = "monto")
    @Mapping(source = "solicitud.plazo", target = "plazo")
    @Mapping(source = "solicitud.email", target = "email")
    @Mapping(source = "nombreCliente", target = "nombre")
    @Mapping(source = "solicitud.tipoPrestamo.nombre", target = "tipoPrestamo")
    @Mapping(source = "solicitud.tipoPrestamo.tasaInteres", target = "tasaInteres")
    @Mapping(source = "solicitud.estado", target = "estadoSolicitud")
    @Mapping(source = "salarioBase", target = "salarioBase")
    @Mapping(source = "deudaTotalMensualAprobadas", target = "deudaTotalMensualSolicitudesAprobadas")
    SolicitudResumenResponse toResumenResponse(ResumenSolicitud resumen);

    default PaginaResponse<SolicitudResumenResponse> toPaginaResponse(Pagina<ResumenSolicitud> pagina) {
        return new PaginaResponse<>(
                pagina.elementos().stream().map(this::toResumenResponse).toList(),
                pagina.pagina(), pagina.tamano(), pagina.totalElementos(), pagina.totalPaginas());
    }
}

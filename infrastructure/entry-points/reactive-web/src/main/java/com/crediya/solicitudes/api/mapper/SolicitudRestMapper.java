package com.crediya.solicitudes.api.mapper;

import com.crediya.solicitudes.api.dto.request.CrearSolicitudRequest;
import com.crediya.solicitudes.api.dto.response.SolicitudResponse;
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
}

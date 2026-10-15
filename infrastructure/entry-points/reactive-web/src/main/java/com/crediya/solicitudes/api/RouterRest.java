package com.crediya.solicitudes.api;

import com.crediya.solicitudes.api.constants.ApiRoutes;
import com.crediya.solicitudes.api.documentation.SolicitudOpenApi;
import com.crediya.solicitudes.api.solicitud.SolicitudHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class RouterRest {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(SolicitudHandler solicitudHandler) {
        return route()
                .POST(ApiRoutes.SOLICITUD, accept(MediaType.APPLICATION_JSON),
                        solicitudHandler::crearSolicitud, SolicitudOpenApi::crearSolicitud)
                .build();
    }
}

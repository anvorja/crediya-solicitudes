package com.crediya.solicitudes.api;

import com.crediya.solicitudes.api.mapper.SolicitudRestMapper;
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(MockitoExtension.class)
class RouterRestTest {

    private WebTestClient webTestClient;

    @Mock
    private CrearSolicitudUseCase crearSolicitudUseCase;

    @Mock
    private SolicitudRestMapper solicitudRestMapper;

    @BeforeEach
    void setUp() {
        // Creamos el Handler con mocks
        Handler handler = new Handler(crearSolicitudUseCase, solicitudRestMapper);

        // Creamos el router con el handler mockeado
        RouterRest routerRest = new RouterRest();

        // Configuramos WebTestClient
        webTestClient = WebTestClient
                .bindToRouterFunction(routerRest.routerFunction(handler))
                .build();
    }

    @Test
    void testHealthEndpoint() {
        webTestClient.get()
                .uri("/api/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> Assertions.assertThat(response)
                        .contains("\"status\":\"UP\"")
                        .contains("\"service\":\"microservicio-solicitudes\"")
                        .contains("\"description\":\"HU2 - Registrar solicitud de préstamo\""));
    }

    @Test
    void testConsultarSolicitudEndpoint() {
        webTestClient.get()
                .uri("/api/v1/solicitud/123")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> Assertions.assertThat(response)
                        .contains("Endpoint en construcción - Consulta por ID")
                        .contains("solicitudId")
                        .contains("123"));
    }

    @Test
    void testConsultarPorDocumentoEndpoint() {
        webTestClient.get()
                .uri("/api/v1/solicitud/documento/12345678")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> Assertions.assertThat(response)
                        .contains("Endpoint en construcción - Consulta por documento")
                        .contains("numeroDocumento")
                        .contains("12345678"));
    }
}
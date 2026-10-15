package com.crediya.solicitudes.consumer.cliente;

import com.crediya.solicitudes.model.cliente.exceptions.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClienteGatewayAdapter contra un servidor HTTP real")
class ClienteGatewayAdapterTest {

    private static final String CUERPO_USUARIO = """
            {"id":"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11","nombres":"Maria","apellidos":"Gomez",
             "documento_identidad":"1020304050","fecha_nacimiento":"1995-03-20","direccion":"Calle 10",
             "telefono":"+573001234567","correo_electronico":"maria@example.com","salario_base":4500000.00,
             "rol":"CLIENTE","campo_nuevo_desconocido":"x"}""";

    private MockWebServer servidor;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = new MockWebServer();
        servidor.start();
    }

    @AfterEach
    void detener() throws IOException {
        servidor.shutdown();
    }

    private ClienteGatewayAdapter adaptador(CircuitBreakerRegistry registro, int timeoutMs, int reintentos) {
        var cliente = WebClient.builder()
                .baseUrl(servidor.url("/").toString())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
        return new ClienteGatewayAdapter(cliente, registro, timeoutMs, reintentos, 10);
    }

    private ClienteGatewayAdapter adaptador() {
        return adaptador(CircuitBreakerRegistry.ofDefaults(), 2000, 2);
    }

    private static MockResponse json(int estado, String cuerpo) {
        return new MockResponse().setResponseCode(estado).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(cuerpo);
    }

    @Test
    @DisplayName("cuando AUTENTICACION responde 200, debe mapear el cliente ignorando campos desconocidos")
    void cuando200_debeMapearCliente() throws InterruptedException {
        servidor.enqueue(json(200, CUERPO_USUARIO));

        StepVerifier.create(adaptador().buscarPorDocumento("1020304050"))
                .assertNext(cliente -> {
                    assertThat(cliente.documentoIdentidad()).isEqualTo("1020304050");
                    assertThat(cliente.nombreCompleto()).isEqualTo("Maria Gomez");
                    assertThat(cliente.email()).isEqualTo("maria@example.com");
                    assertThat(cliente.salarioBase()).isEqualByComparingTo("4500000");
                })
                .verifyComplete();

        RecordedRequest peticion = servidor.takeRequest();
        assertThat(peticion.getMethod()).isEqualTo("GET");
        assertThat(peticion.getPath()).isEqualTo("/api/v1/usuarios/1020304050");
        assertThat(peticion.getHeader(HttpHeaders.ACCEPT)).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("cuando AUTENTICACION responde 404, debe devolver un Mono vacio sin reintentar")
    void cuando404_debeDevolverVacio() {
        servidor.enqueue(json(404, "{\"codigo\":\"USUARIO_NO_ENCONTRADO\"}"));

        StepVerifier.create(adaptador().buscarPorDocumento("000")).verifyComplete();

        assertThat(servidor.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("cuando AUTENTICACION falla con 5xx una vez, debe reintentar y recuperarse")
    void cuando5xxTransitorio_debeReintentar() {
        servidor.enqueue(json(503, "{}"));
        servidor.enqueue(json(200, CUERPO_USUARIO));

        StepVerifier.create(adaptador().buscarPorDocumento("1020304050"))
                .assertNext(cliente -> assertThat(cliente.email()).isEqualTo("maria@example.com"))
                .verifyComplete();

        assertThat(servidor.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("cuando AUTENTICACION sigue fallando con 5xx, debe agotar los reintentos y lanzar ServicioNoDisponibleException")
    void cuando5xxPersistente_debeLanzarServicioNoDisponible() {
        for (int i = 0; i < 3; i++) {
            servidor.enqueue(json(500, "{}"));
        }

        StepVerifier.create(adaptador().buscarPorDocumento("1020304050"))
                .expectError(ServicioNoDisponibleException.class)
                .verify();

        assertThat(servidor.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("cuando AUTENTICACION responde 4xx distinto de 404, no debe reintentar y debe lanzar ServicioNoDisponibleException")
    void cuando4xx_noDebeReintentar() {
        servidor.enqueue(json(401, "{}"));

        StepVerifier.create(adaptador().buscarPorDocumento("1020304050"))
                .expectError(ServicioNoDisponibleException.class)
                .verify();

        assertThat(servidor.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("cuando AUTENTICACION no responde a tiempo, debe lanzar ServicioNoDisponibleException")
    void cuandoTimeout_debeLanzarServicioNoDisponible() {
        for (int i = 0; i < 2; i++) {
            servidor.enqueue(json(200, CUERPO_USUARIO).setBodyDelay(1500, java.util.concurrent.TimeUnit.MILLISECONDS));
        }

        StepVerifier.create(adaptador(CircuitBreakerRegistry.ofDefaults(), 200, 1).buscarPorDocumento("1020304050"))
                .expectError(ServicioNoDisponibleException.class)
                .verify();
    }

    @Test
    @DisplayName("cuando AUTENTICACION esta caido, debe lanzar ServicioNoDisponibleException")
    void cuandoServicioCaido_debeLanzarServicioNoDisponible() throws IOException {
        var adaptador = adaptador(CircuitBreakerRegistry.ofDefaults(), 500, 0);
        servidor.shutdown();

        StepVerifier.create(adaptador.buscarPorDocumento("1020304050"))
                .expectError(ServicioNoDisponibleException.class)
                .verify();
    }

    @Test
    @DisplayName("cuando el circuit breaker se abre, debe fallar rapido sin llamar al servicio")
    void cuandoCircuitBreakerAbierto_debeFallarRapido() {
        var configuracion = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .build();
        var adaptador = adaptador(CircuitBreakerRegistry.of(configuracion), 2000, 0);
        servidor.enqueue(json(500, "{}"));
        servidor.enqueue(json(500, "{}"));

        for (int i = 0; i < 2; i++) {
            StepVerifier.create(adaptador.buscarPorDocumento("1")).expectError(ServicioNoDisponibleException.class).verify();
        }
        int llamadasAntes = servidor.getRequestCount();

        StepVerifier.create(adaptador.buscarPorDocumento("1")).expectError(ServicioNoDisponibleException.class).verify();

        assertThat(servidor.getRequestCount()).isEqualTo(llamadasAntes);
    }

    // ---------- HU4: consulta por lotes ----------

    private static final String LISTA_USUARIOS = """
            [{"nombres":"Maria","apellidos":"Gomez","documento_identidad":"1020304050","correo_electronico":"maria@example.com",
              "salario_base":4500000,"rol":"CLIENTE","dato_nuevo":"x"},
             {"nombres":"Pedro","apellidos":"Ruiz","documento_identidad":"2020202020","correo_electronico":"pedro@example.com",
              "salario_base":3000000,"rol":"CLIENTE"}]""";

    @Test
    @DisplayName("por correos: debe enviar los correos en un solo parametro y mapear la lista de clientes")
    void porCorreos_debeMapearLaLista() throws InterruptedException {
        servidor.enqueue(json(200, LISTA_USUARIOS));

        StepVerifier.create(adaptador().buscarPorEmails(List.of("maria@example.com", "pedro@example.com")).collectList())
                .assertNext(clientes -> {
                    assertThat(clientes).extracting(cliente -> cliente.email()).containsExactly("maria@example.com", "pedro@example.com");
                    assertThat(clientes.get(0).nombreCompleto()).isEqualTo("Maria Gomez");
                    assertThat(clientes.get(1).salarioBase()).isEqualByComparingTo("3000000");
                })
                .verifyComplete();

        RecordedRequest peticion = servidor.takeRequest();
        assertThat(peticion.getPath()).startsWith("/api/v1/usuarios?correos=");
        assertThat(java.net.URLDecoder.decode(peticion.getPath(), java.nio.charset.StandardCharsets.UTF_8))
                .endsWith("correos=maria@example.com,pedro@example.com");
    }

    @Test
    @DisplayName("por correos: cuando AUTENTICACION devuelve una lista vacia, debe devolver un Flux vacio")
    void porCorreos_listaVacia_debeDevolverVacio() {
        servidor.enqueue(json(200, "[]"));

        StepVerifier.create(adaptador().buscarPorEmails(List.of("nadie@example.com"))).verifyComplete();
    }

    @Test
    @DisplayName("por correos: cuando no se piden correos, no debe llamar a AUTENTICACION")
    void porCorreos_sinCorreos_noDebeLlamar() {
        StepVerifier.create(adaptador().buscarPorEmails(List.of())).verifyComplete();

        assertThat(servidor.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("por correos: cuando AUTENTICACION falla con 5xx una vez, debe reintentar y recuperarse")
    void porCorreos_5xxTransitorio_debeReintentar() {
        servidor.enqueue(json(502, "{}"));
        servidor.enqueue(json(200, LISTA_USUARIOS));

        StepVerifier.create(adaptador().buscarPorEmails(List.of("maria@example.com")).count())
                .expectNext(2L)
                .verifyComplete();

        assertThat(servidor.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("por correos: cuando AUTENTICACION deniega (403) o falla, debe lanzar ServicioNoDisponibleException sin reintentar un 4xx")
    void porCorreos_403_debeLanzarServicioNoDisponible() {
        servidor.enqueue(json(403, "{}"));

        StepVerifier.create(adaptador().buscarPorEmails(List.of("maria@example.com")))
                .expectError(ServicioNoDisponibleException.class)
                .verify();

        assertThat(servidor.getRequestCount()).isEqualTo(1);
    }
}

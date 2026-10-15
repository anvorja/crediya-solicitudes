package com.crediya.solicitudes.consumer.cliente;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.exceptions.ServicioNoDisponibleException;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Consulta a AUTENTICACION al cliente que hace la solicitud.
 * Politica: timeout, reintentos con backoff solo ante fallos transitorios (5xx y errores de conexion) y circuit breaker.
 */
@Slf4j
@Component
public class ClienteGatewayAdapter implements ClienteGateway {

    static final String SERVICIO = "autenticacion";
    static final String CIRCUIT_BREAKER = "clienteGateway";
    private static final String RUTA = "/api/v1/usuarios/{documento}";

    private final WebClient client;
    private final CircuitBreaker circuitBreaker;
    private final Duration timeout;
    private final int maximoReintentos;
    private final Duration backoff;

    public ClienteGatewayAdapter(WebClient client,
                                 CircuitBreakerRegistry circuitBreakerRegistry,
                                 @Value("${adapter.restconsumer.timeout}") int timeoutMs,
                                 @Value("${adapter.restconsumer.max-retries:2}") int maximoReintentos,
                                 @Value("${adapter.restconsumer.retry-backoff-ms:100}") long backoffMs) {
        this.client = client;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maximoReintentos = maximoReintentos;
        this.backoff = Duration.ofMillis(backoffMs);
    }

    @Override
    public Mono<Cliente> buscarPorDocumento(String documentoIdentidad) {
        return client.get()
                .uri(RUTA, documentoIdentidad)
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(this::interpretar)
                .timeout(timeout)
                .retryWhen(Retry.backoff(maximoReintentos, backoff)
                        .filter(ClienteGatewayAdapter::esTransitorio)
                        .onRetryExhaustedThrow((especificacion, senal) -> senal.failure()))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnSubscribe(s -> log.info("Consultando cliente en el servicio de {}", SERVICIO))
                .onErrorMap(ClienteGatewayAdapter::esFallaDelServicio, this::aErrorDeDominio);
    }

    private Mono<Cliente> interpretar(ClientResponse respuesta) {
        HttpStatusCode estado = respuesta.statusCode();
        if (estado.is2xxSuccessful()) {
            return respuesta.bodyToMono(UsuarioAutenticacionResponse.class).map(ClienteGatewayAdapter::aCliente);
        }
        if (estado.value() == 404) {
            return respuesta.releaseBody().then(Mono.empty());
        }
        return respuesta.releaseBody().then(Mono.error(new RespuestaInesperada(estado)));
    }

    private static Cliente aCliente(UsuarioAutenticacionResponse respuesta) {
        return new Cliente(respuesta.documentoIdentidad(), respuesta.nombres(), respuesta.apellidos(),
                respuesta.correoElectronico(), respuesta.salarioBase());
    }

    private static boolean esTransitorio(Throwable error) {
        if (error instanceof RespuestaInesperada inesperada) {
            return inesperada.estado.is5xxServerError();
        }
        return true;
    }

    private static boolean esFallaDelServicio(Throwable error) {
        return !(error instanceof ServicioNoDisponibleException);
    }

    private Throwable aErrorDeDominio(Throwable error) {
        if (error instanceof CallNotPermittedException) {
            log.warn("Circuit breaker abierto para el servicio de {}", SERVICIO);
        } else {
            log.error("Fallo consultando el servicio de {}: {}", SERVICIO, error.toString());
        }
        return new ServicioNoDisponibleException(SERVICIO);
    }

    private static final class RespuestaInesperada extends RuntimeException {
        private final transient HttpStatusCode estado;

        private RespuestaInesperada(HttpStatusCode estado) {
            super("Respuesta inesperada del servicio de " + SERVICIO + ": " + estado.value());
            this.estado = estado;
        }
    }
}

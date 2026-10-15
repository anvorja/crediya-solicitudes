package com.crediya.solicitudes.r2dbc.evento;

import com.crediya.solicitudes.model.evento.Destino;
import com.crediya.solicitudes.model.evento.EventoDeDominio;
import com.crediya.solicitudes.model.evento.EventoSaliente;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EventoSalienteRepositoryAdapter implements EventoSalienteRepository {

    // El id lo genera PostgreSQL y se copia dentro del payload como id_evento (los consumidores lo usan para deduplicar)
    private static final String INSERTAR = """
            WITH nuevo AS (SELECT gen_random_uuid() AS id)
            INSERT INTO evento_saliente (id, destino, payload)
            SELECT id, :destino, (CAST(:payload AS JSONB) || jsonb_build_object('id_evento', id))::TEXT FROM nuevo
            """;

    private static final String TOMAR_PENDIENTES = """
            SELECT id, destino, payload, intentos
            FROM evento_saliente
            WHERE enviado_en IS NULL
            ORDER BY creado_en ASC, id ASC
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """;

    private static final String MARCAR_ENVIADO = "UPDATE evento_saliente SET enviado_en = now(), ultimo_error = NULL WHERE id = :id";

    private static final String REGISTRAR_FALLO =
            "UPDATE evento_saliente SET intentos = intentos + 1, ultimo_error = :motivo WHERE id = :id";

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> registrar(EventoDeDominio evento) {
        Destino destino = EventoSerializador.destinoDe(evento);
        return databaseClient.sql(INSERTAR)
                .bind("destino", destino.name())
                .bind("payload", EventoSerializador.aJson(evento))
                .fetch()
                .rowsUpdated()
                .doOnSubscribe(s -> log.debug("Registrando evento en outbox destino={}", destino))
                .then();
    }

    @Override
    public Flux<EventoSaliente> tomarPendientes(int limite) {
        return databaseClient.sql(TOMAR_PENDIENTES)
                .bind("limite", limite)
                .map((fila, metadatos) -> new EventoSaliente(
                        fila.get("id", UUID.class),
                        Destino.valueOf(fila.get("destino", String.class)),
                        fila.get("payload", String.class),
                        fila.get("intentos", Integer.class)))
                .all();
    }

    @Override
    public Mono<Void> marcarEnviado(UUID id) {
        return databaseClient.sql(MARCAR_ENVIADO).bind("id", id).fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> registrarFallo(UUID id, String motivo) {
        return databaseClient.sql(REGISTRAR_FALLO).bind("id", id).bind("motivo", motivo).fetch().rowsUpdated().then()
                .doOnSubscribe(s -> log.warn("Fallo publicando evento id={}", id));
    }
}

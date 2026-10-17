package com.crediya.solicitudes.config;

import com.crediya.solicitudes.usecase.publicareventos.PublicarEventosPendientesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;

/**
 * Relevo del outbox: cada cierto tiempo publica los eventos pendientes. Una ronda que falla no detiene el relevo.
 * {@code concatMap} garantiza que nunca se solapan dos rondas en esta instancia; entre instancias lo evita
 * {@code FOR UPDATE SKIP LOCKED}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final PublicarEventosPendientesUseCase publicarEventosPendientes;
    private final OutboxProperties propiedades;
    private final Disposable.Swap suscripcion = Disposables.swap();

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (!propiedades.enabled()) {
            log.info("Relevo del outbox desactivado");
            return;
        }
        log.info("Relevo del outbox activo cada {} (lote de {})", propiedades.intervalo(), propiedades.lote());
        suscripcion.update(Flux.interval(propiedades.intervalo())
                .onBackpressureDrop()
                .concatMap(tic -> publicarEventosPendientes.ejecutar(propiedades.lote())
                        .doOnNext(publicados -> {
                            if (publicados > 0) {
                                log.info("Eventos publicados en esta ronda: {}", publicados);
                            }
                        })
                        .onErrorResume(error -> {
                            log.warn("Ronda del outbox fallida: {}", error.toString());
                            return Mono.empty();
                        }), 1)
                .subscribe());
    }

    @PreDestroy
    public void detener() {
        suscripcion.dispose();
    }
}

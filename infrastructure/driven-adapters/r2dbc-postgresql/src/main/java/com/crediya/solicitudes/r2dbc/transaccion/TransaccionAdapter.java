package com.crediya.solicitudes.r2dbc.transaccion;

import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TransaccionAdapter implements TransaccionGateway {

    private final TransactionalOperator operator;

    @Override
    public <T> Mono<T> ejecutar(Mono<T> operacion) {
        return operator.transactional(operacion);
    }
}

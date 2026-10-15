package com.crediya.solicitudes.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final Validator validator;

    public <T> Mono<T> validar(T request) {
        return Mono.defer(() -> {
            Set<ConstraintViolation<T>> violaciones = validator.validate(request);
            if (violaciones.isEmpty()) {
                return Mono.just(request);
            }
            Map<String, String> detalles = new TreeMap<>();
            violaciones.forEach(v -> detalles.put(aSnakeCase(v.getPropertyPath().toString()), v.getMessage()));
            return Mono.error(new RequestValidationException(detalles));
        });
    }

    private static String aSnakeCase(String campo) {
        return campo.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}

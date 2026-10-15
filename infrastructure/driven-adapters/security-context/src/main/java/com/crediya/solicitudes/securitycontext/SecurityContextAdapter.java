package com.crediya.solicitudes.securitycontext;

import com.crediya.solicitudes.model.seguridad.UsuarioAutenticado;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.seguridad.Rol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Traduce la autenticacion de Spring Security (JWT ya validado) al usuario del dominio.
 * Un token sin identidad o rol validos se trata como "sin usuario": nunca se asume un rol por defecto.
 */
@Component
public class SecurityContextAdapter implements ContextoSeguridadGateway {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_DOCUMENTO = "documento_identidad";
    static final String CLAIM_ROL = "rol";

    @Override
    public Mono<UsuarioAutenticado> usuarioActual() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(autenticacion -> Mono.justOrEmpty(aUsuario(autenticacion.getPrincipal())));
    }

    private static Optional<UsuarioAutenticado> aUsuario(Object principal) {
        if (principal instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario);
        }
        if (principal instanceof Jwt jwt) {
            return desdeJwt(jwt);
        }
        return Optional.empty();
    }

    private static Optional<UsuarioAutenticado> desdeJwt(Jwt jwt) {
        Optional<Rol> rol = rol(jwt.getClaimAsString(CLAIM_ROL));
        Optional<UUID> id = uuid(jwt.getSubject());
        String documento = jwt.getClaimAsString(CLAIM_DOCUMENTO);
        if (rol.isEmpty() || id.isEmpty() || documento == null || documento.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new UsuarioAutenticado(id.get(), documento, jwt.getClaimAsString(CLAIM_EMAIL), rol.get()));
    }

    private static Optional<Rol> rol(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Rol.valueOf(texto.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> uuid(String texto) {
        try {
            return Optional.of(UUID.fromString(texto));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}

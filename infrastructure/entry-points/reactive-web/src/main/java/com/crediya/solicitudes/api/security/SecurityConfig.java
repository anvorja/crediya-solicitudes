package com.crediya.solicitudes.api.security;

import com.crediya.solicitudes.api.constants.ApiRoutes;
import com.crediya.solicitudes.model.seguridad.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Flux;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] RUTAS_PUBLICAS = {
            "/actuator/health", "/actuator/health/**",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**"
    };

    private final SecurityErrorHandlers erroresDeSeguridad;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(Customizer.withDefaults())
                // API sin estado: cada peticion se autentica con su token
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(rutas -> rutas
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(RUTAS_PUBLICAS).permitAll()
                        .pathMatchers(HttpMethod.POST, ApiRoutes.SOLICITUD).hasRole(Rol.CLIENTE.name())
                        .pathMatchers(HttpMethod.GET, ApiRoutes.SOLICITUD).hasRole(Rol.ASESOR.name())
                        .anyExchange().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(erroresDeSeguridad)
                        .accessDeniedHandler(erroresDeSeguridad))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(erroresDeSeguridad)
                        .accessDeniedHandler(erroresDeSeguridad)
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(convertidorDeJwt())))
                .build();
    }

    static ReactiveJwtAuthenticationConverter convertidorDeJwt() {
        var convertidor = new ReactiveJwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridadesDesdeRol());
        return convertidor;
    }

    /**
     * El claim {@code rol} del token se convierte en la autoridad ROLE_{rol}. Sin rol no hay autoridades.
     */
    static Converter<Jwt, Flux<GrantedAuthority>> autoridadesDesdeRol() {
        return jwt -> {
            String rol = jwt.getClaimAsString("rol");
            if (rol == null || rol.isBlank()) {
                return Flux.empty();
            }
            return Flux.just(new SimpleGrantedAuthority("ROLE_" + rol.trim().toUpperCase()));
        };
    }
}

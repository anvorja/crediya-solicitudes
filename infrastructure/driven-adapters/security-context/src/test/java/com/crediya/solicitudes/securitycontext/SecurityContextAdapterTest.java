package com.crediya.solicitudes.securitycontext;

import com.crediya.solicitudes.model.seguridad.UsuarioAutenticado;
import com.crediya.solicitudes.model.seguridad.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityContextAdapter")
class SecurityContextAdapterTest {

    private static final String ID = "6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11";
    private final SecurityContextAdapter adaptador = new SecurityContextAdapter();

    private static Jwt jwt(Consumer<Jwt.Builder> ajuste) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256").subject(ID)
                .claim("email", "maria@example.com").claim("documento_identidad", "1020304050").claim("rol", "ASESOR")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        ajuste.accept(builder);
        return builder.build();
    }

    private Mono<UsuarioAutenticado> conAutenticacion(Authentication autenticacion) {
        return adaptador.usuarioActual().contextWrite(ReactiveSecurityContextHolder.withAuthentication(autenticacion));
    }

    private Mono<UsuarioAutenticado> conJwt(Jwt jwt) {
        return conAutenticacion(new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }

    @Test
    @DisplayName("cuando el JWT trae identidad y rol validos, debe construir el usuario autenticado")
    void cuandoJwtValido_debeConstruirUsuario() {
        StepVerifier.create(conJwt(jwt(b -> { })))
                .assertNext(usuario -> {
                    assertThat(usuario.id()).isEqualTo(UUID.fromString(ID));
                    assertThat(usuario.documentoIdentidad()).isEqualTo("1020304050");
                    assertThat(usuario.email()).isEqualTo("maria@example.com");
                    assertThat(usuario.rol()).isEqualTo(Rol.ASESOR);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("el rol del token debe interpretarse sin distinguir mayusculas ni espacios")
    void rol_debeIgnorarMayusculas() {
        StepVerifier.create(conJwt(jwt(b -> b.claim("rol", " admin "))))
                .assertNext(usuario -> assertThat(usuario.rol()).isEqualTo(Rol.ADMIN))
                .verifyComplete();
    }

    @Test
    @DisplayName("cuando el token no trae rol, no debe asumir ninguno: devuelve vacio")
    void cuandoSinRol_debeDevolverVacio() {
        StepVerifier.create(conJwt(jwt(b -> b.claims(c -> c.remove("rol"))))).verifyComplete();
    }

    @Test
    @DisplayName("cuando el rol del token es desconocido, debe devolver vacio")
    void cuandoRolDesconocido_debeDevolverVacio() {
        StepVerifier.create(conJwt(jwt(b -> b.claim("rol", "SUPERUSUARIO")))).verifyComplete();
    }

    @Test
    @DisplayName("cuando el subject no es un UUID, debe devolver vacio")
    void cuandoSubjectInvalido_debeDevolverVacio() {
        StepVerifier.create(conJwt(jwt(b -> b.subject("no-es-uuid")))).verifyComplete();
    }

    @Test
    @DisplayName("cuando el token no trae subject, debe devolver vacio")
    void cuandoSinSubject_debeDevolverVacio() {
        StepVerifier.create(conJwt(jwt(b -> b.claims(c -> c.remove("sub"))))).verifyComplete();
    }

    @Test
    @DisplayName("cuando el token no trae documento de identidad, debe devolver vacio")
    void cuandoSinDocumento_debeDevolverVacio() {
        StepVerifier.create(conJwt(jwt(b -> b.claims(c -> c.remove("documento_identidad"))))).verifyComplete();
    }

    @Test
    @DisplayName("cuando no hay contexto de seguridad, debe devolver vacio")
    void cuandoSinContexto_debeDevolverVacio() {
        StepVerifier.create(adaptador.usuarioActual()).verifyComplete();
    }

    @Test
    @DisplayName("cuando la autenticacion es anonima, debe devolver vacio")
    void cuandoAnonimo_debeDevolverVacio() {
        var anonimo = new AnonymousAuthenticationToken("clave", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        StepVerifier.create(conAutenticacion(anonimo)).verifyComplete();
    }

    @Test
    @DisplayName("cuando la autenticacion no esta autenticada, debe devolver vacio")
    void cuandoNoAutenticada_debeDevolverVacio() {
        var sinAutenticar = new UsernamePasswordAuthenticationToken(jwt(b -> { }), null);

        StepVerifier.create(conAutenticacion(sinAutenticar)).verifyComplete();
    }

    @Test
    @DisplayName("cuando el principal es de otro tipo, debe devolver vacio")
    void cuandoPrincipalDesconocido_debeDevolverVacio() {
        StepVerifier.create(conAutenticacion(new UsernamePasswordAuthenticationToken("texto", null, List.of())))
                .verifyComplete();
    }
}

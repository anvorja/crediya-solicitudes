package com.crediya.solicitudes.api.solicitud;

import com.crediya.solicitudes.api.RouterRest;
import com.crediya.solicitudes.api.exception.GlobalErrorHandler;
import com.crediya.solicitudes.api.mapper.SolicitudRestMapperImpl;
import com.crediya.solicitudes.api.validation.RequestValidator;
import com.crediya.solicitudes.model.cliente.exceptions.ClienteNoEncontradoException;
import com.crediya.solicitudes.model.cliente.exceptions.ServicioNoDisponibleException;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.crediya.solicitudes.model.common.Pagina;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.ResumenSolicitud;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.CrearSolicitudCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.MontoFueraDeRangoException;
import com.crediya.solicitudes.model.solicitud.exceptions.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import com.crediya.solicitudes.usecase.listarsolicitudes.ListarSolicitudesUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("POST /api/v1/solicitud (HU2) - router, handler y manejo de errores")
class SolicitudRouterTest {

    private static final String RUTA = "/api/v1/solicitud";
    private static final String CUERPO_VALIDO = """
            {"documento_identidad":"1020304050","monto":5000000,"plazo":24,"tipo_prestamo":"PERSONAL"}""";
    private static final TipoPrestamo PERSONAL = new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"),
            new BigDecimal("50000000"), new BigDecimal("0.018"), true);

    @Mock
    private CrearSolicitudUseCase useCase;

    @Mock
    private ListarSolicitudesUseCase listarUseCase;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var handler = new SolicitudHandler(useCase, listarUseCase, new SolicitudRestMapperImpl(),
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator()));
        var errorHandler = new GlobalErrorHandler(new DefaultErrorAttributes(), new WebProperties(),
                new StaticApplicationContext(), ServerCodecConfigurer.create());
        client = WebTestClient.bindToRouterFunction(new RouterRest().routerFunction(handler))
                .handlerStrategies(estrategiasConManejadorGlobal(errorHandler))
                .build();
    }

    /**
     * HandlerStrategies.builder() ya registra un manejador de ResponseStatusException que respondería antes
     * que el nuestro; en la aplicacion real el manejador global se ejecuta primero (@Order(-2)).
     */
    private static HandlerStrategies estrategiasConManejadorGlobal(GlobalErrorHandler errorHandler) {
        return HandlerStrategies.empty()
                .codecs(configurer -> configurer.registerDefaults(true))
                .exceptionHandler(errorHandler)
                .build();
    }

    private WebTestClient.ResponseSpec post(String cuerpo) {
        return client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON).bodyValue(cuerpo).exchange();
    }

    @Test
    @DisplayName("cuando el cuerpo es valido, debe responder 201 con la solicitud Pendiente de revision en snake_case")
    void cuandoCuerpoValido_debeResponder201() {
        var id = UUID.randomUUID();
        when(useCase.ejecutar(any())).thenReturn(Mono.just(new Solicitud(id, new BigDecimal("5000000"), 24,
                "maria@example.com", EstadoSolicitud.PENDIENTE_REVISION, PERSONAL)));

        post(CUERPO_VALIDO)
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id_solicitud").isEqualTo(id.toString())
                .jsonPath("$.monto").isEqualTo(5000000)
                .jsonPath("$.plazo").isEqualTo(24)
                .jsonPath("$.email").isEqualTo("maria@example.com")
                .jsonPath("$.estado").isEqualTo("PENDIENTE_REVISION")
                .jsonPath("$.tipo_prestamo").isEqualTo("PERSONAL")
                .jsonPath("$.tasa_interes").isEqualTo(0.018);

        var comando = ArgumentCaptor.forClass(CrearSolicitudCommand.class);
        verify(useCase).ejecutar(comando.capture());
        assertThat(comando.getValue().documentoIdentidad()).isEqualTo("1020304050");
        assertThat(comando.getValue().monto()).isEqualByComparingTo("5000000");
        assertThat(comando.getValue().plazo()).isEqualTo(24);
        assertThat(comando.getValue().tipoPrestamo()).isEqualTo("PERSONAL");
    }

    @Test
    @DisplayName("cuando faltan campos o son invalidos, debe responder 400 con el detalle por campo y sin llamar al caso de uso")
    void cuandoDatosInvalidos_debeResponder400ConDetalles() {
        post("""
                {"documento_identidad":"abc","monto":-10,"plazo":0}""")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                .jsonPath("$.detalles.documento_identidad").isNotEmpty()
                .jsonPath("$.detalles.monto").isNotEmpty()
                .jsonPath("$.detalles.plazo").isNotEmpty()
                .jsonPath("$.detalles.tipo_prestamo").isNotEmpty();

        verify(useCase, never()).ejecutar(any());
    }

    @Test
    @DisplayName("cuando el plazo supera 360 meses, debe responder 400")
    void cuandoPlazoSuperaMaximo_debeResponder400() {
        post(CUERPO_VALIDO.replace("\"plazo\":24", "\"plazo\":361"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detalles.plazo").isNotEmpty();
    }

    @Test
    @DisplayName("cuando no hay cuerpo, debe responder 400 indicando que es obligatorio")
    void cuandoSinCuerpo_debeResponder400() {
        client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detalles.cuerpo").isNotEmpty();
    }

    @Test
    @DisplayName("cuando el JSON esta mal formado, debe responder 400 sin detalles internos")
    void cuandoJsonMalFormado_debeResponder400() {
        post("{esto no es json")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("PETICION_INVALIDA");
    }

    @Test
    @DisplayName("cuando el tipo de contenido no es JSON, debe responder 415")
    void cuandoContentTypeNoSoportado_debeResponder415() {
        client.post().uri(RUTA).contentType(MediaType.TEXT_PLAIN).bodyValue("hola").exchange()
                .expectStatus().isEqualTo(415);
    }

    @Test
    @DisplayName("cuando el cliente no existe, debe responder 404")
    void cuandoClienteNoExiste_debeResponder404() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new ClienteNoEncontradoException()));

        post(CUERPO_VALIDO)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("CLIENTE_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("cuando el tipo de prestamo no existe, debe responder 400")
    void cuandoTipoNoExiste_debeResponder400() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new TipoPrestamoNoValidoException("INEXISTENTE")));

        post(CUERPO_VALIDO)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TIPO_PRESTAMO_NO_VALIDO")
                .jsonPath("$.mensaje").value(mensaje -> assertThat(mensaje.toString()).contains("INEXISTENTE"));
    }

    @Test
    @DisplayName("cuando el monto esta fuera del rango del tipo, debe responder 400")
    void cuandoMontoFueraDeRango_debeResponder400() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(
                new MontoFueraDeRangoException("PERSONAL", new BigDecimal("500000"), new BigDecimal("50000000"))));

        post(CUERPO_VALIDO)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("MONTO_FUERA_DE_RANGO");
    }

    @Test
    @DisplayName("cuando el dominio rechaza los datos, debe responder 400 con el codigo del dominio")
    void cuandoDominioRechazaDatos_debeResponder400() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new DatosSolicitudInvalidosException("plazo", "es obligatorio")));

        post(CUERPO_VALIDO)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("DATOS_SOLICITUD_INVALIDOS");
    }

    @Test
    @DisplayName("cuando AUTENTICACION no responde, debe responder 503")
    void cuandoServicioNoDisponible_debeResponder503() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new ServicioNoDisponibleException("autenticacion")));

        post(CUERPO_VALIDO)
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.codigo").isEqualTo("SERVICIO_NO_DISPONIBLE");
    }

    @Test
    @DisplayName("cuando ocurre un error inesperado, debe responder 500 con mensaje generico sin filtrar detalles")
    void cuandoErrorInesperado_debeResponder500Generico() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new IllegalStateException("password=super-secreto")));

        post(CUERPO_VALIDO)
                .expectStatus().is5xxServerError()
                .expectBody(String.class)
                .value(cuerpo -> assertThat(cuerpo)
                        .contains("ERROR_INTERNO")
                        .doesNotContain("super-secreto")
                        .doesNotContain("IllegalStateException"));
    }

    @Test
    @DisplayName("cuando la ruta no existe, debe responder 404 con el formato de error uniforme")
    void cuandoRutaInexistente_debeResponder404() {
        client.get().uri("/api/v1/no-existe").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("PETICION_INVALIDA");
    }

    @Test
    @DisplayName("cuando el dominio deniega el acceso, debe responder 403 con el codigo del dominio")
    void cuandoDominioDeniegaAcceso_debeResponder403() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new AccesoDenegadoException()));

        post(CUERPO_VALIDO)
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
    }

    // ---------- HU4: listado ----------

    private static ResumenSolicitud resumen(String nombre, BigDecimal salario, String deuda) {
        var solicitud = new Solicitud(UUID.randomUUID(), new BigDecimal("5000000"), 24, "maria@example.com",
                EstadoSolicitud.PENDIENTE_REVISION, PERSONAL);
        return new ResumenSolicitud(solicitud, nombre, salario, new BigDecimal(deuda));
    }

    @Test
    @DisplayName("GET listado: debe devolver la pagina con los campos de la HU4 en snake_case")
    void listar_debeDevolverLaPaginaConLosCamposDeLaHu() {
        when(listarUseCase.ejecutar(any())).thenReturn(Mono.just(new Pagina<>(
                List.of(resumen("Maria Gomez", new BigDecimal("4500000"), "258404.18")), 0, 10, 21)));

        client.get().uri(RUTA).exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.pagina").isEqualTo(0)
                .jsonPath("$.tamano").isEqualTo(10)
                .jsonPath("$.total_elementos").isEqualTo(21)
                .jsonPath("$.total_paginas").isEqualTo(3)
                .jsonPath("$.contenido[0].monto").isEqualTo(5000000)
                .jsonPath("$.contenido[0].plazo").isEqualTo(24)
                .jsonPath("$.contenido[0].email").isEqualTo("maria@example.com")
                .jsonPath("$.contenido[0].nombre").isEqualTo("Maria Gomez")
                .jsonPath("$.contenido[0].tipo_prestamo").isEqualTo("PERSONAL")
                .jsonPath("$.contenido[0].tasa_interes").isEqualTo(0.018)
                .jsonPath("$.contenido[0].estado_solicitud").isEqualTo("PENDIENTE_REVISION")
                .jsonPath("$.contenido[0].salario_base").isEqualTo(4500000)
                .jsonPath("$.contenido[0].deuda_total_mensual_solicitudes_aprobadas").isEqualTo(258404.18);
    }

    @Test
    @DisplayName("GET listado: cuando AUTENTICACION no conoce al solicitante, debe omitir nombre y salario")
    void listar_clienteDesconocido_debeDevolverNulos() {
        when(listarUseCase.ejecutar(any())).thenReturn(Mono.just(new Pagina<>(List.of(resumen(null, null, "0")), 0, 10, 1)));

        client.get().uri(RUTA).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.contenido[0].nombre").isEmpty()
                .jsonPath("$.contenido[0].salario_base").isEmpty()
                .jsonPath("$.contenido[0].deuda_total_mensual_solicitudes_aprobadas").isEqualTo(0);
    }

    @Test
    @DisplayName("GET listado: sin parametros debe usar el filtro por defecto")
    void listar_sinParametros_debeUsarFiltroPorDefecto() {
        when(listarUseCase.ejecutar(any())).thenReturn(Mono.just(new Pagina<>(List.of(), 0, 10, 0)));

        client.get().uri(RUTA).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.contenido.length()").isEqualTo(0).jsonPath("$.total_paginas").isEqualTo(0);

        var filtro = ArgumentCaptor.forClass(FiltroSolicitudes.class);
        verify(listarUseCase).ejecutar(filtro.capture());
        assertThat(filtro.getValue().estados()).isEqualTo(EstadoSolicitud.PARA_REVISION_MANUAL);
        assertThat(filtro.getValue().tipoPrestamo()).isNull();
        assertThat(filtro.getValue().pagina()).isZero();
        assertThat(filtro.getValue().tamano()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET listado: debe interpretar estados repetidos y separados por coma, tipo, pagina y tamano")
    void listar_debeInterpretarLosParametros() {
        when(listarUseCase.ejecutar(any())).thenReturn(Mono.just(new Pagina<>(List.of(), 2, 25, 0)));

        client.get().uri(uri -> uri.path(RUTA)
                        .queryParam("estado", "rechazada,revision_manual").queryParam("estado", "PENDIENTE_REVISION")
                        .queryParam("tipo_prestamo", "personal").queryParam("pagina", "2").queryParam("tamano", "25").build())
                .exchange().expectStatus().isOk();

        var filtro = ArgumentCaptor.forClass(FiltroSolicitudes.class);
        verify(listarUseCase).ejecutar(filtro.capture());
        assertThat(filtro.getValue().estados()).containsExactlyInAnyOrder(
                EstadoSolicitud.RECHAZADA, EstadoSolicitud.REVISION_MANUAL, EstadoSolicitud.PENDIENTE_REVISION);
        assertThat(filtro.getValue().tipoPrestamo()).isEqualTo("personal");
        assertThat(filtro.getValue().pagina()).isEqualTo(2);
        assertThat(filtro.getValue().tamano()).isEqualTo(25);
    }

    @Test
    @DisplayName("GET listado: cuando pagina o tamano no son enteros, debe responder 400 con el detalle de ambos")
    void listar_enterosInvalidos_debeResponder400() {
        client.get().uri(RUTA + "?pagina=abc&tamano=1.5").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                .jsonPath("$.detalles.pagina").isNotEmpty()
                .jsonPath("$.detalles.tamano").isNotEmpty();

        verify(listarUseCase, never()).ejecutar(any());
    }

    @Test
    @DisplayName("GET listado: cuando el estado no existe o no es de revision, debe responder 400 con el codigo del dominio")
    void listar_estadoInvalido_debeResponder400() {
        client.get().uri(RUTA + "?estado=INEXISTENTE").exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.codigo").isEqualTo("DATOS_SOLICITUD_INVALIDOS");
        client.get().uri(RUTA + "?estado=APROBADA").exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.codigo").isEqualTo("DATOS_SOLICITUD_INVALIDOS");
    }

    @Test
    @DisplayName("GET listado: cuando el tamano esta fuera de 1 y 50 o la pagina es negativa, debe responder 400")
    void listar_paginacionInvalida_debeResponder400() {
        client.get().uri(RUTA + "?tamano=51").exchange().expectStatus().isBadRequest();
        client.get().uri(RUTA + "?tamano=0").exchange().expectStatus().isBadRequest();
        client.get().uri(RUTA + "?pagina=-1").exchange().expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET listado: cuando el dominio deniega el acceso, debe responder 403")
    void listar_denegado_debeResponder403() {
        when(listarUseCase.ejecutar(any())).thenReturn(Mono.error(new AccesoDenegadoException()));

        client.get().uri(RUTA).exchange()
                .expectStatus().isForbidden().expectBody().jsonPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
    }
}

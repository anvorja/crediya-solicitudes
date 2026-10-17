package com.crediya.solicitudes.model.solicitud;

import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.MontoFueraDeRangoException;
import com.crediya.solicitudes.model.solicitud.exceptions.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamoFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Solicitud - reglas de creacion (HU2)")
class SolicitudTest {

    private static final BigDecimal MONTO = new BigDecimal("5000000");

    @Test
    @DisplayName("cuando los datos son validos, debe crearse sin id y en estado Pendiente de revision")
    void cuandoDatosValidos_debeCrearsePendienteDeRevision() {
        var solicitud = Solicitud.crear("  Maria@Example.COM ", MONTO, 24, TipoPrestamoFixture.personal());

        assertThat(solicitud.id()).isNull();
        assertThat(solicitud.email()).isEqualTo("maria@example.com");
        assertThat(solicitud.monto()).isEqualByComparingTo(MONTO);
        assertThat(solicitud.plazo()).isEqualTo(24);
        assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.PENDIENTE_REVISION);
        assertThat(solicitud.tipoPrestamo()).isEqualTo(TipoPrestamoFixture.personal());
    }

    @ParameterizedTest(name = "email = [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("cuando el email es nulo o vacio, debe rechazar")
    void cuandoEmailVacio_debeRechazar(String email) {
        assertThatThrownBy(() -> Solicitud.crear(email, MONTO, 12, TipoPrestamoFixture.personal()))
                .isInstanceOf(DatosSolicitudInvalidosException.class)
                .satisfies(e -> assertThat(((DatosSolicitudInvalidosException) e).getCampo()).isEqualTo("email"));
    }

    @ParameterizedTest(name = "monto = {0}")
    @ValueSource(strings = {"0", "-1", "-5000000"})
    @DisplayName("cuando el monto no es positivo, debe rechazar")
    void cuandoMontoNoPositivo_debeRechazar(String monto) {
        assertThatThrownBy(() -> Solicitud.crear("a@b.co", new BigDecimal(monto), 12, TipoPrestamoFixture.personal()))
                .isInstanceOf(DatosSolicitudInvalidosException.class)
                .satisfies(e -> assertThat(((DatosSolicitudInvalidosException) e).getCampo()).isEqualTo("monto"));
    }

    @Test
    @DisplayName("cuando el monto es nulo, debe rechazar")
    void cuandoMontoNulo_debeRechazar() {
        assertThatThrownBy(() -> Solicitud.crear("a@b.co", null, 12, TipoPrestamoFixture.personal()))
                .isInstanceOf(DatosSolicitudInvalidosException.class);
    }

    @ParameterizedTest(name = "plazo = {0}")
    @ValueSource(ints = {0, -1, 361, 1000})
    @DisplayName("cuando el plazo esta fuera de 1 y 360 meses, debe rechazar")
    void cuandoPlazoFueraDeRango_debeRechazar(int plazo) {
        assertThatThrownBy(() -> Solicitud.crear("a@b.co", MONTO, plazo, TipoPrestamoFixture.personal()))
                .isInstanceOf(DatosSolicitudInvalidosException.class)
                .satisfies(e -> assertThat(((DatosSolicitudInvalidosException) e).getCampo()).isEqualTo("plazo"));
    }

    @ParameterizedTest(name = "plazo = {0}")
    @ValueSource(ints = {1, 360})
    @DisplayName("cuando el plazo es el minimo o el maximo, debe aceptar")
    void cuandoPlazoEnLimites_debeAceptar(int plazo) {
        assertThat(Solicitud.crear("a@b.co", MONTO, plazo, TipoPrestamoFixture.personal()).plazo()).isEqualTo(plazo);
    }

    @Test
    @DisplayName("cuando el plazo es nulo, debe rechazar")
    void cuandoPlazoNulo_debeRechazar() {
        assertThatThrownBy(() -> Solicitud.crear("a@b.co", MONTO, null, TipoPrestamoFixture.personal()))
                .isInstanceOf(DatosSolicitudInvalidosException.class);
    }

    @ParameterizedTest(name = "monto = {0}")
    @ValueSource(strings = {"499999.99", "50000000.01"})
    @DisplayName("cuando el monto esta fuera del rango del tipo de prestamo, debe rechazar con MontoFueraDeRangoException")
    void cuandoMontoFueraDeRangoDelTipo_debeRechazar(String monto) {
        assertThatThrownBy(() -> Solicitud.crear("a@b.co", new BigDecimal(monto), 12, TipoPrestamoFixture.personal()))
                .isInstanceOf(MontoFueraDeRangoException.class)
                .hasMessageContaining("PERSONAL")
                .hasMessageContaining("500000")
                .hasMessageContaining("50000000");
    }

    @Test
    @DisplayName("cuando se asigna un id, debe devolver una copia con el id y el resto intacto")
    void cuandoSeAsignaId_debeDevolverCopia() {
        var id = UUID.randomUUID();
        var original = Solicitud.crear("a@b.co", MONTO, 12, TipoPrestamoFixture.personal());

        var conId = original.conId(id);

        assertThat(conId.id()).isEqualTo(id);
        assertThat(conId).usingRecursiveComparison().ignoringFields("id").isEqualTo(original);
        assertThat(original.id()).isNull();
    }

    @Test
    @DisplayName("la cuota mensual debe calcularse con el monto, la tasa del tipo y el plazo de la solicitud")
    void cuotaMensual_debeUsarMontoTasaYPlazo() {
        var solicitud = Solicitud.crear("a@b.co", new BigDecimal("5000000"), 24, TipoPrestamoFixture.personal());

        assertThat(solicitud.cuotaMensual()).isEqualByComparingTo("258404.18");
    }

    @Test
    @DisplayName("los estados deben tener una descripcion legible")
    void estados_debenTenerDescripcion() {
        for (EstadoSolicitud estado : EstadoSolicitud.values()) {
            assertThat(estado.getDescripcion()).isNotBlank();
        }
        assertThat(EstadoSolicitud.PENDIENTE_REVISION.getDescripcion()).isEqualTo("Pendiente de revision");
    }

    @Test
    @DisplayName("las excepciones de solicitud deben exponer codigo y campo")
    void excepciones_debenExponerCodigo() {
        var invalidos = new DatosSolicitudInvalidosException("monto", "es obligatorio");
        var tipo = new TipoPrestamoNoValidoException("INEXISTENTE");

        assertThat(invalidos.getCode()).isEqualTo(DatosSolicitudInvalidosException.CODE);
        assertThat(invalidos.getMessage()).isEqualTo("El campo 'monto' es obligatorio");
        assertThat(tipo.getCode()).isEqualTo(TipoPrestamoNoValidoException.CODE);
        assertThat(tipo.getMessage()).contains("INEXISTENTE");
    }

    @Test
    @DisplayName("cambiarEstado: cuando la transicion es valida, devuelve otra solicitud con el nuevo estado y los demas datos iguales")
    void cambiarEstado_transicionValida() {
        var id = UUID.randomUUID();
        var original = new Solicitud(id, MONTO, 24, "maria@example.com", EstadoSolicitud.PENDIENTE_REVISION, TipoPrestamoFixture.personal());

        var aprobada = original.cambiarEstado(EstadoSolicitud.APROBADA);

        assertThat(aprobada.estado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(aprobada.id()).isEqualTo(id);
        assertThat(aprobada.email()).isEqualTo("maria@example.com");
        assertThat(original.estado()).isEqualTo(EstadoSolicitud.PENDIENTE_REVISION);
    }

    @Test
    @DisplayName("cambiarEstado: cuando la transicion no esta permitida, falla")
    void cambiarEstado_transicionInvalida() {
        var aprobada = new Solicitud(UUID.randomUUID(), MONTO, 24, "maria@example.com", EstadoSolicitud.APROBADA, TipoPrestamoFixture.personal());

        assertThatThrownBy(() -> aprobada.cambiarEstado(EstadoSolicitud.RECHAZADA))
                .isInstanceOf(com.crediya.solicitudes.model.solicitud.exceptions.TransicionEstadoInvalidaException.class)
                .hasMessageContaining("No se puede pasar");
    }
}

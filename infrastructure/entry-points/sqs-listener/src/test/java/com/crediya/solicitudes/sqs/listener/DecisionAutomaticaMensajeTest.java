package com.crediya.solicitudes.sqs.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Contrato del mensaje de decision automatica")
class DecisionAutomaticaMensajeTest {

    private static final String COMPLETO = """
            {"version":1,"id_evento":"e1","tipo":"DECISION_AUTOMATICA","id_solicitud":"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11",
             "decision":"APROBADA","campo_futuro":{"x":1},
             "destinatario":{"email":"maria@example.com","nombre":"Maria Gomez"},
             "plan_de_pago":[{"numero":1,"cuota":258404.18,"capital":168404.18,"interes":90000.00,"saldo":4831595.82},
                             {"numero":2,"cuota":258404.18,"capital":171435.25,"interes":86968.93,"saldo":4660160.57}]}""";

    @Test
    @DisplayName("lee la decision, el nombre y el plan de pago e ignora los campos que no conoce")
    void mensajeCompleto() {
        var comando = DecisionAutomaticaMensaje.aComando(COMPLETO);

        assertThat(comando.idSolicitud()).isEqualTo("6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11");
        assertThat(comando.decision()).isEqualTo("APROBADA");
        assertThat(comando.nombreCliente()).isEqualTo("Maria Gomez");
        assertThat(comando.planDePago()).hasSize(2);
        assertThat(comando.planDePago().get(1).numero()).isEqualTo(2);
        assertThat(comando.planDePago().get(1).interes()).isEqualByComparingTo("86968.93");
    }

    @Test
    @DisplayName("el nombre y el plan son opcionales")
    void minimo() {
        var comando = DecisionAutomaticaMensaje.aComando("{\"id_solicitud\":\"x\",\"decision\":\"REVISION_MANUAL\"}");

        assertThat(comando.nombreCliente()).isNull();
        assertThat(comando.planDePago()).isEmpty();
    }

    @ParameterizedTest(name = "mensaje = {0}")
    @ValueSource(strings = {"no es json", "[1,2]", "\"texto\"", "{}", "{\"id_solicitud\":\"x\"}", "{\"decision\":\"APROBADA\"}",
            "{\"id_solicitud\":\" \",\"decision\":\"APROBADA\"}", "{\"id_solicitud\":5,\"decision\":\"APROBADA\"}",
            "{\"version\":2,\"id_solicitud\":\"x\",\"decision\":\"APROBADA\"}",
            "{\"id_solicitud\":\"x\",\"decision\":\"APROBADA\",\"plan_de_pago\":[{\"numero\":1,\"cuota\":\"mucho\"}]}"})
    @DisplayName("un mensaje que no cumple el contrato se rechaza con IllegalArgumentException")
    void mensajeInvalido(String cuerpo) {
        assertThatThrownBy(() -> DecisionAutomaticaMensaje.aComando(cuerpo)).isInstanceOf(IllegalArgumentException.class);
    }
}

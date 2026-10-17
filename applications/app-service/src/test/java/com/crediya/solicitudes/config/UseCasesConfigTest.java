package com.crediya.solicitudes.config;

import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.tipoprestamo.gateways.TipoPrestamoRepository;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.evento.gateways.PublicadorDeEventos;
import com.crediya.solicitudes.usecase.actualizarestado.ActualizarEstadoSolicitudUseCase;
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import com.crediya.solicitudes.usecase.procesardecision.ProcesarDecisionAutomaticaUseCase;
import com.crediya.solicitudes.usecase.publicareventos.PublicarEventosPendientesUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UseCasesConfig: registro automatico de casos de uso")
class UseCasesConfigTest {

    @Test
    @DisplayName("debe registrar como bean cada clase *UseCase con sus puertos inyectados")
    void debeRegistrarCasosDeUso() {
        try (var contexto = new AnnotationConfigApplicationContext(PuertosSimulados.class)) {
            assertThat(contexto.getBeansOfType(CrearSolicitudUseCase.class)).hasSize(1);
            assertThat(contexto.getBeansOfType(ActualizarEstadoSolicitudUseCase.class)).hasSize(1);
            assertThat(contexto.getBeansOfType(PublicarEventosPendientesUseCase.class)).hasSize(1);
            assertThat(contexto.getBeansOfType(ProcesarDecisionAutomaticaUseCase.class)).hasSize(1);
        }
    }

    @Configuration
    @Import(UseCasesConfig.class)
    static class PuertosSimulados {

        @Bean
        ClienteGateway clienteGateway() {
            return Mockito.mock(ClienteGateway.class);
        }

        @Bean
        TipoPrestamoRepository tipoPrestamoRepository() {
            return Mockito.mock(TipoPrestamoRepository.class);
        }

        @Bean
        SolicitudRepository solicitudRepository() {
            return Mockito.mock(SolicitudRepository.class);
        }

        @Bean
        ContextoSeguridadGateway contextoSeguridadGateway() {
            return Mockito.mock(ContextoSeguridadGateway.class);
        }

        @Bean
        EventoSalienteRepository eventoSalienteRepository() {
            return Mockito.mock(EventoSalienteRepository.class);
        }

        @Bean
        PublicadorDeEventos publicadorDeEventos() {
            return Mockito.mock(PublicadorDeEventos.class);
        }

        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }

        @Bean
        TransaccionGateway transaccionGateway() {
            return Mockito.mock(TransaccionGateway.class);
        }
    }
}

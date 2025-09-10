package com.crediya.solicitudes.integration;

import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.exception.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exception.SolicitudDuplicadaException;
import com.crediya.solicitudes.model.solicitud.exception.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Integración - Registrar Solicitud de Préstamo")
class RegistrarSolicitudIntegrationTest {

    @Mock
    private SolicitudRepository solicitudRepository;

    private CrearSolicitudUseCase useCase;

    @BeforeEach
    void setUp() {
        // ✅ CORRECTO: Solo SolicitudRepository según el código del proyecto
        useCase = new CrearSolicitudUseCase(solicitudRepository);
    }

    private Solicitud crearSolicitudCompletaValida() {
        Solicitud solicitud = new Solicitud();
        // Información del cliente (documento de identidad)
        solicitud.setNumeroDocumento("12345678");
        solicitud.setNombres("Juan Carlos");
        solicitud.setApellidos("Pérez Gómez");
        solicitud.setEmail("juan.perez@email.com");
        solicitud.setTelefono("+573001234567");

        // Detalles del préstamo (monto, plazo, tipo)
        solicitud.setMontoSolicitado(new BigDecimal("5000000"));
        solicitud.setPlazoMeses(24);
        solicitud.setTipoCredito("PERSONAL"); // Tipo existente

        // Información financiera
        solicitud.setIngresosMensuales(new BigDecimal("3000000"));
        solicitud.setGastosMensuales(new BigDecimal("1500000"));

        solicitud.setFechaCreacion(LocalDateTime.now());
        return solicitud;
    }

    @Nested
    @DisplayName("Historia de Usuario: Registrar una solicitud de préstamo")
    class HistoriaUsuarioCompleta {

        @Test
        @DisplayName("Escenario Principal: Como cliente, envío solicitud completa exitosamente")
        void escenarioPrincipalRegistroCompleto() {
            // Given - Cliente con datos válidos
            Solicitud solicitud = crearSolicitudCompletaValida();
            Solicitud solicitudGuardada = crearSolicitudGuardada();

            // Configurar mocks para flujo exitoso - REACTIVO
            when(solicitudRepository.existeSolicitudActivaPorDocumento("12345678"))
                    .thenReturn(Mono.just(false));
            when(solicitudRepository.guardar(any(Solicitud.class)))
                    .thenReturn(Mono.just(solicitudGuardada));

            // When & Then - Verificar flujo reactivo completo
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectNextMatches(resultado -> {
                        // CA-1: Solicitud registrada con información completa
                        return resultado.getNumeroDocumento().equals("12345678") &&
                                resultado.getMontoSolicitado().equals(new BigDecimal("5000000")) &&
                                resultado.getPlazoMeses() == 24 &&
                                resultado.getTipoCredito().equals("PERSONAL") &&
                                // CA-2: Estado inicial correcto
                                resultado.getEstado().equals(EstadoSolicitud.PENDIENTE_REVISION) &&
                                // Información guardada permanentemente
                                resultado.getId() != null;
                    })
                    .verifyComplete();

            // Verificar interacciones
            verify(solicitudRepository).guardar(any(Solicitud.class));
        }

        @Test
        @DisplayName("Escenario Alternativo: Tipo de préstamo inválido")
        void escenarioAlternativoTipoPrestamoInvalido() {
            // Given
            Solicitud solicitud = crearSolicitudCompletaValida();
            solicitud.setTipoCredito("TIPO_NO_EXISTENTE"); // CA-3: Tipo no válido

            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectErrorMatches(error ->
                            error instanceof TipoPrestamoNoValidoException &&
                                    error.getMessage().contains("Tipo de crédito no válido")
                    )
                    .verify();

            // Verificar que NO se guardó nada
            verify(solicitudRepository, never()).guardar(any());
        }

        @Test
        @DisplayName("Escenario Alternativo: Cliente ya tiene solicitud activa")
        void escenarioAlternativoSolicitudDuplicada() {
            // Given
            Solicitud solicitud = crearSolicitudCompletaValida();

            // Cliente ya tiene una solicitud activa
            when(solicitudRepository.existeSolicitudActivaPorDocumento("12345678"))
                    .thenReturn(Mono.just(true));

            // When & Then - No debe permitir duplicados
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectErrorMatches(error ->
                            error instanceof SolicitudDuplicadaException &&
                                    error.getMessage().contains("Cliente ya tiene una solicitud activa")
                    )
                    .verify();

            // Verificar que NO se guardó nada
            verify(solicitudRepository, never()).guardar(any());
        }
    }

    @Nested
    @DisplayName("Validaciones Técnicas Específicas - Flujo Reactivo")
    class ValidacionesTecnicas {

        @Test
        @DisplayName("Debe validar campos obligatorios del cliente")
        void debeValidarCamposObligatoriosCliente() {

            String[] camposObligatorios = {
                    "numeroDocumento", "nombres", "apellidos", "email"
            };

            for (String campo : camposObligatorios) {
                Solicitud solicitudInvalida = crearSolicitudConCampoInvalido(campo);

                // When & Then - Usar StepVerifier para cada validación
                StepVerifier.create(useCase.ejecutar(solicitudInvalida))
                        .expectErrorMatches(error -> {
                            boolean esTipoCorreto = error instanceof DatosSolicitudInvalidosException;
                            boolean contieneTextoEsperado = error.getMessage().toLowerCase()
                                    .contains(campo.toLowerCase().contains("numero") ? "documento" : campo.toLowerCase());
                            return esTipoCorreto && contieneTextoEsperado;
                        })
                        .verify();
            }
        }

        @Test
        @DisplayName("Debe validar monto según criterios de negocio")
        void debeValidarRangosMonto() {

            // Test monto cero (según el UseCase real: "mayor a cero")
            Solicitud montoInvalido = crearSolicitudCompletaValida();
            montoInvalido.setMontoSolicitado(BigDecimal.ZERO);

            StepVerifier.create(useCase.ejecutar(montoInvalido))
                    .expectErrorMatches(error ->
                            error instanceof DatosSolicitudInvalidosException &&
                                    error.getMessage().contains("mayor a cero")
                    )
                    .verify();
        }
    }

    // ============================================================
    // MÉTODOS AUXILIARES PARA CREAR SOLICITUDES DE PRUEBA
    // ============================================================

    private Solicitud crearSolicitudConCampoInvalido(String campo) {
        Solicitud solicitud = crearSolicitudCompletaValida();

        switch (campo) {
            case "numeroDocumento" -> solicitud.setNumeroDocumento(""); // Vacío
            case "nombres" -> solicitud.setNombres(null); // Null
            case "apellidos" -> solicitud.setApellidos("   "); // Solo espacios
            case "email" -> solicitud.setEmail(""); // Vacío
            // Removemos "telefono" porque no está en las validaciones del UseCase actual
            default -> throw new IllegalArgumentException("Campo no válido: " + campo);
        }

        return solicitud;
    }

    private Solicitud crearSolicitudGuardada() {
        Solicitud guardada = crearSolicitudCompletaValida();
        guardada.setId("SOL-001");
        guardada.setEstado(EstadoSolicitud.PENDIENTE_REVISION); // CA-2: Estado inicial
        guardada.setFechaActualizacion(LocalDateTime.now());
        return guardada;
    }

}

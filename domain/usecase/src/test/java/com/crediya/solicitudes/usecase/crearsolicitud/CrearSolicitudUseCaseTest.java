// domain/usecase/src/test/java/com/crediya/solicitudes/usecase/crearsolicitud/CrearSolicitudUseCaseTest.java
package com.crediya.solicitudes.usecase.crearsolicitud;

import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.exception.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exception.SolicitudDuplicadaException;
import com.crediya.solicitudes.model.solicitud.exception.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios reactivos para CrearSolicitudUseCase (HU2)
 * CUMPLE: 90% cobertura en dominio usando StepVerifier
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrearSolicitudUseCase - Tests Reactivos HU2")
class CrearSolicitudUseCaseTest {

    @Mock
    private SolicitudRepository solicitudRepository;

    private CrearSolicitudUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearSolicitudUseCase(solicitudRepository);
    }

    @Nested
    @DisplayName("Criterios de Aceptación - HU2")
    class CriteriosAceptacionHU2 {

        @Test
        @DisplayName("CA-1: Debe registrar solicitud con datos completos del cliente y préstamo")
        void debeRegistrarSolicitudConDatosCompletos() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            Solicitud solicitudGuardada = crearSolicitudValidaConId();

            when(solicitudRepository.existeSolicitudActivaPorDocumento(anyString()))
                    .thenReturn(Mono.just(false));
            when(solicitudRepository.guardar(any(Solicitud.class)))
                    .thenReturn(Mono.just(solicitudGuardada));

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectNextMatches(resultado ->
                            resultado.getId() != null &&
                                    resultado.getNumeroDocumento().equals("12345678") &&
                                    resultado.getNombres().equals("Juan") &&
                                    resultado.getApellidos().equals("Pérez") &&
                                    resultado.getMontoSolicitado().compareTo(new BigDecimal("5000000")) == 0 &&
                                    resultado.getTipoCredito().equals("PERSONAL")
                    )
                    .verifyComplete();

            verify(solicitudRepository).guardar(any(Solicitud.class));
        }

        @Test
        @DisplayName("CA-2: Debe asignar estado inicial 'PENDIENTE_REVISION' automáticamente")
        void debeAsignarEstadoPendienteRevision() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            Solicitud solicitudGuardada = crearSolicitudValidaConId();

            when(solicitudRepository.existeSolicitudActivaPorDocumento(anyString()))
                    .thenReturn(Mono.just(false));
            when(solicitudRepository.guardar(any(Solicitud.class)))
                    .thenReturn(Mono.just(solicitudGuardada));

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectNextMatches(resultado ->
                            resultado.getEstado() == EstadoSolicitud.PENDIENTE_REVISION &&
                                    resultado.getFechaCreacion() != null &&
                                    resultado.getFechaActualizacion() != null
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("CA-3: Debe validar tipos de préstamo válidos")
        void debeValidarTiposPrestamoValidos() {
            // Given - Tipos válidos según BusinessConstants
            String[] tiposValidos = {"PERSONAL", "VEHICULO", "VIVIENDA", "EDUCATIVO"};

            for (String tipoValido : tiposValidos) {
                Solicitud solicitud = crearSolicitudValida();
                solicitud.setTipoCredito(tipoValido);
                Solicitud solicitudGuardada = crearSolicitudValidaConId();

                when(solicitudRepository.existeSolicitudActivaPorDocumento(anyString()))
                        .thenReturn(Mono.just(false));
                when(solicitudRepository.guardar(any(Solicitud.class)))
                        .thenReturn(Mono.just(solicitudGuardada));

                // When & Then - No debe lanzar excepción
                StepVerifier.create(useCase.ejecutar(solicitud))
                        .expectNextCount(1)
                        .verifyComplete();
            }
        }

        @Test
        @DisplayName("CA-3: Debe rechazar tipos de préstamo inválidos")
        void debeRechazarTipoPrestamoInvalido() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setTipoCredito("TIPO_INVALIDO");

            // NO necesitamos mocks porque el error ocurre antes de las validaciones asíncronas

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectError(TipoPrestamoNoValidoException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("Validaciones de Datos Básicos")
    class ValidacionesDatosBasicos {

        @Test
        @DisplayName("Debe fallar con documento vacío")
        void deberiaFallarConDocumentoVacio() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setNumeroDocumento("");

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectError(DatosSolicitudInvalidosException.class)
                    .verify();
        }

        @Test
        @DisplayName("Debe fallar con nombres vacíos")
        void deberiaFallarConNombresVacios() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setNombres(null);

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectError(DatosSolicitudInvalidosException.class)
                    .verify();
        }

        @Test
        @DisplayName("Debe fallar con apellidos vacíos")
        void deberiaFallarConApellidosVacios() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setApellidos("   ");

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectError(DatosSolicitudInvalidosException.class)
                    .verify();
        }

        @Test
        @DisplayName("Debe fallar con email vacío")
        void deberiaFallarConEmailVacio() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setEmail(null);

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectError(DatosSolicitudInvalidosException.class)
                    .verify();
        }

        @Test
        @DisplayName("Debe fallar con monto inválido")
        void deberiaFallarConMontoInvalido() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setMontoSolicitado(BigDecimal.ZERO);

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectError(DatosSolicitudInvalidosException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("Validación de Solicitudes Duplicadas")
    class ValidacionSolicitudesDuplicadas {

        @Test
        @DisplayName("Debe fallar cuando cliente ya tiene solicitud activa")
        void deberiaFallarConSolicitudDuplicada() {
            // Given
            Solicitud solicitud = crearSolicitudValida();

            when(solicitudRepository.existeSolicitudActivaPorDocumento("12345678"))
                    .thenReturn(Mono.just(true)); // Cliente ya tiene solicitud

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectError(SolicitudDuplicadaException.class)
                    .verify();
        }

        @Test
        @DisplayName("Debe procesar cuando cliente NO tiene solicitud activa")
        void deberiaProcesarSinSolicitudDuplicada() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            Solicitud solicitudGuardada = crearSolicitudValidaConId();

            when(solicitudRepository.existeSolicitudActivaPorDocumento("12345678"))
                    .thenReturn(Mono.just(false)); // NO tiene solicitud
            when(solicitudRepository.guardar(any(Solicitud.class)))
                    .thenReturn(Mono.just(solicitudGuardada));

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Casos Límite")
    class CasosLimite {

        @Test
        @DisplayName("Debe procesar montos en límites permitidos")
        void debeProcesarMontosEnLimitesPermitidos() {
            // Given - Monto en el límite superior
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setMontoSolicitado(new BigDecimal("50000000")); // 50M límite superior

            when(solicitudRepository.existeSolicitudActivaPorDocumento(anyString()))
                    .thenReturn(Mono.just(false));
            when(solicitudRepository.guardar(any(Solicitud.class)))
                    .thenAnswer(invocation -> {
                        Solicitud sol = invocation.getArgument(0);
                        sol.setId("uuid-generado-123"); // Asegurar que tenga ID
                        return Mono.just(sol);
                    });

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectNextMatches(resultado -> {
                        // Debug para ver qué está recibiendo
                        System.out.println("Resultado: " + resultado);
                        System.out.println("Monto esperado: 50000000");
                        System.out.println("Monto actual: " + resultado.getMontoSolicitado());

                        return resultado.getMontoSolicitado() != null &&
                                resultado.getMontoSolicitado().compareTo(new BigDecimal("50000000")) == 0;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Debe generar ID si no existe")
        void debeGenerarIdSiNoExiste() {
            // Given
            Solicitud solicitud = crearSolicitudValida();
            solicitud.setId(null); // Sin ID

            when(solicitudRepository.existeSolicitudActivaPorDocumento(anyString()))
                    .thenReturn(Mono.just(false));
            when(solicitudRepository.guardar(any(Solicitud.class)))
                    .thenAnswer(invocation -> {
                        Solicitud sol = invocation.getArgument(0);
                        return Mono.just(sol);
                    });

            // When & Then
            StepVerifier.create(useCase.ejecutar(solicitud))
                    .expectNextMatches(resultado -> resultado.getId() != null && !resultado.getId().isEmpty())
                    .verifyComplete();
        }
    }

    // ============================================
    // MÉTODOS AUXILIARES PARA TESTS
    // ============================================

    private Solicitud crearSolicitudValida() {
        Solicitud solicitud = new Solicitud();
        solicitud.setNumeroDocumento("12345678");
        solicitud.setNombres("Juan");
        solicitud.setApellidos("Pérez");
        solicitud.setEmail("juan.perez@email.com");
        solicitud.setMontoSolicitado(new BigDecimal("5000000"));
        solicitud.setPlazoMeses(24);
        solicitud.setTipoCredito("PERSONAL");
        return solicitud;
    }

    private Solicitud crearSolicitudValidaConId() {
        Solicitud solicitud = crearSolicitudValida();
        solicitud.setId("uuid-generado-123");
        solicitud.setEstado(EstadoSolicitud.PENDIENTE_REVISION);
        solicitud.setFechaCreacion(java.time.LocalDateTime.now());
        solicitud.setFechaActualizacion(java.time.LocalDateTime.now());
        return solicitud;
    }
}
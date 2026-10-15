# CrediYa - Microservicio de Solicitudes

Solicitudes de prestamo de la plataforma CrediYa. Arquitectura hexagonal / Clean Architecture con
[Scaffold de Bancolombia](https://bancolombia.github.io/scaffold-clean-architecture/) 3.25.0, Spring Boot 3.5 y WebFlux
(flujo reactivo de punta a punta: WebFlux -> casos de uso -> R2DBC / WebClient).

## Estado de las historias de usuario

| HU | Descripcion | Rama | Estado |
|----|-------------|------|--------|
| HU2 | Registrar una solicitud de prestamo (`POST /api/v1/solicitud`) | `feature/HU-02-solicitud` | Hecha |
| HU3 | Autenticacion: solo un cliente crea solicitudes y solo a su nombre | `feature/HU-03-...` | Pendiente |
| HU4 | Listado de solicitudes para revision manual (`GET /api/v1/solicitud`) | `feature/HU-04-...` | Pendiente |
| HU5 | Contenerizacion con Docker y docker-compose | `feature/HU-05-...` | Pendiente |
| HU6 | Aprobar o rechazar una solicitud (`PUT /api/v1/solicitud`) + notificacion | `feature/HU-06-...` | Pendiente |
| HU7 | Validacion automatica (capacidad de endeudamiento) | `feature/HU-07-...` | Pendiente |

## Estructura

```
domain/model      entidades, comandos, excepciones de dominio y puertos (gateways). Sin Spring ni slf4j
domain/usecase    casos de uso (flujo reactivo). Solo depende de model
infrastructure/
  driven-adapters/r2dbc-postgresql   persistencia R2DBC (SQL explicito con DatabaseClient) + migraciones Flyway
  driven-adapters/rest-consumer      cliente del microservicio de AUTENTICACION (timeout, reintentos, circuit breaker)
  entry-points/reactive-web          router funcional, handlers, DTO (records), MapStruct, OpenAPI, errores
applications/app-service             ensamblado, configuracion y main
```

Reglas del enunciado que se cumplen: el dominio no tiene `spring-boot-dependencies` ni `slf4j-api`; los logs viven en
infraestructura (adaptadores/gateways); los errores de dominio llevan un codigo y `GlobalErrorHandler`
(extiende `AbstractErrorWebExceptionHandler`) los traduce a HTTP; la documentacion OpenAPI esta en una clase utilitaria
(`SolicitudOpenApi`), no en anotaciones; validacion tanto en DTO (Bean Validation) como en dominio.

## Registrar una solicitud (HU2)

El cliente se valida contra AUTENTICACION (`GET /api/v1/usuarios/{documento}`) y el email de la solicitud es el del cliente.

```bash
curl -i -X POST http://localhost:8080/api/v1/solicitud -H 'Content-Type: application/json' -d '{
  "documento_identidad": "1020304050", "monto": 5000000, "plazo": 24, "tipo_prestamo": "PERSONAL"
}'
```

Tipos de prestamo sembrados por Flyway: `PERSONAL`, `LIBRE_INVERSION`, `VEHICULO`, `HIPOTECARIO` (la tasa es mensual).

| Codigo | Cuando |
|--------|--------|
| 201 | Solicitud registrada en estado `PENDIENTE_REVISION` |
| 400 | Campos invalidos, tipo de prestamo inexistente o monto fuera del rango del tipo |
| 404 | El cliente no esta registrado en AUTENTICACION |
| 503 | AUTENTICACION no responde (tras reintentos) o el circuit breaker esta abierto |
| 500 | Error inesperado (mensaje generico, sin detalles internos) |

## Ejecutar localmente

Requisitos: Java 21 y Docker (PostgreSQL local y tests de integracion con Testcontainers).

```bash
docker run -d --name crediya-solicitudes-db -e POSTGRES_DB=crediya_solicitudes -e POSTGRES_USER=crediya \
  -e POSTGRES_PASSWORD=cambia-esta-clave -p 5432:5432 postgres:16-alpine
export DB_USERNAME=crediya DB_PASSWORD=cambia-esta-clave AUTH_SERVICE_URL=http://localhost:8081
./gradlew bootRun
```

Swagger UI: http://localhost:8080/swagger-ui.html - Health: http://localhost:8080/actuator/health

> Si tenias una base de datos de la version anterior (esquema `solicitudes`), recreala: la migracion `V1` se reescribio
> para seguir el modelo del enunciado y Flyway rechazara el checksum antiguo.

## Pruebas y calidad

```bash
./gradlew check                 # tests + estructura + cobertura minima 90% en model y usecase
./gradlew jacocoMergedReport    # reporte consolidado: build/reports/jacocoMergedReport/
./gradlew jacocoMergedReport -PrunPitest   # ademas, mutation testing (lento, opt-in)
```

Notas de entorno: Testcontainers 1.21.x necesita `api.version=1.44` con Docker Engine 29+ (ya configurado en `main.gradle`;
se puede cambiar con `-PdockerApiVersion=...`).

## Flujo de trabajo (gitflow)

`main` (estable) <- `develop` <- `feature/HU-XX-nombre` (una rama por HU, se integra a `develop` con `--no-ff`).

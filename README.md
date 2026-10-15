# CrediYa - Microservicio de Solicitudes

Solicitudes de prestamo de la plataforma CrediYa. Arquitectura hexagonal / Clean Architecture con
[Scaffold de Bancolombia](https://bancolombia.github.io/scaffold-clean-architecture/) 3.25.0, Spring Boot 3.5 y WebFlux
(flujo reactivo de punta a punta: WebFlux -> casos de uso -> R2DBC / WebClient).

## Estado de las historias de usuario

| HU | Descripcion | Rama | Estado |
|----|-------------|------|--------|
| HU2 | Registrar una solicitud de prestamo (`POST /api/v1/solicitud`) | `feature/HU-02-solicitud` | Hecha |
| HU3 | Autenticacion: solo un cliente crea solicitudes y solo a su nombre | `feature/HU-03-seguridad` | Hecha |
| HU4 | Listado de solicitudes para revision manual (`GET /api/v1/solicitud`) | `feature/HU-04-listado-solicitudes` | Hecha |
| HU5 | Contenerizacion con Docker y docker-compose | `feature/HU-05-contenerizacion` | Hecha |
| HU6 | Aprobar o rechazar una solicitud (`PUT /api/v1/solicitud`) + notificacion | `feature/HU-06-aprobar-rechazar-solicitud` | Hecha |
| HU7 | Validacion automatica (capacidad de endeudamiento) | `feature/HU-07-validacion-automatica` | Hecha |

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

## Seguridad (HU3)

Este servicio es un *resource server*: valida los JWT RS256 que emite AUTENTICACION usando **solo su clave publica**
(`GET /.well-known/jwks.json`, cacheada); no comparte ningun secreto. Se comprueban firma, vigencia, emisor y audiencia.

| Endpoint | Quien puede |
|----------|-------------|
| `/actuator/health`, `/v3/api-docs`, Swagger UI | Publico |
| `POST /api/v1/solicitud` | Solo `CLIENTE`, y unicamente a nombre de su propio `documento_identidad` |
| `GET /api/v1/solicitud` | Solo `ASESOR` |

- Un token sin rol no concede ningun permiso (403). Un token invalido, vencido, de otra audiencia o firmado con otra clave da 401.
- El token del cliente se **reenvia** a AUTENTICACION al validar al cliente, de modo que ese servicio aplica sus propias reglas.
- Los errores 401/403 usan el mismo formato JSON que el resto de la API y no revelan el motivo del rechazo.

## Listado para revision manual (HU4)

`GET /api/v1/solicitud` (solo `ASESOR`) lista las solicitudes que necesitan decision: **Pendiente de revision**, **Revision manual**
y **Rechazada**, de la mas antigua a la mas reciente. Cada fila incluye `monto`, `plazo`, `email`, `nombre`, `tipo_prestamo`,
`tasa_interes`, `estado_solicitud`, `salario_base` y `deuda_total_mensual_solicitudes_aprobadas`.

| Parametro | Descripcion |
|-----------|-------------|
| `estado` | Estados a incluir, repetidos o separados por coma. Solo admite los tres de revision (no `APROBADA`) |
| `tipo_prestamo` | Nombre del tipo, sin distinguir mayusculas |
| `pagina` | Empieza en 0 (por defecto 0) |
| `tamano` | De 1 a 50 (por defecto 10) |

```bash
curl -H "Authorization: Bearer $TOKEN_ASESOR" 'http://localhost:8080/api/v1/solicitud?estado=RECHAZADA,REVISION_MANUAL&pagina=0&tamano=20'
```

La respuesta es `{ contenido: [...], pagina, tamano, total_elementos, total_paginas }`. Para evitar una consulta por fila, el
servicio hace **una** consulta por lotes a AUTENTICACION (nombre y salario) y **una** a la base (aprobadas de esos correos).
La deuda mensual suma la cuota de cada prestamo aprobado con la formula de amortizacion
`P * i * (1 + i)^n / ((1 + i)^n - 1)` (i = tasa mensual del tipo, n = plazo en meses). Si AUTENTICACION ya no conoce al
solicitante, `nombre` y `salario_base` salen nulos pero la solicitud sigue apareciendo.

## Aprobar o rechazar una solicitud y notificar (HU6)

`PUT /api/v1/solicitud` con `{"id_solicitud": "<uuid>", "estado": "APROBADA" | "RECHAZADA"}`. Solo un **ASESOR**.

| Desde \ Hacia | Pendiente | Revision manual | Aprobada | Rechazada |
|--------------|-----------|-----------------|----------|-----------|
| Pendiente de revision | no | si | si | si |
| Revision manual | no | no | si | si |
| Rechazada | no | no | si (reconsiderada) | no |
| Aprobada | no | no | no | no |

Errores: `404 SOLICITUD_NO_ENCONTRADA`, `409 TRANSICION_ESTADO_INVALIDA`, `409 SOLICITUD_MODIFICADA` (otro asesor decidio al mismo
tiempo: el `UPDATE` solo aplica si el estado sigue siendo el que se leyo), `400` si el estado no es `APROBADA`/`RECHAZADA`.

**Como llega el correo sin perder avisos (outbox).** El cambio de estado y el evento `SOLICITUD_DECIDIDA` se guardan en la misma
transaccion (tabla `evento_saliente`); un relevo (`OutboxRelay`, cada `OUTBOX_INTERVALO`, `OUTBOX_LOTE` por ronda) los publica en la
cola `crediya-notificaciones`, de donde los lee la Lambda `lambda-notificaciones`, que publica el correo en el topico SNS
`crediya-correos`. Si la cola no responde, la decision queda guardada y el evento pendiente se reintenta (`intentos`,
`ultimo_error`). La entrega es *al menos una vez*; el contrato lleva `id_evento` para que los consumidores deduplique. Varias
instancias pueden correr el relevo a la vez (`FOR UPDATE SKIP LOCKED`). La consulta del nombre del solicitante a AUTENTICACION se
hace antes de abrir la transaccion; si AUTENTICACION no lo conoce, el correo sale sin nombre.

AWS: `adapter.sqs.*` se configura por variables de entorno (`AWS_REGION`, `AWS_ENDPOINT_URL` solo en LocalStack,
`SQS_NOTIFICACIONES_URL`). Las credenciales **no** estan en el codigo ni en `application.yaml`: el SDK las toma del rol de la tarea
(ECS), de variables de entorno o de un perfil. Permiso necesario: `sqs:SendMessage` sobre la cola de notificaciones.

## Validacion automatica de la capacidad de endeudamiento (HU7)

Los tipos de prestamo con `validacion_automatica` (PERSONAL y LIBRE_INVERSION en el catalogo sembrado) no esperan a un asesor:

1. Al crear la solicitud se guarda, **en la misma transaccion**, el evento `VALIDACION_AUTOMATICA_SOLICITADA` (outbox) con el salario y el
   nombre del solicitante y sus prestamos aprobados. El relevo lo publica en `crediya-validacion-automatica`.
2. La Lambda [`lambda-capacidad-endeudamiento`](../lambda-capacidad-endeudamiento) calcula (capacidad = 35% del salario, menos la deuda
   vigente; aprobada si la cuota cabe; revision manual si ademas el monto supera 5 salarios) y publica `DECISION_AUTOMATICA` en
   `crediya-decision-automatica`.
3. `SQSListener` (entry point `sqs-listener`) lee esa cola y `ProcesarDecisionAutomaticaUseCase` aplica el resultado:
   `APROBADA`/`RECHAZADA` cambian el estado y registran el aviso al solicitante (con el plan de pago si la aprobo); `REVISION_MANUAL`
   solo cambia el estado (la decision final es de un asesor y aparece en el listado de HU4).

Garantias: la cola entrega *al menos una vez*, por eso la decision es **idempotente** (solo se aplica si la solicitud sigue
`PENDIENTE_REVISION`; una repetida, o una que un asesor ya decidio a mano, se ignora sin duplicar el correo). El listener borra el
mensaje solo despues de procesarlo; si falla vuelve a la cola y tras 3 intentos va a `crediya-decision-automatica-dlq`. Si SQS no
responde, el listener reintenta con espera creciente en vez de morir. Los tipos sin validacion automatica (VEHICULO, HIPOTECARIO)
quedan pendientes para revision manual como en HU4.

| Variable | Para que |
|----------|----------|
| `SQS_VALIDACION_URL` | Cola donde se publican los pedidos de evaluacion |
| `SQS_DECISION_URL` | Cola que escucha el listener |
| `SQS_LISTENER_ENABLED` | `false` apaga el listener (pruebas o instancias que no deben consumir) |

Permisos de IAM de la tarea: `sqs:SendMessage` sobre las colas de notificaciones y validacion; `sqs:ReceiveMessage`, `sqs:DeleteMessage` y
`sqs:GetQueueAttributes` sobre la cola de decisiones.

## Registrar una solicitud (HU2)

El cliente se valida contra AUTENTICACION (`GET /api/v1/usuarios/{documento}`) y el email de la solicitud es el del cliente.

```bash
# TOKEN=<access_token de un CLIENTE, obtenido en POST /api/v1/login de AUTENTICACION>
curl -i -X POST http://localhost:8080/api/v1/solicitud -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "documento_identidad": "1020304050", "monto": 5000000, "plazo": 24, "tipo_prestamo": "PERSONAL"
}'
```

Tipos de prestamo sembrados por Flyway: `PERSONAL`, `LIBRE_INVERSION`, `VEHICULO`, `HIPOTECARIO` (la tasa es mensual).

| Codigo | Cuando |
|--------|--------|
| 201 | Solicitud registrada en estado `PENDIENTE_REVISION` |
| 400 | Campos invalidos, tipo de prestamo inexistente o monto fuera del rango del tipo |
| 401 | Falta el token o no es valido |
| 403 | El rol no es `CLIENTE` o el documento no es el del usuario autenticado |
| 404 | El cliente no esta registrado en AUTENTICACION |
| 503 | AUTENTICACION no responde (tras reintentos) o el circuit breaker esta abierto |
| 500 | Error inesperado (mensaje generico, sin detalles internos) |

## Docker: toda la aplicacion con un comando (HU5)

Requiere el repositorio `microservicio-autenticacion` clonado **al lado** de este (o `AUTH_REPO_PATH=/ruta/al/repo`).

```bash
./scripts/init-env.sh        # una vez: crea .env con credenciales y clave JWT aleatorias (no se versiona)
docker compose --env-file .env -f deployment/docker-compose.yml up --build
```

Levanta **SOLICITUDES** (http://localhost:8080), **AUTENTICACION** (http://localhost:8081), una PostgreSQL por servicio y
**LocalStack** (SQS, SNS y Lambda) en una red propia. Un servicio de un solo uso (`aws-init`) crea las colas con su DLQ, el topico
`crediya-correos`, un buzon para ver los correos y despliega la Lambda de notificaciones, compilada desde su repositorio
(`NOTIFICACIONES_REPO_PATH`, por defecto `../../lambda-notificaciones`). Las credenciales `test` del compose son ficticias y solo
valen contra LocalStack. Para ver el correo de una aprobacion:
`docker run --rm --network crediya_crediya-net -e AWS_ACCESS_KEY_ID=test -e AWS_SECRET_ACCESS_KEY=test amazon/aws-cli:2.27.50 --endpoint-url http://localstack:4566 --region us-east-1 sqs receive-message --queue-url http://localstack:4566/000000000000/crediya-correos-buzon --message-attribute-names All`.
Tambien despliega `lambda-capacidad-endeudamiento` (`CAPACIDAD_REPO_PATH`, por defecto `../../lambda-capacidad-endeudamiento`), conectada a su cola, y el
endpoint de consulta por API Gateway (la URL exacta la imprime `docker logs crediya-aws-init-1`):
`POST http://localhost:4566/restapis/<id>/local/_user_request_/api/v1/calcular-capacidad`.
LocalStack se fija en `4.9.2`: las imagenes `latest` posteriores exigen un `LOCALSTACK_AUTH_TOKEN`.
SOLICITUDES llama a AUTENTICACION por el nombre del servicio de compose (`http://autenticacion:8080`) y valida los tokens
con su JWKS; las bases de datos no se publican fuera de la red de compose.

| Que se valida | Como |
|---------------|------|
| Cada servicio corre en contenedor | Dockerfile multi-stage por servicio; la imagen final solo lleva JRE + jar, usuario sin privilegios (uid 10001), `java` como PID 1 |
| Las bases persisten al reiniciar | Volumenes con nombre: `docker compose down` los conserva, solo `down -v` los borra |
| Comportamiento identico al nativo | Misma configuracion por variables de entorno; los tests de extremo a extremo y esta prueba manual dan los mismos resultados |
| Arranque ordenado | `depends_on` con healthchecks (`pg_isready` y `/actuator/health/readiness`) |

Endurecimiento: sistema de archivos de solo lectura (con `/tmp` en tmpfs), sin capacidades de Linux, `no-new-privileges`, limite de
memoria y versiones de imagen fijadas (nada de `latest`). Los secretos se exigen por variable de entorno (`${VAR:?...}`): el
compose no arranca si falta alguno.

Escenario de prueba: el administrador (`BOOTSTRAP_ADMIN_*` del `.env`) inicia sesion en AUTENTICACION y registra un cliente y un
asesor; el cliente inicia sesion y crea su solicitud en SOLICITUDES; el asesor la ve en el listado con nombre y salario.

## Ejecutar localmente (sin Docker para la aplicacion)

Requisitos: Java 21 y Docker (PostgreSQL local y tests de integracion con Testcontainers).

```bash
docker run -d --name crediya-solicitudes-db -e POSTGRES_DB=crediya_solicitudes -e POSTGRES_USER=crediya \
  -e POSTGRES_PASSWORD=cambia-esta-clave -p 5432:5432 postgres:16-alpine
export DB_USERNAME=crediya DB_PASSWORD=cambia-esta-clave
export AUTH_SERVICE_URL=http://localhost:8081 AUTH_JWKS_URI=http://localhost:8081/.well-known/jwks.json
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

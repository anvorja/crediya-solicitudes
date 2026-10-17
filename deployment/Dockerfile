# syntax=docker/dockerfile:1.7
# Imagen del microservicio de SOLICITUDES. Construir desde la raiz del repositorio:
#   docker build -f deployment/Dockerfile -t crediya/solicitudes .

# ---------- 1) Compilacion: JDK completo + Gradle; no llega a la imagen final ----------
FROM eclipse-temurin:21.0.12_8-jdk AS build
WORKDIR /workspace
COPY . .
# La cache de Gradle se conserva entre construcciones sin quedar dentro de ninguna capa
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain bootJar -x test

# ---------- 2) Ejecucion: solo el JRE y el jar, usuario sin privilegios ----------
FROM eclipse-temurin:21.0.12_8-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -h /app app
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/applications/app-service/build/libs/*.jar /app/app.jar
# Paquete de CA de RDS (publico): permite DB_SSL_MODE=verify-full
COPY --chown=10001:10001 deployment/certs/rds-global-bundle.pem /app/certs/rds-global-bundle.pem

# El JVM lee JAVA_TOOL_OPTIONS por si mismo: no hace falta un script ni un shell como punto de entrada
# (asi java es el PID 1 y recibe SIGTERM para el apagado ordenado).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

package com.crediya.solicitudes.r2dbc;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Prepara un PostgreSQL real con las migraciones aplicadas para las pruebas de los adaptadores.
 */
public final class PostgresTestSupport {

    private PostgresTestSupport() {
    }

    public static void migrar(PostgreSQLContainer<?> postgres) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    public static ConnectionPool crearPool(PostgreSQLContainer<?> postgres) {
        var configuracion = PostgresqlConnectionConfiguration.builder()
                .host(postgres.getHost())
                .port(postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(postgres.getDatabaseName())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build();
        return new ConnectionPool(ConnectionPoolConfiguration.builder(new PostgresqlConnectionFactory(configuracion)).build());
    }
}

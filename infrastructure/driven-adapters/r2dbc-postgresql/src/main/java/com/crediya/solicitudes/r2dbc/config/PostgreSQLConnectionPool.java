// infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/com/crediya/solicitudes/r2dbc/config/PostgreSQLConnectionPool.java
package com.crediya.solicitudes.r2dbc.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.client.SSLMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PostgreSQLConnectionPool {
    /* Change these values for your project */
    public static final int INITIAL_SIZE = 12;
    public static final int MAX_SIZE = 15;
    public static final int MAX_IDLE_TIME = 30;
    public static final int DEFAULT_PORT = 5432;

	@Bean
	public ConnectionPool getConnectionConfig(PostgresqlConnectionProperties properties) {
		SSLMode sslMode = modoSsl(properties.sslMode());
		PostgresqlConnectionConfiguration.Builder dbBuilder = PostgresqlConnectionConfiguration.builder()
                .host(properties.host())
                .port(properties.port())
                .database(properties.database())
                .schema(properties.schema())
                .username(properties.username())
                .password(properties.password())
                .sslMode(sslMode);
        if (verificaCertificado(sslMode) && properties.sslRootCert() != null && !properties.sslRootCert().isBlank()) {
            dbBuilder.sslRootCert(properties.sslRootCert());
        }
        PostgresqlConnectionConfiguration dbConfiguration = dbBuilder.build();

        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder()
                .connectionFactory(new PostgresqlConnectionFactory(dbConfiguration))
                .name("api-postgres-connection-pool")
                .initialSize(INITIAL_SIZE)
                .maxSize(MAX_SIZE)
                .maxIdleTime(Duration.ofMinutes(MAX_IDLE_TIME))
                .validationQuery("SELECT 1")
                .build();

		return new ConnectionPool(poolConfiguration);
	}

    /** Solo los modos verify-* comprueban la CA: ahi se usa DB_SSL_ROOT_CERT (paquete de CA de RDS). */
    static boolean verificaCertificado(SSLMode modo) {
        return modo == SSLMode.VERIFY_CA || modo == SSLMode.VERIFY_FULL;
    }

    /** DB_SSL_MODE: disable (local), require (RDS exige conexion cifrada), verify-ca o verify-full. */
    static SSLMode modoSsl(String valor) {
        if (valor == null || valor.isBlank()) {
            return SSLMode.DISABLE;
        }
        return SSLMode.valueOf(valor.trim().toUpperCase().replace('-', '_'));
    }
}

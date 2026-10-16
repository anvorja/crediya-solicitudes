package com.crediya.solicitudes.r2dbc.config;

import io.r2dbc.postgresql.client.SSLMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class PostgreSQLConnectionPoolTest {

    @InjectMocks
    private PostgreSQLConnectionPool connectionPool;

    @Mock
    private PostgresqlConnectionProperties properties;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(properties.host()).thenReturn("localhost");
        when(properties.port()).thenReturn(5432);
        when(properties.database()).thenReturn("dbName");
        when(properties.schema()).thenReturn("schema");
        when(properties.username()).thenReturn("username");
        when(properties.password()).thenReturn("password");
    }

    @Test
    void getConnectionConfigSuccess() {
        assertNotNull(connectionPool.getConnectionConfig(properties));
    }

    @Test
    void sinValorSeDeshabilitaSsl() {
        assertEquals(SSLMode.DISABLE, PostgreSQLConnectionPool.modoSsl(null));
        assertEquals(SSLMode.DISABLE, PostgreSQLConnectionPool.modoSsl(" "));
    }

    @Test
    void aceptaLosModosEnFormatoJdbc() {
        assertEquals(SSLMode.REQUIRE, PostgreSQLConnectionPool.modoSsl("require"));
        assertEquals(SSLMode.VERIFY_FULL, PostgreSQLConnectionPool.modoSsl("verify-full"));
    }

    @Test
    void rechazaUnModoDesconocido() {
        assertThrows(IllegalArgumentException.class, () -> PostgreSQLConnectionPool.modoSsl("cualquiera"));
    }
}

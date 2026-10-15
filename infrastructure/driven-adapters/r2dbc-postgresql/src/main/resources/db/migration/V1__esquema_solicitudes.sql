-- Modelo relacional del microservicio SOLICITUDES (ver enunciado: estados, tipo_prestamo, solicitud)

CREATE TABLE estados (
    id_estado   SMALLINT     PRIMARY KEY,
    nombre      VARCHAR(30)  NOT NULL UNIQUE,
    descripcion VARCHAR(200)
);

INSERT INTO estados (id_estado, nombre, descripcion) VALUES
    (1, 'PENDIENTE_REVISION', 'Pendiente de revision'),
    (2, 'REVISION_MANUAL',    'Revision manual por un asesor'),
    (3, 'APROBADA',           'Solicitud aprobada'),
    (4, 'RECHAZADA',          'Solicitud rechazada');

-- tasa_interes: tasa mensual como fraccion (0.018 = 1.8% mensual)
CREATE TABLE tipo_prestamo (
    id_tipo_prestamo      SMALLINT      PRIMARY KEY,
    nombre                VARCHAR(50)   NOT NULL UNIQUE,
    monto_minimo          NUMERIC(15,2) NOT NULL,
    monto_maximo          NUMERIC(15,2) NOT NULL,
    tasa_interes          NUMERIC(7,6)  NOT NULL,
    validacion_automatica BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_tipo_prestamo_rango CHECK (monto_minimo > 0 AND monto_minimo <= monto_maximo),
    CONSTRAINT ck_tipo_prestamo_tasa CHECK (tasa_interes >= 0)
);

INSERT INTO tipo_prestamo (id_tipo_prestamo, nombre, monto_minimo, monto_maximo, tasa_interes, validacion_automatica) VALUES
    (1, 'PERSONAL',        500000.00,    50000000.00,   0.018000, TRUE),
    (2, 'LIBRE_INVERSION', 1000000.00,   30000000.00,   0.022000, TRUE),
    (3, 'VEHICULO',        10000000.00,  200000000.00,  0.012000, FALSE),
    (4, 'HIPOTECARIO',     50000000.00,  1000000000.00, 0.009000, FALSE);

CREATE TABLE solicitud (
    id_solicitud     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    monto            NUMERIC(15,2) NOT NULL,
    plazo            INTEGER       NOT NULL,
    email            VARCHAR(255)  NOT NULL,
    id_estado        SMALLINT      NOT NULL REFERENCES estados (id_estado),
    id_tipo_prestamo SMALLINT      NOT NULL REFERENCES tipo_prestamo (id_tipo_prestamo),
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_solicitud_monto CHECK (monto > 0),
    CONSTRAINT ck_solicitud_plazo CHECK (plazo BETWEEN 1 AND 360)
);

CREATE INDEX ix_solicitud_email ON solicitud (lower(email));
CREATE INDEX ix_solicitud_estado_creado ON solicitud (id_estado, creado_en DESC);

-- Outbox transaccional: el evento se guarda en la misma transaccion que el cambio de estado y un relevo lo publica despues
CREATE TABLE evento_saliente (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    destino      VARCHAR(30)  NOT NULL,
    payload      TEXT         NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviado_en   TIMESTAMPTZ,
    intentos     INTEGER      NOT NULL DEFAULT 0,
    ultimo_error VARCHAR(300)
);

-- Solo los pendientes, en orden de llegada
CREATE INDEX ix_evento_saliente_pendientes ON evento_saliente (creado_en) WHERE enviado_en IS NULL;

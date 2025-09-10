// infrastructure/entry-points/reactive-web/src/main/java/com/crediya/solicitudes/api/constants/ValidationMessagesConstants.java
package com.crediya.solicitudes.api.constants;

public class ValidationMessagesConstants {

    private ValidationMessagesConstants() {
        // Prevenir instanciación
    }

    // Mensajes de validación de datos de entrada
    public static final class Validation {
        private Validation() {}

        // Documento
        public static final String DOCUMENTO_REQUERIDO = "El número de documento es obligatorio";
        public static final String DOCUMENTO_LONGITUD = "El número de documento debe tener entre 6 y 15 caracteres";
        public static final String DOCUMENTO_FORMATO = "El número de documento solo debe contener números";

        // Nombres y apellidos
        public static final String NOMBRES_REQUERIDOS = "Los nombres son obligatorios";
        public static final String NOMBRES_LONGITUD = "Los nombres no pueden exceder 100 caracteres";
        public static final String NOMBRES_FORMATO = "Los nombres solo pueden contener letras y espacios";
        public static final String APELLIDOS_REQUERIDOS = "Los apellidos son obligatorios";
        public static final String APELLIDOS_LONGITUD = "Los apellidos no pueden exceder 100 caracteres";
        public static final String APELLIDOS_FORMATO = "Los apellidos solo pueden contener letras y espacios";

        // Email
        public static final String EMAIL_REQUERIDO = "El email es obligatorio";
        public static final String EMAIL_FORMATO = "El formato del email no es válido";
        public static final String EMAIL_LONGITUD = "El email no puede exceder 150 caracteres";

        // Teléfono
        public static final String TELEFONO_REQUERIDO = "El teléfono es obligatorio";
        public static final String TELEFONO_FORMATO = "El teléfono debe tener entre 10 y 15 dígitos";

        // Monto
        public static final String MONTO_REQUERIDO = "El monto solicitado es obligatorio";
        public static final String MONTO_MINIMO = "El monto mínimo a solicitar es $100,000";
        public static final String MONTO_MAXIMO = "El monto máximo a solicitar es $50,000,000";
        public static final String MONTO_FORMATO = "El monto debe ser un valor numérico válido";

        // Plazo
        public static final String PLAZO_REQUERIDO = "El plazo en meses es obligatorio";
        public static final String PLAZO_MINIMO = "El plazo mínimo es 6 meses";
        public static final String PLAZO_MAXIMO = "El plazo máximo es 72 meses";

        // Tipo de crédito
        public static final String TIPO_CREDITO_REQUERIDO = "El tipo de crédito es obligatorio";
        public static final String TIPO_CREDITO_INVALIDO = "El tipo de crédito debe ser: PERSONAL, VEHICULO, VIVIENDA o EDUCATIVO";

        // Información financiera
        public static final String INGRESOS_NEGATIVOS = "Los ingresos mensuales no pueden ser negativos";
        public static final String INGRESOS_FORMATO = "Los ingresos deben ser un valor numérico válido";
        public static final String GASTOS_NEGATIVOS = "Los gastos mensuales no pueden ser negativos";
        public static final String GASTOS_FORMATO = "Los gastos deben ser un valor numérico válido";
    }

    // Mensajes de lógica de negocio
    public static final class Business {
        private Business() {}

        public static final String SOLICITUD_DUPLICADA = "Ya existe una solicitud activa para el documento: %s";
        public static final String SOLICITUD_NO_ENCONTRADA = "Solicitud no encontrada: %s";
        public static final String TRANSICION_ESTADO_INVALIDA = "No se puede cambiar de %s a %s";
        public static final String DATOS_DOCUMENTO_VERIFICACION_MANUAL = "Datos del documento requieren verificación manual";
        public static final String HISTORIAL_CREDITICIO_REVISION = "Historial crediticio requiere revisión (Score: %d)";
    }

    // Mensajes de éxito
    public static final class Success {
        private Success() {}

        public static final String SOLICITUD_CREADA = "Solicitud creada exitosamente";
        public static final String SOLICITUD_ACTUALIZADA = "Solicitud actualizada exitosamente";
        public static final String SOLICITUD_PROCESADA = "Solicitud procesada correctamente";
    }

    // Mensajes de error general
    public static final class Error {
        private Error() {}

        public static final String ERROR_INTERNO = "Ha ocurrido un error inesperado. Por favor contacte al administrador.";
        public static final String DATOS_INVALIDOS = "Los datos proporcionados no cumplen con las validaciones requeridas";
        public static final String ACCESO_DENEGADO = "Acceso denegado para esta operación";
        public static final String RECURSO_NO_ENCONTRADO = "El recurso solicitado no fue encontrado";
    }

    // Mensajes de log
    public static final class Log {
        private Log() {}

        public static final String INICIANDO_CREACION_SOLICITUD = "Iniciando creación de solicitud para documento: {}";
        public static final String SOLICITUD_CREADA_EXITOSAMENTE = "Solicitud creada exitosamente: {} - Estado: {}";
        public static final String ERROR_CREANDO_SOLICITUD = "Error creando solicitud para documento {}: {}";
        public static final String PROCESANDO_SOLICITUD = "Procesando solicitud para documento: {}";
        public static final String CONSULTANDO_SOLICITUD = "Consultando solicitud: {}";
        public static final String CONSULTANDO_POR_DOCUMENTO = "Consultando solicitud por documento: {}";
    }
}

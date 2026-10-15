package com.crediya.solicitudes.api.constants;

public final class ValidationMessages {

    public static final String DOCUMENTO_REQUERIDO = "El documento de identidad es obligatorio";
    public static final String DOCUMENTO_FORMATO = "El documento de identidad debe tener entre 5 y 20 digitos";
    public static final String MONTO_REQUERIDO = "El monto es obligatorio";
    public static final String MONTO_POSITIVO = "El monto debe ser mayor a cero";
    public static final String PLAZO_REQUERIDO = "El plazo en meses es obligatorio";
    public static final String PLAZO_MINIMO = "El plazo debe ser de al menos 1 mes";
    public static final String PLAZO_MAXIMO = "El plazo no puede superar 360 meses";
    public static final String TIPO_PRESTAMO_REQUERIDO = "El tipo de prestamo es obligatorio";
    public static final String ID_SOLICITUD_REQUERIDO = "El identificador de la solicitud es obligatorio";
    public static final String ESTADO_REQUERIDO = "El estado es obligatorio";
    public static final String ENTERO_INVALIDO = "Debe ser un numero entero";
    public static final String CUERPO_REQUERIDO = "El cuerpo de la peticion es obligatorio";
    public static final String CAMPO_CUERPO = "cuerpo";

    private ValidationMessages() {
    }
}

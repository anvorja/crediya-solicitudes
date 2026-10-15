#!/bin/sh
# Prepara en LocalStack lo que en AWS se crearia aparte: colas (cada una con su DLQ), el topico de correos y un "buzon" para
# ver los correos, las dos Lambdas conectadas a sus colas y el endpoint de consulta de capacidad (API Gateway).
# Es idempotente: se puede repetir. Las credenciales son ficticias y solo valen contra LocalStack.
set -eu

AWS="aws --endpoint-url ${LOCALSTACK_URL:-http://localstack:4566} --region ${AWS_REGION:-us-east-1}"
CUENTA=000000000000
REGION="${AWS_REGION:-us-east-1}"
ENDPOINT_PARA_LAMBDAS="${LAMBDA_AWS_ENDPOINT_URL:-http://localstack:4566}"
JAR_NOTIFICACIONES="${LAMBDA_NOTIFICACIONES_JAR:-/artifacts/lambda-notificaciones.jar}"
JAR_CAPACIDAD="${LAMBDA_CAPACIDAD_JAR:-/artifacts/lambda-capacidad-endeudamiento.jar}"
ROL="arn:aws:iam::$CUENTA:role/lambda-local"

crear_cola_con_dlq() {
  nombre="$1"
  $AWS sqs create-queue --queue-name "$nombre-dlq" >/dev/null
  dlq_arn="arn:aws:sqs:$REGION:$CUENTA:$nombre-dlq"
  # Visibilidad de 60 s (> timeout de la Lambda de 30 s); tras 3 intentos el mensaje va a la DLQ
  $AWS sqs create-queue --queue-name "$nombre" --attributes "{
    \"VisibilityTimeout\":\"60\",
    \"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$dlq_arn\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"
  }" >/dev/null
  echo "cola $nombre lista"
}

# desplegar_lambda <nombre> <jar> <handler> <variables>
desplegar_lambda() {
  nombre="$1"; jar="$2"; handler="$3"; variables="$4"
  [ -f "$jar" ] || { echo "No se encontro $jar: no se puede desplegar $nombre" >&2; exit 1; }
  $AWS lambda delete-function --function-name "$nombre" >/dev/null 2>&1 || true
  $AWS lambda create-function --function-name "$nombre" --runtime java21 --handler "$handler" \
    --role "$ROL" --timeout 30 --memory-size 512 --zip-file "fileb://$jar" --environment "Variables={$variables}" >/dev/null
  $AWS lambda wait function-active-v2 --function-name "$nombre"
  echo "lambda $nombre desplegada"
}

# conectar_cola <lambda> <cola>: la Lambda consume la cola y reporta fallos por mensaje
conectar_cola() {
  for uuid in $($AWS lambda list-event-source-mappings --function-name "$1" --query 'EventSourceMappings[].UUID' --output text); do
    $AWS lambda delete-event-source-mapping --uuid "$uuid" >/dev/null
  done
  $AWS lambda create-event-source-mapping --function-name "$1" --event-source-arn "arn:aws:sqs:$REGION:$CUENTA:$2" \
    --batch-size 10 --function-response-types ReportBatchItemFailures >/dev/null
  echo "lambda $1 conectada a la cola $2"
}

crear_cola_con_dlq crediya-notificaciones
crear_cola_con_dlq crediya-validacion-automatica
crear_cola_con_dlq crediya-decision-automatica

TOPIC_ARN=$($AWS sns create-topic --name crediya-correos --query TopicArn --output text)
$AWS sqs create-queue --queue-name crediya-correos-buzon >/dev/null
$AWS sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
  --notification-endpoint "arn:aws:sqs:$REGION:$CUENTA:crediya-correos-buzon" --attributes RawMessageDelivery=true >/dev/null
echo "topico $TOPIC_ARN con buzon crediya-correos-buzon"

# HU6: notificaciones
desplegar_lambda crediya-notificaciones "$JAR_NOTIFICACIONES" \
  com.crediya.notificaciones.infrastructure.entrypoint.NotificacionesHandler::handleRequest \
  "CORREOS_TOPIC_ARN=$TOPIC_ARN,AWS_ENDPOINT_URL=$ENDPOINT_PARA_LAMBDAS"
conectar_cola crediya-notificaciones crediya-notificaciones

# HU7: capacidad de endeudamiento (por cola) y su endpoint de consulta (por API Gateway)
VARIABLES_CAPACIDAD="COLA_DECISIONES_URL=$ENDPOINT_PARA_LAMBDAS/$CUENTA/crediya-decision-automatica,AWS_ENDPOINT_URL=$ENDPOINT_PARA_LAMBDAS"
desplegar_lambda crediya-capacidad-endeudamiento "$JAR_CAPACIDAD" \
  com.crediya.capacidad.infrastructure.entrypoint.ValidacionSqsHandler::handleRequest "$VARIABLES_CAPACIDAD"
conectar_cola crediya-capacidad-endeudamiento crediya-validacion-automatica
desplegar_lambda crediya-calcular-capacidad "$JAR_CAPACIDAD" \
  com.crediya.capacidad.infrastructure.entrypoint.CalculoApiHandler::handleRequest "$VARIABLES_CAPACIDAD"

for api in $($AWS apigateway get-rest-apis --query "items[?name=='crediya-capacidad'].id" --output text); do
  $AWS apigateway delete-rest-api --rest-api-id "$api" >/dev/null
done
API_ID=$($AWS apigateway create-rest-api --name crediya-capacidad --query id --output text)
RAIZ=$($AWS apigateway get-resources --rest-api-id "$API_ID" --query 'items[0].id' --output text)
padre="$RAIZ"
for segmento in api v1 calcular-capacidad; do
  padre=$($AWS apigateway create-resource --rest-api-id "$API_ID" --parent-id "$padre" --path-part "$segmento" --query id --output text)
done
$AWS apigateway put-method --rest-api-id "$API_ID" --resource-id "$padre" --http-method POST --authorization-type NONE >/dev/null
$AWS apigateway put-integration --rest-api-id "$API_ID" --resource-id "$padre" --http-method POST --type AWS_PROXY \
  --integration-http-method POST \
  --uri "arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$REGION:$CUENTA:function:crediya-calcular-capacidad/invocations" >/dev/null
$AWS apigateway create-deployment --rest-api-id "$API_ID" --stage-name local >/dev/null
echo "API de consulta: POST http://localhost:4566/restapis/$API_ID/local/_user_request_/api/v1/calcular-capacidad"

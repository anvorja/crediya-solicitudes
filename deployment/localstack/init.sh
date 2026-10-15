#!/bin/sh
# Prepara en LocalStack lo que en AWS se crearia aparte: colas (con su DLQ), el topico de correos, un "buzon" que recibe
# los correos para poder verlos, y la Lambda de notificaciones conectada a su cola. Es idempotente: se puede repetir.
# Las credenciales son ficticias y solo valen contra LocalStack.
set -eu

AWS="aws --endpoint-url ${LOCALSTACK_URL:-http://localstack:4566} --region ${AWS_REGION:-us-east-1}"
CUENTA=000000000000
REGION="${AWS_REGION:-us-east-1}"
LAMBDA_JAR="${LAMBDA_NOTIFICACIONES_JAR:-/artifacts/lambda-notificaciones.jar}"

crear_cola_con_dlq() {
  nombre="$1"
  $AWS sqs create-queue --queue-name "$nombre-dlq" >/dev/null
  dlq_arn="arn:aws:sqs:$REGION:$CUENTA:$nombre-dlq"
  # El tiempo de visibilidad (60 s) supera el timeout de la Lambda (30 s); tras 3 intentos el mensaje va a la DLQ
  $AWS sqs create-queue --queue-name "$nombre" --attributes "{
    \"VisibilityTimeout\":\"60\",
    \"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$dlq_arn\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"
  }" >/dev/null
  echo "cola $nombre lista"
}

crear_cola_con_dlq crediya-notificaciones

TOPIC_ARN=$($AWS sns create-topic --name crediya-correos --query TopicArn --output text)
$AWS sqs create-queue --queue-name crediya-correos-buzon >/dev/null
BUZON_ARN="arn:aws:sqs:$REGION:$CUENTA:crediya-correos-buzon"
$AWS sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$BUZON_ARN" \
  --attributes RawMessageDelivery=true >/dev/null
echo "topico $TOPIC_ARN con buzon crediya-correos-buzon"

if [ ! -f "$LAMBDA_JAR" ]; then
  echo "No se encontro $LAMBDA_JAR: la Lambda de notificaciones no se despliega" >&2
  exit 1
fi

$AWS lambda delete-function --function-name crediya-notificaciones >/dev/null 2>&1 || true
$AWS lambda create-function --function-name crediya-notificaciones --runtime java21 \
  --handler com.crediya.notificaciones.infrastructure.entrypoint.NotificacionesHandler::handleRequest \
  --role "arn:aws:iam::$CUENTA:role/lambda-local" --timeout 30 --memory-size 512 \
  --zip-file "fileb://$LAMBDA_JAR" \
  --environment "Variables={CORREOS_TOPIC_ARN=$TOPIC_ARN,AWS_ENDPOINT_URL=${LAMBDA_AWS_ENDPOINT_URL:-http://localstack:4566}}" >/dev/null
$AWS lambda wait function-active-v2 --function-name crediya-notificaciones

for uuid in $($AWS lambda list-event-source-mappings --function-name crediya-notificaciones --query 'EventSourceMappings[].UUID' --output text); do
  $AWS lambda delete-event-source-mapping --uuid "$uuid" >/dev/null
done
$AWS lambda create-event-source-mapping --function-name crediya-notificaciones \
  --event-source-arn "arn:aws:sqs:$REGION:$CUENTA:crediya-notificaciones" --batch-size 10 \
  --function-response-types ReportBatchItemFailures >/dev/null
echo "lambda crediya-notificaciones conectada a la cola crediya-notificaciones"

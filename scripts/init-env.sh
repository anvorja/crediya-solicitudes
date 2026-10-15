#!/usr/bin/env bash
# Crea el archivo .env con credenciales aleatorias y una clave JWT nueva si todavia no existe.
# No sobreescribe un .env existente: ahi pueden estar las claves de tus datos actuales.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  echo ".env ya existe: no se modifica."
  exit 0
fi

aleatoria() { openssl rand -base64 24 | tr -d '/+=' | cut -c1-24; }

{
  echo "# Generado por scripts/init-env.sh. No lo subas al repositorio."
  echo "# Autenticacion"
  echo "AUTH_DB_USERNAME=crediya_auth"
  echo "AUTH_DB_PASSWORD=$(aleatoria)"
  echo "AUTH_PORT=8081"
  echo "JWT_PRIVATE_KEY=$(./scripts/generate-jwt-key.sh)"
  echo "BOOTSTRAP_ADMIN_EMAIL=admin@crediya.local"
  echo "BOOTSTRAP_ADMIN_PASSWORD=$(aleatoria)"
  echo "BOOTSTRAP_ADMIN_DOCUMENTO=0000000001"
  echo "# Solicitudes"
  echo "SOLICITUDES_DB_USERNAME=crediya_solicitudes"
  echo "SOLICITUDES_DB_PASSWORD=$(aleatoria)"
  echo "SOLICITUDES_PORT=8080"
} > .env
chmod 600 .env
echo ".env creado. La clave del administrador inicial esta en BOOTSTRAP_ADMIN_PASSWORD."

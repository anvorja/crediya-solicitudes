#!/usr/bin/env bash
# Genera una clave privada RSA 2048 en formato PKCS#8 (DER) codificada en base64, lista para la variable JWT_PRIVATE_KEY.
# Ojo: "openssl genpkey -outform DER" produce PKCS#1, que la aplicacion no acepta; por eso se convierte con "pkcs8 -topk8".
# No guardes la salida en el repositorio: va a un gestor de secretos (AWS Secrets Manager, Vault...).
set -euo pipefail
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null \
  | openssl pkcs8 -topk8 -nocrypt -outform DER \
  | base64 -w0
echo

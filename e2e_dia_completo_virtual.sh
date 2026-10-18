#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

command -v docker >/dev/null || { echo "Falta Docker" >&2; exit 1; }
command -v Xvfb >/dev/null || { echo "Falta Xvfb (apt install xvfb)" >&2; exit 1; }
command -v xdotool >/dev/null || { echo "Falta xdotool" >&2; exit 1; }
command -v ant >/dev/null || { echo "Falta Apache Ant" >&2; exit 1; }
command -v java >/dev/null || { echo "Falta Java 21" >&2; exit 1; }

if (: >/dev/tcp/127.0.0.1/3307) 2>/dev/null; then
    echo "El puerto 3307 ya está ocupado; no se detendrá ningún contenedor existente." >&2
    exit 1
fi

PROYECTO="restaurante-dia-e2e-$$-$(date +%s)"
COMPOSE=(docker compose -p "$PROYECTO" -f docker-compose.integration.yml)
DIRECTORIO_E2E="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-dia-e2e-XXXXXX")"
mkdir -p "$DIRECTORIO_E2E/bin" "$DIRECTORIO_E2E/facturas"
cat > "$DIRECTORIO_E2E/bin/lp" <<'LP'
#!/bin/sh
exit 0
LP
chmod +x "$DIRECTORIO_E2E/bin/lp"

limpiar() {
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    rm -rf -- "$DIRECTORIO_E2E"
}
trap limpiar EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

export DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER='restaurante_test_app'
export E2E_MYSQL_PASSWORD="${E2E_MYSQL_PASSWORD:-test_only_app_password}"
export DB_PASSWORD="$E2E_MYSQL_PASSWORD"
export DB_NAME='restaurante_test'
export MYSQL_DATABASE='restaurante_test'
export BACKUP_HMAC_KEY='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
export TZ='UTC'
export E2E_WORKDIR="$DIRECTORIO_E2E"
export E2E_BD_DESECHABLE='SI'
export PDF_FACTURAS_DIR="$DIRECTORIO_E2E/facturas"
export PATH="$DIRECTORIO_E2E/bin:$PATH"
export CUPS_SERVER='127.0.0.1:65534'
export ACCION_IMPRESION='IMPRIMIR'

"${COMPOSE[@]}" up -d --wait
CLASE="${1:-Vista.DiaCompletoFiscalE2EVirtual}"
if (($# > 0)); then shift; fi
./e2e_virtual.sh "$CLASE" "$@"

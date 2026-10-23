#!/usr/bin/env bash
#
# e2e_dia_completo_virtual.sh — arnés diario E2E sobre BD desechable schema-only.
#
# Flujo (todo sembrado por la interfaz, Xvfb :99, mouse/teclado):
#   1. MySQL efímero con `db/e2e/001-esquema.sql` (esquema oficial sin datos).
#   2. Vista.BootstrapInicialE2EVirtual (solo si la BD aún no arrancó bootstrap).
#   3. Vista.SeedBaseE2EVirtual (config/sala/mesas/plato base, idempotente).
#   4. (opcional E2E_SEED_PLATOS=108) Vista.SeedPlatosCombinatoriaE2EVirtual.
#   5. La clase pedida (default: Vista.DiaCompletoFiscalE2EVirtual).
#
# Uso:
#   ./e2e_dia_completo_virtual.sh                 # día fiscal completo
#   ./e2e_dia_completo_virtual.sh Vista.E2EVirtual
#   E2E_SEED_PLATOS=108 ./e2e_dia_completo_virtual.sh
#   E2E_MANTENER_BD=SI ./e2e_dia_completo_virtual.sh   # conserva BD para inspección
#
# Cierre de test: con E2E_MANTENER_BD=SI la BD se reinicializa (esquema vacío)
# y el contenedor queda arriba para inspección; sin ella, down --volumes.
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
COMPOSE=(docker compose -p "$PROYECTO" -f docker-compose.e2e.yml)
DIRECTORIO_E2E="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-dia-e2e-XXXXXX")"
mkdir -p "$DIRECTORIO_E2E/bin" "$DIRECTORIO_E2E/facturas"
cat > "$DIRECTORIO_E2E/bin/lp" <<'LP'
#!/bin/sh
exit 0
LP
chmod +x "$DIRECTORIO_E2E/bin/lp"

limpiar() {
    local exit_code=$?
    if [[ "${E2E_MANTENER_BD:-NO}" == "SI" && "$exit_code" == 0 ]]; then
        # Cierre de test = inicializar la BD (esquema vacío) y dejar contenedor arriba.
        if [[ -x ./inicializar_bd_test.sh ]]; then
            ./inicializar_bd_test.sh >/dev/null 2>&1 || true
        fi
        echo "BD mantenida (proyecto=$PROYECTO): inspeccionar y luego cerrar con:" >&2
        echo "  docker compose -p $PROYECTO -f docker-compose.e2e.yml down --volumes" >&2
        echo "Workdir conservado (montaje de esquema necesario para inicializar_bd_test.sh): $DIRECTORIO_E2E" >&2
        return "$exit_code"
    fi
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
export E2E_LOGIN_USERNAME='admin'
export E2E_LOGIN_PASSWORD='E2EBootstrap2026X'
export PATH="$DIRECTORIO_E2E/bin:$PATH"
export CUPS_SERVER='127.0.0.1:65534'
export ACCION_IMPRESION='IMPRIMIR'

"${COMPOSE[@]}" up -d --wait

# Bootstrap por UI únicamente si esta BD todavía no completó el asistente
# (decisión del arnés; la creación de la cuenta siempre ocurre por la interfaz).
ESTADO_BOOTSTRAP="$("${COMPOSE[@]}" exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT valor FROM configuracion_sistema WHERE clave = '"'"'bootstrap_inicial_completado'"'"'" restaurante_test 2>/dev/null || true')"
if [[ "$ESTADO_BOOTSTRAP" != "1" ]]; then
    ./e2e_virtual.sh Vista.BootstrapInicialE2EVirtual
fi

./e2e_virtual.sh Vista.SeedBaseE2EVirtual

if [[ "${E2E_SEED_PLATOS:-NO}" == "108" ]]; then
    ./e2e_virtual.sh Vista.SeedPlatosCombinatoriaE2EVirtual
fi

CLASE="${1:-Vista.DiaCompletoFiscalE2EVirtual}"
if (($# > 0)); then shift; fi
./e2e_virtual.sh "$CLASE" "$@"

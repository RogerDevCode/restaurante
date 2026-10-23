#!/usr/bin/env bash
#
# e2e_bootstrap_virtual.sh — bootstrap E2E sobre Schema-only (sin datos SQL).
#
# Levanta MySQL efímero con `db/e2e/001-esquema.sql` (derivado byte a byte de
# BD.sql; SIN filas) y ejecuta por la interfaz (Xvfb :99, mouse/teclado):
#   1. Vista.BootstrapInicialE2EVirtual — bootstrap inicial de la app por UI.
#   2. (SI E2E_BOOTSTRAP_FULL_DAY=SI)  Vista.SeedBaseE2EVirtual + Vista.DiaCompletoFiscalE2EVirtual
#   3. (si no)                         Vista.SeedBaseE2EVirtual + E2EVirtual + PedidoRapidoE2EVirtual
#   4. (opcional E2E_SEED_PLATOS=108)  Vista.SeedPlatosCombinatoriaE2EVirtual — 108 platos por UI
#
# credenciales de demo creadas por el bootstrap y exportadas para las demás
# clases (nunca magic admin/admin dentro de los tests; el bootstrap mantiene
# su prueba negativa con la demo bloqueada):
#   E2E_LOGIN_USERNAME/PASSWORD = admin / E2EBootstrap2026X
#
# Con E2E_MANTENER_BD=SI el contenedor queda arriba para inspección; al
# cerrar la inspección: ./inicializar_bd_test.sh y
# docker compose -p <proyecto> -f docker-compose.e2e.yml down --volumes
set -euo pipefail

cd "$(dirname "$0")"
ROOT="$(pwd)"

for dependency in docker Xvfb xdotool ant java; do
    command -v "$dependency" >/dev/null || { echo "Falta dependencia: $dependency" >&2; exit 1; }
done

if (: >/dev/tcp/127.0.0.1/3307) 2>/dev/null; then
    echo "El puerto 3307 ya está ocupado; no se detendrá ningún servicio existente." >&2
    exit 1
fi

PROJECT="restaurante-bootstrap-e2e-$$-$(date +%s)"
WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-bootstrap-e2e-XXXXXX")"
mkdir -p "$WORKDIR/bin" "$WORKDIR/facturas"
cat > "$WORKDIR/bin/lp" <<'LP'
#!/bin/sh
exit 0
LP
chmod +x "$WORKDIR/bin/lp"

COMPOSE_FILE="$WORKDIR/compose.yml"
cat > "$COMPOSE_FILE" <<EOF
services:
  db:
    image: mysql:8.4.11
    environment:
      MYSQL_DATABASE: restaurante_test
      MYSQL_ROOT_PASSWORD: test_root_pass
    command: --log-bin-trust-function-creators=1
    ports:
      - "127.0.0.1:3307:3306"
    volumes:
      - bootstrap_mysql_data:/var/lib/mysql
      - type: bind
        source: "$WORKDIR/001-esquema.sql"
        target: /docker-entrypoint-initdb.d/001-esquema.sql
        read_only: true
      - type: bind
        source: "$WORKDIR/002-privilegios.sql"
        target: /docker-entrypoint-initdb.d/002-privilegios.sql
        read_only: true
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping --host=127.0.0.1 --user=root --password=\"\$\${MYSQL_ROOT_PASSWORD}\" --silent"]
      interval: 5s
      timeout: 5s
      retries: 24
      start_period: 30s
volumes:
  bootstrap_mysql_data:
EOF
# Esquema oficial SIN datos + privilegios mínimos (todo bajo git, sin seeds).
cp db/e2e/001-esquema.sql "$WORKDIR/"
cp db/e2e/002-privilegios.sql "$WORKDIR/"
COMPOSE=(docker compose -p "$PROJECT" -f "$COMPOSE_FILE")

cleanup() {
    local exit_code=$?
    if (( exit_code != 0 )); then
        "${COMPOSE[@]}" logs --no-color db >&2 || true
    fi
    if [[ "${E2E_MANTENER_BD:-NO}" == "SI" && "$exit_code" == 0 ]]; then
        # Cierre de test = inicializar la BD (esquema vacío) y dejar contenedor arriba.
        if [[ -x ./inicializar_bd_test.sh ]]; then
            ./inicializar_bd_test.sh >/dev/null 2>&1 || true
        fi
        echo "BD mantenida (proyecto=$PROJECT): inspeccionar y luego cerrar con:" >&2
        echo "  docker compose -p $PROJECT -f $COMPOSE_FILE down --volumes" >&2
        echo "Workdir conservado (montaje de esquema necesario para inicializar_bd_test.sh): $WORKDIR" >&2
        return "$exit_code"
    fi
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    rm -rf -- "$WORKDIR"
    return "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

export DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER='restaurante_test_app'
export DB_PASSWORD='test_only_app_password'
export DB_NAME='restaurante_test'
export MYSQL_DATABASE='restaurante_test'
export BACKUP_HMAC_KEY='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
export TZ='UTC'
export E2E_BD_DESECHABLE='SI'
export E2E_WORKDIR="$WORKDIR"
export E2E_DIAG_SCREENSHOT="${TMPDIR:-/tmp}/restaurante-e2e-historial-$$.png"
export PDF_FACTURAS_DIR="$WORKDIR/facturas"
export E2E_LOGIN_USERNAME='admin'
export E2E_LOGIN_PASSWORD='E2EBootstrap2026X'
export PATH="$WORKDIR/bin:$PATH"
export CUPS_SERVER='127.0.0.1:65534'
export ACCION_IMPRESION='IMPRIMIR'

"${COMPOSE[@]}" up -d --wait
./e2e_virtual.sh Vista.BootstrapInicialE2EVirtual
./e2e_virtual.sh Vista.SeedBaseE2EVirtual
if [[ "${E2E_SEED_PLATOS:-NO}" == "108" ]]; then
    ./e2e_virtual.sh Vista.SeedPlatosCombinatoriaE2EVirtual
fi
if [[ "${E2E_BOOTSTRAP_FULL_DAY:-NO}" == "SI" ]]; then
    ./e2e_virtual.sh Vista.DiaCompletoFiscalE2EVirtual
else
    ./e2e_virtual.sh Vista.E2EVirtual
    ./e2e_virtual.sh Vista.PedidoRapidoE2EVirtual
fi

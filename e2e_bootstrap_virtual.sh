#!/usr/bin/env bash
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
cat > "$WORKDIR/002-runtime-privileges.sql" <<'SQL'
DROP USER IF EXISTS 'restaurante_test_app'@'%';
CREATE USER 'restaurante_test_app'@'%' IDENTIFIED BY 'test_only_app_password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON restaurante_test.* TO 'restaurante_test_app'@'%';
SQL
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
      MYSQL_USER: restaurante_test_app
      MYSQL_PASSWORD: test_only_app_password
      MYSQL_ROOT_PASSWORD: test_root_pass
    command: --log-bin-trust-function-creators=1
    ports:
      - "127.0.0.1:3307:3306"
    volumes:
      - bootstrap_mysql_data:/var/lib/mysql
      - type: bind
        source: "$ROOT/BD.sql"
        target: /docker-entrypoint-initdb.d/001-restaurante.sql
        read_only: true
      - type: bind
        source: "$WORKDIR/002-runtime-privileges.sql"
        target: /docker-entrypoint-initdb.d/002-runtime-privileges.sql
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

COMPOSE=(docker compose -p "$PROJECT" -f "$COMPOSE_FILE")
cleanup() {
    local exit_code=$?
    if (( exit_code != 0 )); then
        "${COMPOSE[@]}" logs --no-color db >&2 || true
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
export E2E_BD_DESECHABLE='SI'
export E2E_WORKDIR="$WORKDIR"
export E2E_DIAG_SCREENSHOT="${TMPDIR:-/tmp}/restaurante-e2e-historial-$$.png"
export PDF_FACTURAS_DIR="$WORKDIR/facturas"
export TZ='UTC'
export PATH="$WORKDIR/bin:$PATH"
export CUPS_SERVER='127.0.0.1:65534'
export ACCION_IMPRESION='IMPRIMIR'

"${COMPOSE[@]}" up -d --wait
./e2e_virtual.sh Vista.BootstrapInicialE2EVirtual
export E2E_LOGIN_USERNAME='admin'
export E2E_LOGIN_PASSWORD='E2EBootstrap2026X'
if [[ "${E2E_BOOTSTRAP_FULL_DAY:-NO}" == "SI" ]]; then
    ./e2e_virtual.sh Vista.DiaCompletoFiscalE2EVirtual
else
    ./e2e_virtual.sh Vista.E2EVirtual
    ./e2e_virtual.sh Vista.PedidoRapidoE2EVirtual
fi

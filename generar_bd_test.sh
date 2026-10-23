#!/usr/bin/env bash
#
# generar_bd_test.sh — genera BD-test.sql sembrando TODO por la interfaz.
#
# Flujo (evolución de e2e_bootstrap_virtual.sh sobre BD schema-only en 3307):
#   1. MySQL efímero con `db/e2e/001-esquema.sql` (esquema oficial sin filas).
#   2. Vista.BootstrapInicialE2EVirtual — bootstrap inicial por UI.
#   3. Vista.SeedBaseE2EVirtual — config (tasa 40 / IVA 16) + Sala Principal (2
#      mesas) + categoría E2ESEED-CAT + plato base E2ESEED-POLLO (USO 5.00).
#   4. Vista.SeedPlatosCombinatoriaE2EVirtual — 108 platos (18x6 USD/VES x
#      EXENTO/IVA-APARTE/IVA-INCLUIDO) con aumenta +0.50 USD / +20.00 VES.
#   5. Recolección data-only con mysqldump (--where por tabla, orden
#      determinista, sin LOCK/UNLOCK ni FOREIGN_KEY_CHECKS=0).
#   6. Probar el artefacto: inicializar_bd_test.sh --datos + verificar_bd_test.sh
#      sobre una BD recién recreada (carga con FK activas).
#   7. Cierre: inicializar BD a esquema vacío (regla de AGENTS.md) y bajar el
#      contenedor (o dejarlo si E2E_MANTENER_BD=SI).
#
# El archivo resultante (BD-test.sql en el directorio del repo) es Catálogo +
# base + admin: datos sintéticos 'E2ESEED', sin datos reales.
set -euo pipefail

cd "$(dirname "$0")"

for dependency in docker Xvfb xdotool ant java python3; do
    command -v "$dependency" >/dev/null || { echo "Falta dependencia: $dependency" >&2; exit 1; }
done

if (: >/dev/tcp/127.0.0.1/3307) 2>/dev/null; then
    echo "El puerto 3307 ya está ocupado; no se detendrá ningún servicio existente." >&2
    exit 1
fi

ROOT="$(pwd)"
PROJECT="restaurante-bd-test-gen-$$-$(date +%s)"
WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-bd-test-gen-XXXXXX")"
mkdir -p "$WORKDIR/bin" "$WORKDIR/facturas" "$WORKDIR/chunks"
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
      - bd_test_gen_mysql_data:/var/lib/mysql
      - type: bind
        source: "$ROOT/db/e2e/001-esquema.sql"
        target: /docker-entrypoint-initdb.d/001-esquema.sql
        read_only: true
      - type: bind
        source: "$ROOT/db/e2e/002-privilegios.sql"
        target: /docker-entrypoint-initdb.d/002-privilegios.sql
        read_only: true
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping --host=127.0.0.1 --user=root --password=\"\$\${MYSQL_ROOT_PASSWORD}\" --silent"]
      interval: 5s
      timeout: 5s
      retries: 24
      start_period: 30s
volumes:
  bd_test_gen_mysql_data:
EOF
COMPOSE=(docker compose -p "$PROJECT" -f "$COMPOSE_FILE")

cleanup() {
    local exit_code=$?
    if [[ "${E2E_MANTENER_BD:-NO}" == "SI" && "$exit_code" == 0 ]]; then
        echo "BD mantenida (proyecto=$PROJECT): cerrar luego con:" >&2
        echo "  docker compose -p $PROJECT -f $COMPOSE_FILE down --volumes" >&2
        echo "Workdir conservado (montaje del esquema para inicializar_bd_test.sh): $WORKDIR" >&2
        return "$exit_code"
    fi
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    rm -rf -- "$WORKDIR"
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
export E2E_WORKDIR="$WORKDIR"
export E2E_BD_DESECHABLE='SI'
export PDF_FACTURAS_DIR="$WORKDIR/facturas"
export E2E_LOGIN_USERNAME='admin'
export E2E_LOGIN_PASSWORD='E2EBootstrap2026X'
export PATH="$WORKDIR/bin:$PATH"
export CUPS_SERVER='127.0.0.1:65534'
export ACCION_IMPRESION='IMPRIMIR'

"${COMPOSE[@]}" up -d --wait
CONTENEDOR_ID="$("${COMPOSE[@]}" ps -q db)"
./e2e_virtual.sh Vista.BootstrapInicialE2EVirtual
./e2e_virtual.sh Vista.SeedBaseE2EVirtual
./e2e_virtual.sh Vista.SeedPlatosCombinatoriaE2EVirtual

# Indice de recolección ordenada: tablas + filtro opcion (mysqldump --where).
# Orden pensado para carga y lectura: config → admin → cliente → salas →
# catalogo → mapeos → control; `configuracion_sistema` va toda con defaults.
TABLE_NAME=("config" "configuracion_sistema" "usuarios" "clientes" "salas" "categorias" "platos" "plato_categoria" "restaurante_control")
TABLE_WHERE=("" "" "id=1" "documento='V-00000000'" "nombre='Sala Principal'" "nombre='E2ESEED-CAT'" "nombre LIKE 'E2ESEED-%'" "nombre_clave LIKE 'e2eseed-%'" "id=1")

# Normalización: la sala sembrada via UI puede quedar con id alto (el arranque
# de la app auto-siembra otras salas); los arneses de pruebas usan sala id=1.
# BD operativa vacía => DELETE + reinsert id=1 es seguro antes del dump.
docker exec "$CONTENEDOR_ID" sh -lc "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" restaurante_test -e \"DELETE FROM salas; INSERT INTO salas (id, nombre, mesas, tipo) VALUES (1, 'Sala Principal', 4, 'SALON');\"" 2>/dev/null || true

i=0
for idx in "${!TABLE_NAME[@]}"; do
    TABLA="${TABLE_NAME[$idx]}"
    FILTRO="${TABLE_WHERE[$idx]}"
    ARG_FILTER=""
    if [[ -n "$FILTRO" ]]; then ARG_FILTER="--where=\"$FILTRO\""; fi
    docker exec "$CONTENEDOR_ID" sh -lc "mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --no-create-info --skip-add-locks --skip-comments --skip-extended-insert --order-by-primary --skip-dump-date $ARG_FILTER restaurante_test $TABLA" > "$WORKDIR/chunks/$(printf '%02d' $i)-$TABLA.sql" 2>/dev/null
    i=$((i+1))
done

python3 - "$WORKDIR/chunks" > BD-test.sql <<'PY'
import sys, glob
from pathlib import Path

CHUNK_DIR = Path(sys.argv[1])
lineas = [
    "-- BD-test.sql — datos de pruebas para la BD restaurante_test (QA / E2E).",
    "-- Generado POR LA INTERFAZ por generar_bd_test.sh (BootstrapInicialE2EVirtual",
    "-- + SeedBaseE2EVirtual + SeedPlatosCombinatoriaE2EVirtual); nunca a mano.",
    "-- Regenerar con: ./generar_bd_test.sh",
    "--",
    "-- Contenido: config(id=1, tasa 40/IVA 16), configuracion_sistema (defaults",
    "-- + bootstrap_inicial_completado=1), usuarios(id=1 'admin', hash bcrypt de la",
    "-- contraseña de prueba E2EBootstrap2026X), clientes('V-00000000' Consumidor",
    "-- Final), salas('Sala Principal', 4 mesas), categorias('E2ESEED-CAT'),",
    "-- platos(108 combos 'E2ESEED-USD/VES-(EXE|IVA|INC)-NN' + base 'E2ESEED-POLLO',",
    "-- precios coherentes con la tasa 40), plato_categoria(109 mapeos) y el registro",
    "-- operativo hotelero restaurante_control(id=1). Tablas operativas vacías:",
    "-- pedidos, detalle_pedidos, pedido_pagos, pedido_saldos_movimientos,",
    "-- pedido_liquidaciones, comandas_marchas(_detalle), auditoria_* , cierres_caja,",
    "-- login_intentos, plato_favorito, mesoneros, preferencias_tabla, schema_migrations.",
    "-- La carga respeta la integridad referencial (sin FOREIGN_KEY_CHECKS=0).",
    "SET NAMES utf8mb4;",
    "USE `restaurante_test`;",
    "",
]
inserts = 0
saltadas = 0
for chunk in sorted(CHUNK_DIR.glob("*.sql")):
    for raw in chunk.read_text(encoding="utf-8").splitlines():
        linea = raw.rstrip()
        if not linea:
            saltadas += 1
            continue
        if linea.startswith("--") or linea.startswith("/*") or linea.startswith("LOCK ") \
           or linea.startswith("UNLOCK ") or "FOREIGN_KEY_CHECKS" in linea:
            saltadas += 1
            continue
        if linea.startswith("INSERT INTO"):
            inserts += 1
            lineas.append(linea)
lineas.append("")
sys.stdout.write("\n".join(lineas))
sys.stderr.write(f"INSERTs recolectadas: {inserts} (declaraciones saltadas: {saltadas})\n")
PY

if ! grep -q '^INSERT INTO ' BD-test.sql; then
    echo "FAIL BD-test.sql sin INSERTs; revisar recolección." >&2
    exit 1
fi
wc -l BD-test.sql

# Probar el artefacto en una BD recién recreada (carga con FK activas).
./inicializar_bd_test.sh --datos
./verificar_bd_test.sh

# Cierre: la BD queda inicializada (esquema vacío) según la regla de AGENTS.md.
./inicializar_bd_test.sh
echo "GENERACION_BD_TEST_OK: BD-test.sql generado y verificado."

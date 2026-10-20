#!/usr/bin/env bash
# Drill de respaldo/restauración en una BD desechable aislada.
# Crea un MySQL efímero en 127.0.0.1:3307, respalda, muta, restaura y
# verifica conciliación financiera. No toca la BD de servicio ni datos reales.
set -euo pipefail

cd "$(dirname "$0")"

command -v docker >/dev/null || { echo "Falta Docker" >&2; exit 1; }
command -v java >/dev/null || { echo "Falta Java 21" >&2; exit 1; }
command -v ant >/dev/null || { echo "Falta Apache Ant" >&2; exit 1; }

if (: >/dev/tcp/127.0.0.1/3307) 2>/dev/null; then
    echo "El puerto 3307 ya está ocupado; el drill no detendrá contenedores ajenos." >&2
    exit 1
fi

PROYECTO="restaurante-drill-$$-$(date +%s)"
COMPOSE=(docker compose -p "$PROYECTO" -f docker-compose.integration.yml)
DIRECTORIO="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-drill-XXXXXX")"

limpiar() {
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    rm -rf -- "$DIRECTORIO"
}
trap limpiar EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

export DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER='restaurante_test_app'
export E2E_MYSQL_PASSWORD="${E2E_MYSQL_PASSWORD:-test_only_app_password}"
export DB_PASSWORD="$E2E_MYSQL_PASSWORD"
export MYSQL_USER='restaurante_test_app'
export MYSQL_PASSWORD="$E2E_MYSQL_PASSWORD"
export DB_NAME='restaurante_test'
export MYSQL_DATABASE='restaurante_test'
export BACKUP_HMAC_KEY='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
export TZ='UTC'

CLASE_JAVA=(java -cp "build/classes:librerias/*" restaurante.Restaurante)
MYSQL=(env MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -P3306 -urestaurante_test_app restaurante_test -N -B)

mysql_exec() {
    "${COMPOSE[@]}" exec -T db "${MYSQL[@]}" -e "$1"
}

echo "== Compilando =="
ant -q compile

echo "== Levantando MySQL efímero =="
"${COMPOSE[@]}" up -d --wait

echo "== Sembrando un pago consistente para poder conciliar =="
mysql_exec "INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd, es_legado)
            SELECT id, 'EFECTIVO', 'USD', total, COALESCE(tasa_cambio, 1), total_bs, total, 1
            FROM pedidos WHERE id = 100"

echo "== 1. Respaldo inicial =="
"${CLASE_JAVA[@]}" --respaldar "$DIRECTORIO" | tee "$DIRECTORIO/respaldo1.log"
ORIGINAL="$(sed -n 's/^RESPALDO_OK //p' "$DIRECTORIO/respaldo1.log")"
test -f "$ORIGINAL" || { echo "No se generó el respaldo" >&2; exit 1; }

echo "== 2. Verificación aislada del respaldo (schema temporal) =="
"${CLASE_JAVA[@]}" --verificar-respaldo "$ORIGINAL"

echo "== 3. Mutación destructiva de la BD viva =="
mysql_exec "UPDATE config SET nombre='MUTADO_POR_DRILL' WHERE id=1"
mysql_exec "INSERT INTO configuracion_sistema (clave, valor) VALUES ('drill_mutacion','1')
            ON DUPLICATE KEY UPDATE valor='1'"

echo "== 4. Restauración desde el respaldo verificado =="
"${CLASE_JAVA[@]}" --restaurar-respaldo "$ORIGINAL"

echo "== 5. Comprobando reversión de la mutación =="
NOMBRE="$(mysql_exec "SELECT nombre FROM config WHERE id=1")"
if [ "$NOMBRE" = "MUTADO_POR_DRILL" ]; then
    echo "FALLO: la restauración no revirtió el nombre mutado" >&2
    exit 1
fi
RESIDUO="$(mysql_exec "SELECT COUNT(*) FROM configuracion_sistema WHERE clave='drill_mutacion'")"
if [ "$RESIDUO" != "0" ]; then
    echo "FALLO: la restauración dejó el residuo drill_mutacion ($RESIDUO)" >&2
    exit 1
fi
echo "OK: nombre restaurado a '$NOMBRE' y sin residuo de la mutación"

echo "== 6. Un respaldo financieramente inconsistente debe rechazarse =="
mysql_exec "UPDATE pedido_pagos SET monto_usd = monto_usd + 100 WHERE id_pedido = 100 LIMIT 1"
"${CLASE_JAVA[@]}" --respaldar "$DIRECTORIO" | tee "$DIRECTORIO/respaldo2.log"
INCONSISTENTE="$(sed -n 's/^RESPALDO_OK //p' "$DIRECTORIO/respaldo2.log")"
if "${CLASE_JAVA[@]}" --verificar-respaldo "$INCONSISTENTE" > "$DIRECTORIO/verificacion2.log" 2>&1; then
    echo "FALLO: la verificación aceptó un respaldo descuadrado" >&2
    cat "$DIRECTORIO/verificacion2.log" >&2
    exit 1
fi
if ! grep -qi "no cuadra\|concili" "$DIRECTORIO/verificacion2.log"; then
    echo "FALLO: el rechazo no fue por conciliación financiera" >&2
    cat "$DIRECTORIO/verificacion2.log" >&2
    exit 1
fi
echo "OK: el respaldo descuadrado fue rechazado por conciliación financiera"

echo "DRILL_RESTAURACION_OK"

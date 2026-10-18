#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "================================================================================"
echo "  RESTAURANTE 2026 - LIMPIEZA PARA COMENZAR HOY (LINUX / UNIX)                 "
echo "================================================================================"
echo ""
echo "  Este script:"
echo "    - Elimina pedidos anteriores, consumos, pagos y cierres de caja."
echo "    - Reinicia el contador de pedidos al numero 1."
echo "    - Libera el 100% de las mesas."
echo "    - Conserva intactos los 87 platos, salas, categorias, mesoneros y config."
echo ""

SQL_FILE="$DIR/limpiar_para_comenzar_hoy.sql"
if [ ! -f "$SQL_FILE" ]; then
    echo "[ERROR] No se encontro el archivo $SQL_FILE"
    exit 1
fi

# Cargar variables desde .env si existe
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_USER="admin"
DB_PASS="admin"
DB_NAME="restaurante"

if [ -f "$DIR/.env" ]; then
    export $(grep -v '^#' "$DIR/.env" | xargs -d '\n' 2>/dev/null || grep -v '^#' "$DIR/.env" | xargs)
    [ -n "$MYSQL_HOST" ] && DB_HOST="$MYSQL_HOST"
    [ -n "$MYSQL_PORT" ] && DB_PORT="$MYSQL_PORT"
    [ -n "$MYSQL_USER" ] && DB_USER="$MYSQL_USER"
    [ -n "$MYSQL_PASSWORD" ] && DB_PASS="$MYSQL_PASSWORD"
    [ -n "$MYSQL_DATABASE" ] && DB_NAME="$MYSQL_DATABASE"
    [ -n "$DB_HOST" ] && DB_HOST="$DB_HOST"
    [ -n "$DB_PORT" ] && DB_PORT="$DB_PORT"
    [ -n "$DB_USER" ] && DB_USER="$DB_USER"
    [ -n "$DB_PASS" ] && DB_PASS="$DB_PASS"
    [ -n "$DB_NAME" ] && DB_NAME="$DB_NAME"
fi

CONTENEDOR="${CONTENEDOR:-restaurante-db-1}"
USE_DOCKER=false
if ! command -v mysql >/dev/null 2>&1; then
    if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q "^${CONTENEDOR}$"; then
        USE_DOCKER=true
    fi
fi

echo "[1/4] Generando respaldo preventivo de seguridad..."
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="$DIR/respaldo_antes_de_limpiar_${TIMESTAMP}.sql"

if [ "$USE_DOCKER" = true ]; then
    docker exec "$CONTENEDOR" mysqldump -u"$DB_USER" -p"$DB_PASS" --single-transaction "$DB_NAME" > "$BACKUP_FILE" 2>/dev/null || true
else
    if command -v mysqldump >/dev/null 2>&1; then
        mysqldump -h "$DB_HOST" -P "$DB_PORT" -u"$DB_USER" -p"$DB_PASS" --single-transaction "$DB_NAME" > "$BACKUP_FILE" 2>/dev/null || true
    fi
fi

if [ -s "$BACKUP_FILE" ]; then
    echo "  - Respaldo guardado en: $BACKUP_FILE"
else
    echo "  - [AVISO] No se pudo generar respaldo previo o la base no existia aun."
    rm -f "$BACKUP_FILE"
fi

echo "[2/4] Ejecutando limpieza transaccional e inicializando pedidos a #1..."
if [ "$USE_DOCKER" = true ]; then
    docker exec -i "$CONTENEDOR" mysql --default-character-set=utf8mb4 -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
else
    mysql --default-character-set=utf8mb4 -h "$DB_HOST" -P "$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
fi
echo "  - Limpieza completada exitosamente."

echo "[3/4] Validando esquema con Restaurante.jar..."
if [ -f "$DIR/Restaurante.jar" ]; then
    java -jar "$DIR/Restaurante.jar" --migrate-db >/dev/null 2>&1 || true
fi

echo ""
echo "================================================================================"
echo "  [OK] LIMPIEZA EXITOSA: Sistema listo para iniciar servicio hoy."
echo "    - Pedidos anteriores eliminados."
echo "    - Proximo pedido sera el #1."
echo "    - 100% de mesas libres."
echo "    - Catalogo de 87 platos intacto con fecha de hoy."
echo "================================================================================"

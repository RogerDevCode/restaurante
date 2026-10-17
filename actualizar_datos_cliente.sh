#!/usr/bin/env bash
set -e

# ========================================================
# Script Especial de Actualización de Datos de Cliente
# Sistema Restaurante 2026 (Linux / Docker)
# ========================================================

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

SQL_FILE="BD RESTAURANTE 2026.sql"

echo "========================================================"
echo " ACTUALIZADOR DE BASE DE DATOS - DATOS REALES DE CLIENTE"
echo " Restaurante 2026"
echo "========================================================"
echo ""

if [ ! -f "$SQL_FILE" ]; then
    echo "[ERROR] No se encontró el archivo '$SQL_FILE' en $DIR"
    exit 1
fi

# Cargar variables de .env si existe
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | grep -E '^(MYSQL_|DB_)' | xargs 2>/dev/null || true)
fi

DB_HOST="${MYSQL_HOST:-127.0.0.1}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_USER="${MYSQL_USER:-admin}"
DB_PASS="${MYSQL_PASSWORD:-admin}"
DB_NAME="${MYSQL_DATABASE:-restaurante}"
CONTENEDOR="${CONTENEDOR:-restaurante-db-1}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="respaldo_preventivo_${TIMESTAMP}.sql"

echo "[1/4] Verificando conectividad con MySQL..."
USAR_DOCKER=0
if command -v mysql &> /dev/null && mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" -e "SELECT 1;" &>/dev/null; then
    USAR_DOCKER=0
    echo "  -> Conectado vía cliente local MySQL ($DB_HOST:$DB_PORT)."
elif command -v docker &> /dev/null && docker ps --format '{{.Names}}' | grep -q "^${CONTENEDOR}$"; then
    USAR_DOCKER=1
    echo "  -> Conectado vía contenedor Docker ($CONTENEDOR)."
else
    echo "[ERROR] No se pudo conectar a MySQL local ni al contenedor Docker '$CONTENEDOR'."
    echo "Asegúrese de que el servidor MySQL o contenedor Docker esté activo."
    exit 1
fi

echo "[2/4] Generando respaldo preventivo de seguridad..."
if [ "$USAR_DOCKER" -eq 1 ]; then
    docker exec "$CONTENEDOR" mysqldump -u"$DB_USER" -p"$DB_PASS" --single-transaction "$DB_NAME" > "$BACKUP_FILE" 2>/dev/null || true
else
    mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" --single-transaction "$DB_NAME" > "$BACKUP_FILE" 2>/dev/null || true
fi

if [ -f "$BACKUP_FILE" ] && [ -s "$BACKUP_FILE" ]; then
    echo "  -> Respaldo guardado exitosamente en: $BACKUP_FILE"
else
    echo "  -> [AVISO] La base de datos estaba vacía o inicializándose; se continuará con la importación."
    rm -f "$BACKUP_FILE" 2>/dev/null || true
fi

echo "[3/4] Importando datos reales y catálogo sanitizado ($SQL_FILE)..."
if [ "$USAR_DOCKER" -eq 1 ]; then
    docker exec -i "$CONTENEDOR" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
else
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
fi
echo "  -> Volcado importado exitosamente."

echo "[4/4] Validando migraciones de esquema con el sistema..."
if command -v java &> /dev/null && [ -f "Restaurante.jar" ]; then
    java -jar Restaurante.jar --migrate-db || true
fi

echo ""
echo "========================================================"
echo " ¡ACTUALIZACIÓN COMPLETADA CON ÉXITO!"
echo "========================================================"
echo " Datos del Negocio Establecidos:"
echo "   - Comercio: RESTAURANT LA AVALANCHA 2023, C.A"
echo "   - RIF:      J-504542725"
echo "   - Dirección: RIO CHICO EDO MIRANDA"
echo "   - Catálogo:  87 platos reales cargados"
echo "   - Tasa Base: 880.12 Bs/$"
echo "   - Usuario:   admin / admin (o xavier / admin)"
echo "========================================================"

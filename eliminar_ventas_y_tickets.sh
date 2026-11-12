#!/usr/bin/env bash
# ==============================================================================
# RESTAURANTE 2026 - ELIMINAR VENTAS, PEDIDOS Y TICKETS (LINUX / UNIX)
#
# Uso:
#   ./eliminar_ventas_y_tickets.sh            (Modo interactivo: BD activa o archivo SQL)
#   ./eliminar_ventas_y_tickets.sh <dump.sql> (Modo directo: limpia el archivo SQL)
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "================================================================================"
echo "  RESTAURANTE 2026 - ELIMINAR VENTAS, PEDIDOS Y TICKETS                        "
echo "================================================================================"

limpiar_archivo_sql() {
    local ARCHIVO_IN="$1"
    if [ ! -f "$ARCHIVO_IN" ]; then
        echo "[ERROR] No se encontro el archivo: $ARCHIVO_IN"
        return 1
    fi

    echo ""
    echo "Procesando archivo SQL: $ARCHIVO_IN..."
    if command -v python3 >/dev/null 2>&1; then
        python3 "$DIR/limpiar_sql_ventas.py" "$ARCHIVO_IN"
    else
        echo "[ERROR] Se requiere python3 para procesar archivos de volcado SQL."
        return 1
    fi
}

vaciar_base_de_datos() {
    local SQL_FILE="$DIR/eliminar_ventas_y_tickets.sql"
    if [ ! -f "$SQL_FILE" ]; then
        echo "[ERROR] No se encontro el archivo $SQL_FILE"
        return 1
    fi

    echo ""
    echo "================================================================================"
    echo "  [ATENCION] VACIADO DE VENTAS Y TICKETS EN LA BASE DE DATOS ACTIVA             "
    echo "================================================================================"
    echo "  Este proceso:"
    echo "    - ELIMINA todos los pedidos, consumos, comandas, pagos y cierres de caja."
    echo "    - REINICIA el contador de pedidos al numero 1."
    echo "    - LIBERA el 100% de las mesas de todas las salas."
    echo "    - CONSERVA intactos los platos, precios, categorias, salas, mesoneros,"
    echo "      clientes, usuarios y configuracion fiscal."
    echo "================================================================================"
    echo ""
    read -r -p "Escriba 'SI' en mayusculas para confirmar la operacion: " CONFIRMACION
    if [ "$CONFIRMACION" != "SI" ]; then
        echo "Operacion cancelada por el usuario."
        return 0
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

    echo ""
    echo "[1/3] Generando respaldo preventivo de seguridad..."
    TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
    BACKUP_FILE="$DIR/respaldo_antes_de_eliminar_ventas_${TIMESTAMP}.sql"

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
        echo "  - [AVISO] No se pudo generar respaldo previo o la base aun no existia."
        rm -f "$BACKUP_FILE"
    fi

    echo "[2/3] Ejecutando eliminacion de ventas y reseteo de contadores a 1..."
    if [ "$USE_DOCKER" = true ]; then
        docker exec -i "$CONTENEDOR" mysql --default-character-set=utf8mb4 -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
    else
        mysql --default-character-set=utf8mb4 -h "$DB_HOST" -P "$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
    fi
    echo "  - Limpieza de tablas transaccionales completada con exito."

    echo "[3/3] Validando esquema con Restaurante.jar..."
    if [ -f "$DIR/Restaurante.jar" ]; then
        java -jar "$DIR/Restaurante.jar" --migrate-db >/dev/null 2>&1 || true
    fi

    echo ""
    echo "================================================================================"
    echo "  [OK] OPERACION COMPLETADA CON EXITO                                          "
    echo "    - Todas las ventas, pedidos, pagos y cierres han sido eliminados.          "
    echo "    - El proximo pedido / ticket sera rigurosamente el #1.                     "
    echo "    - Mesas 100% libres en todas las salas.                                    "
    echo "    - Platos, precios, categorias, usuarios y configuracion intactos.          "
    echo "================================================================================"
}

# Si se pasa un archivo como parametro directo
if [ -n "$1" ]; then
    limpiar_archivo_sql "$1"
    exit $?
fi

# Modo interactivo
echo ""
echo "Seleccione una opcion:"
echo "  [1] Vaciar ventas y tickets de la BASE DE DATOS activa (MySQL)"
echo "  [2] Limpiar un archivo de volcado SQL (ej: a1.sql -> a1-sin-ventas.sql)"
echo "  [0] Salir"
echo ""
read -r -p "Opcion [1/2/0]: " OPCION

case "$OPCION" in
    1)
        vaciar_base_de_datos
        ;;
    2)
        DEFAULT_FILE="a1.sql"
        if [ -f "$DIR/$DEFAULT_FILE" ]; then
            read -r -p "Ruta del archivo SQL [$DEFAULT_FILE]: " ARCHIVO_INPUT
            ARCHIVO_INPUT="${ARCHIVO_INPUT:-$DEFAULT_FILE}"
        else
            read -r -p "Ruta del archivo SQL a limpiar: " ARCHIVO_INPUT
        fi
        limpiar_archivo_sql "$ARCHIVO_INPUT"
        ;;
    0|*)
        echo "Operacion finalizada."
        exit 0
        ;;
esac

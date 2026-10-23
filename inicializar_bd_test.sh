#!/usr/bin/env bash
#
# inicializar_bd_test.sh — reinicializa la BD de pruebas E2E/QA en 3307.
#
# Regla de AGENTS.md: al cerrar un test la BD debe quedar inicializada.
#   ./inicializar_bd_test.sh          → esquema oficial VACÍO (db/e2e/001-esquema.sql)
#   ./inicializar_bd_test.sh --datos  → esquema + BD-test.sql (catálogo + base + admin)
#   ./cargar_bd_test.sh               → inicializar --datos y verificar
#
# Opera SOLO sobre el contenedor que escucha en 127.0.0.1:3307 con la BD
# restaurante_test; jamás sobre la BD de producción. La carga de BD-test.sql
# respeta la integridad referencial (sin FOREIGN_KEY_CHECKS=0).
set -euo pipefail

cd "$(dirname "$0")"

MODO="VACIA"
if [[ "${1:-}" == "--datos" ]]; then MODO="DATOS"; fi

CONTENEDOR="$(docker ps --format '{{.Names}}:{{.Ports}}' | awk 'index($0, "127.0.0.1:3307->") {sub(/:.*/, ""); print; exit}')"
if [[ -z "$CONTENEDOR" ]]; then
    echo "No hay contenedor E2E activo en 127.0.0.1:3307; nada que inicializar." >&2
    exit 1
fi
echo "Inicializando BD de pruebas ($MODO) en contenedor: $CONTENEDOR"

inicializar_sql() {
    docker exec "$CONTENEDOR" sh -lc '
        set -e
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS restaurante_test; CREATE DATABASE restaurante_test COLLATE utf8_spanish_ci;"
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" restaurante_test < /docker-entrypoint-initdb.d/001-esquema.sql
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" restaurante_test < /docker-entrypoint-initdb.d/002-privilegios.sql
    '
}

if [[ "$MODO" == "VACIA" ]]; then
    inicializar_sql
    TABLAS="$(docker exec "$CONTENEDOR" sh -lc '
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = CHAR(114,101,115,116,97,117,114,97,110,116,101,95,116,101,115,116);"
    ')"
    if [[ "$TABLAS" != "24" ]]; then
        echo "Esquema incompleto tras inicializar: $TABLAS tablas (esperadas 24)." >&2
        exit 1
    fi
    echo "BD_INICIALIZADA_OK: esquema oficial vacío (24 tablas, 0 filas de datos)."
    exit 0
fi

if [[ ! -f BD-test.sql ]]; then
    echo "No existe BD-test.sql; generarlo con ./generar_bd_test.sh." >&2
    exit 1
fi
inicializar_sql
docker exec -i "$CONTENEDOR" sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" restaurante_test' < BD-test.sql

PLATOS="$(docker exec "$CONTENEDOR" sh -lc '
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM platos;" restaurante_test
')"
MAPEOS="$(docker exec "$CONTENEDOR" sh -lc '
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM plato_categoria;" restaurante_test
')"
if [[ "$PLATOS$MAPEOS" != "109109" ]]; then
    echo "Carga incompleta de BD-test.sql (platos=$PLATOS, mapeos=$MAPEOS; esperados 109/109)." >&2
    exit 1
fi
echo "BD_INICIALIZADA_OK: esquema + BD-test.sql cargado (platos=$PLATOS, mapeos=$MAPEOS)."
echo "Sugerencia inmediata: ./verificar_bd_test.sh"

#!/usr/bin/env bash
# Drill de restauración en BD secundaria.
# Tres servidores MySQL efímeros locales: origen (3307), secundaria (3308, TZ America/Caracas)
# y verificador (3309). Respaldo en el origen, restauración con el CLI completo en la
# secundaria, verificación pura en el verificador y verificación del respaldo real de
# producción (solo lectura). La BD de servicio (3306) y la app en 49200 no se tocan.
# Al final imprime RESTAURACION_SECUNDARIA_OK si todo pasa.
# Vars: SECUNDARIA_KEEP=1 conserva el directorio de trabajo para depurar.
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

command -v docker >/dev/null || { echo "Falta Docker" >&2; exit 1; }
command -v java >/dev/null || { echo "Falta Java 21" >&2; exit 1; }
command -v ant >/dev/null || { echo "Falta Apache Ant" >&2; exit 1; }

for puerto in 3307 3308 3309; do
    if (: >/dev/tcp/127.0.0.1/$puerto) 2>/dev/null; then
        echo "El puerto $puerto ya está ocupado; el drill no detendrá servicios ajenos." >&2
        exit 1
    fi
done

if [ ! -s .env ] || ! grep -q '^BACKUP_HMAC_KEY=' .env; then
    echo "FALLO:BACKUP_HMAC_KEY no está en .env; no se puede verificar el respaldo real de producción" >&2
    exit 1
fi
if [ -z "$(ls Restaurante_Ejecutable/respaldos/*.sql 2>/dev/null)" ]; then
    echo "FALLO: no hay respaldos reales en Restaurante_Ejecutable/respaldos/" >&2
    exit 1
fi

PROYECTO="restaurante-secundaria-drill-$$-$(date +%s)"
WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-secundaria-XXXXXX")"
EVIDENCIA="$ROOT/evidencias/produccion/$(date +%Y-%m-%d)"
mkdir -p "$EVIDENCIA"

COMPOSE=(docker compose -p "$PROYECTO" -f "$WORKDIR/compose.yml")

cat > "$WORKDIR/002-perms-origen.sql" <<'SQL'
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON restaurante_origen.* TO 'app_origen'@'%';
SQL
cat > "$WORKDIR/002-perms-restaurar.sql" <<'SQL'
GRANT ALL PRIVILEGES ON *.* TO 'admin_restaurar'@'%';
SQL
cat > "$WORKDIR/002-perms-verificar.sql" <<'SQL'
GRANT ALL PRIVILEGES ON *.* TO 'admin_verificar'@'%';
SQL

cat > "$WORKDIR/compose.yml" <<EOF
services:
  db_origen:
    image: mysql:8.4.11
    environment:
      MYSQL_DATABASE: restaurante_origen
      MYSQL_USER: app_origen
      MYSQL_PASSWORD: test_only_app_password
      MYSQL_ROOT_PASSWORD: test_root_pass
    command: --log-bin-trust-function-creators=1
    ports:
      - "127.0.0.1:3307:3306"
    volumes:
      - secundaria_drill_origen:/var/lib/mysql
      - $ROOT/BD.sql:/docker-entrypoint-initdb.d/001-restaurante.sql:ro
      - $WORKDIR/002-perms-origen.sql:/docker-entrypoint-initdb.d/002-perms.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping --host=127.0.0.1 --user=root --password=\$\${MYSQL_ROOT_PASSWORD} --silent"]
      interval: 5s
      timeout: 5s
      retries: 24
      start_period: 30s
  db_secundaria:
    image: mysql:8.4.11
    environment:
      MYSQL_DATABASE: restaurante_secundaria
      MYSQL_USER: admin_restaurar
      MYSQL_PASSWORD: admin_restore_pass
      MYSQL_ROOT_PASSWORD: test_root_pass
      TZ: America/Caracas
    command: --log-bin-trust-function-creators=1 --max_connections=50
    ports:
      - "127.0.0.1:3308:3306"
    volumes:
      - secundaria_drill_secundaria:/var/lib/mysql
      - $ROOT/BD.sql:/docker-entrypoint-initdb.d/001-restaurante.sql:ro
      - $WORKDIR/002-perms-restaurar.sql:/docker-entrypoint-initdb.d/002-perms.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping --host=127.0.0.1 --user=root --password=\$\${MYSQL_ROOT_PASSWORD} --silent"]
      interval: 5s
      timeout: 5s
      retries: 24
      start_period: 30s
  db_verificador:
    image: mysql:8.4.11
    environment:
      MYSQL_DATABASE: restaurante_verif
      MYSQL_USER: admin_verificar
      MYSQL_PASSWORD: admin_verify_pass
      MYSQL_ROOT_PASSWORD: test_root_pass
    command: --log-bin-trust-function-creators=1
    ports:
      - "127.0.0.1:3309:3306"
    volumes:
      - secundaria_drill_verificador:/var/lib/mysql
      - $ROOT/BD.sql:/docker-entrypoint-initdb.d/001-restaurante.sql:ro
      - $WORKDIR/002-perms-verificar.sql:/docker-entrypoint-initdb.d/002-perms.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping --host=127.0.0.1 --user=root --password=\$\${MYSQL_ROOT_PASSWORD} --silent"]
      interval: 5s
      timeout: 5s
      retries: 24
      start_period: 30s
volumes:
  secundaria_drill_origen:
  secundaria_drill_secundaria:
  secundaria_drill_verificador:
EOF

HMAC_TEST='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
export TZ='UTC'

CLASE_JAVA=(java -cp "$ROOT/build/classes:$ROOT/librerias/*" restaurante.Restaurante)
# run_cli DIR --comando... : ejecuta el CLI desde DIR (sin .env en cwd/parent) para que la
# clave BACKUP_HMAC_KEY provenga EXCLUISIVAMENTE del entorno inyectado y sea determinista.
run_cli() { local dir="$1"; shift; (cd "$dir" && exec "$@"); }

SQL_ORIGEN='jdbc:mysql://127.0.0.1:3307/restaurante_origen?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
SQL_SECUNDARIA='jdbc:mysql://127.0.0.1:3308/restaurante_secundaria?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
SQL_VERIFICADOR='jdbc:mysql://127.0.0.1:3309/restaurante_verif?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'

MYSQL_O=(docker exec -i -e MYSQL_PWD=test_root_pass "$PROYECTO-db_origen-1" mysql --protocol=TCP -h127.0.0.1 -uroot restaurante_origen -N -B)
MYSQL_S=(docker exec -i -e MYSQL_PWD=test_root_pass "$PROYECTO-db_secundaria-1" mysql --protocol=TCP -h127.0.0.1 -uroot restaurante_secundaria -N -B)
MYSQL_V=(docker exec -i -e MYSQL_PWD=test_root_pass "$PROYECTO-db_verificador-1" mysql --protocol=TCP -h127.0.0.1 -uroot restaurante_verif -N -B)

ok() { echo "OK: $1"; }
fail() { echo "FALLO: $1" >&2; exit 1; }

cleanup() {
    local codigo=$?
    if (( codigo != 0 )); then
        "${COMPOSE[@]}" logs --no-color db_origen db_secundaria db_verificador >&2 || true
        echo "DIR de trabajo conservado: $WORKDIR" >&2
    else
        rm -rf -- "$WORKDIR"
    fi
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    echo "Servidores MySQL efímeros eliminados. Evidencia en: $EVIDENCIA"
    return "$codigo"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "== Compilando =="
ant -q compile

echo "== Levantando 3 servidores MySQL efímeros (origen 3307 / secundaria 3308 con TZ America/Caracas / verificador 3309) =="
"${COMPOSE[@]}" up -d --wait

echo "== Evidencia de heterogeneidad de servidores =="
HOST_O="$("${MYSQL_O[@]}" -e "SELECT @@hostname")"
HOST_S="$("${MYSQL_S[@]}" -e "SELECT @@hostname")"
echo "  origen      host=$HOST_O tz=$("${MYSQL_O[@]}" -e 'SELECT @@system_time_zone, @@time_zone') @@port=$("${MYSQL_O[@]}" -e 'SELECT @@port')"
echo "  secundaria  host=$HOST_S tz=$("${MYSQL_S[@]}" -e 'SELECT @@system_time_zone, @@time_zone') @@port=$("${MYSQL_S[@]}" -e 'SELECT @@port')"
[ "$HOST_O" != "$HOST_S" ] || fail "origen y secundaria comparten hostname; no son servidores heterogéneos"
ok "servidores MySQL distintos confirmados (diferentes hostnames y puertos)"

echo "== 1. Arranque de esquemue en origen (--migrate-db) y siembra financiera REAL (solo lectura de producción) =="
run_cli "$WORKDIR" env DB_URL="$SQL_ORIGEN" DB_USER='app_origen' DB_PASSWORD='test_only_app_password' DB_NAME='restaurante_origen' \
    MYSQL_USER='app_origen' MYSQL_PASSWORD='test_only_app_password' MYSQL_DATABASE='restaurante_origen' \
    BACKUP_HMAC_KEY="$HMAC_TEST" "${CLASE_JAVA[@]}" --migrate-db > "$WORKDIR/migrar_origen.log" 2>&1 \
    || { cat "$WORKDIR/migrar_origen.log" >&2; fail "--migrate-db falló en el origen"; }
grep -q "Migración de base de datos validada correctamente" "$WORKDIR/migrar_origen.log" \
    || { cat "$WORKDIR/migrar_origen.log" >&2; fail "sin confirmación de migración de esquema en origen"; }

SEED="$WORKDIR/semillas.sql"
docker_has_pedido="$(docker exec restaurante-db-1 mysql -uadmin -padmin -N -B -e 'SELECT COUNT(*) FROM restaurante.pedidos' 2>/dev/null || echo 0)"
{
    echo "SET FOREIGN_KEY_CHECKS=0;"
    if [ "$docker_has_pedido" -ge 1 ]; then
        docker exec restaurante-db-1 sh -c 'mysqldump -uadmin -padmin --no-create-info --no-tablespaces --skip-triggers --complete-insert --where="id=13" restaurante pedidos 2>/dev/null'
        IDS_LIQUID="$(docker exec restaurante-db-1 mysql -uadmin -padmin -N -B -e 'SELECT GROUP_CONCAT(id) FROM restaurante.pedido_liquidaciones WHERE id IN (SELECT id_liquidacion FROM restaurante.pedido_pagos WHERE id_pedido=13)' 2>/dev/null)"
        [ -n "$IDS_LIQUID" ] && docker exec restaurante-db-1 sh -c "mysqldump -uadmin -padmin --no-create-info --no-tablespaces --skip-triggers --complete-insert --where='id IN ($IDS_LIQUID)' restaurante pedido_liquidaciones 2>/dev/null"
        docker exec restaurante-db-1 sh -c 'mysqldump -uadmin -padmin --no-create-info --no-tablespaces --skip-triggers --complete-insert --where="id_pedido=13" restaurante detalle_pedidos 2>/dev/null'
        docker exec restaurante-db-1 sh -c 'mysqldump -uadmin -padmin --no-create-info --no-tablespaces --skip-triggers --complete-insert --where="id_pedido=13" restaurante pedido_pagos 2>/dev/null'
    fi
} > "$SEED"
"${MYSQL_O[@]}" < "$SEED"
if grep -q 'INSERT INTO `pedidos`' "$SEED"; then
    SEMILLA="pedido13 (fila viva de producción)"
    FILAS_PEDIDO="$("${MYSQL_O[@]}" -e 'SELECT COUNT(*) FROM pedidos')"
    [ "$FILAS_PEDIDO" -ge 1 ] || fail "la siembra real declarada pedido13 pero pedidos quedó vacío"
    OK_FK="$("${MYSQL_O[@]}" -e 'SELECT (SELECT COUNT(*) FROM detalle_pedidos WHERE id_plato IS NOT NULL AND id_plato NOT IN (SELECT id FROM platos)) + (SELECT COUNT(*) FROM pedido_pagos WHERE id_liquidacion IS NOT NULL AND id_liquidacion NOT IN (SELECT id FROM pedido_liquidaciones))')"
    [ "$OK_FK" = "0" ] || fail "la siembra dejó referencias huérfanas ($OK_FK)"
    ok "siembra real: datos financieros de producción cargados en el origen con referencias consistentes (solo lectura)"
else
    SEMILLA="vacía (la BD de producción no tenía pedidos activos; conciliación con sumas vacías)"
    ok "BD de producción sin pedidos vivos; el respaldo y la conciliación seguirán con sumas vacías"
fi
echo "  SEMILLA_FINANCIERA=$SEMILLA"

echo "== 2. Respaldo en el servidor DE ORIGEN =="
run_cli "$WORKDIR" env DB_URL="$SQL_ORIGEN" DB_USER='app_origen' DB_PASSWORD='test_only_app_password' DB_NAME='restaurante_origen' \
    MYSQL_USER='app_origen' MYSQL_PASSWORD='test_only_app_password' MYSQL_DATABASE='restaurante_origen' \
    BACKUP_HMAC_KEY="$HMAC_TEST" "${CLASE_JAVA[@]}" --respaldar "$WORKDIR/respaldos" \
    > "$WORKDIR/respaldo1.log" 2>&1 || { cat "$WORKDIR/respaldo1.log" >&2; fail "el respaldo en el origen falló"; }
grep -E "RESPALDO_OK|RESPALDO_FALLIDO" "$WORKDIR/respaldo1.log" || true
ORIGINAL="$(sed -n 's/^RESPALDO_OK //p' "$WORKDIR/respaldo1.log" | tr -d '\r')"
[ -f "$ORIGINAL" ] || { cat "$WORKDIR/respaldo1.log" >&2; fail "no se generó el respaldo en el origen"; }
ok "respaldo del origen: $(basename "$ORIGINAL") ($(stat -c%s "$ORIGINAL") bytes)"

echo "== 3. Verificación aislada del respaldo en la BD SECUNDARIA (schema temporal del servidor 3308) =="
run_cli "$WORKDIR" env DB_URL="$SQL_SECUNDARIA" DB_USER='admin_restaurar' DB_PASSWORD='admin_restore_pass' DB_NAME='restaurante_secundaria' \
    MYSQL_USER='admin_restaurar' MYSQL_PASSWORD='admin_restore_pass' MYSQL_DATABASE='restaurante_secundaria' \
    BACKUP_HMAC_KEY="$HMAC_TEST" "${CLASE_JAVA[@]}" --verificar-respaldo "$ORIGINAL" \
    > "$WORKDIR/verificar_secundaria.log" 2>&1 || { cat "$WORKDIR/verificar_secundaria.log" >&2; fail "la verificación del respaldo falló en la BD secundaria"; }
grep -E "VERIFICACION_OK" "$WORKDIR/verificar_secundaria.log" || true
ok "el respaldo del origen es restaurable en un servidor MySQL distinto (verificado en schema temporal de 3308)"

echo "== 4. Restauración COMPLETA del CLI en la BD secundaria (3308) =="
run_cli "$WORKDIR" env DB_URL="$SQL_SECUNDARIA" DB_USER='admin_restaurar' DB_PASSWORD='admin_restore_pass' DB_NAME='restaurante_secundaria' \
    MYSQL_USER='admin_restaurar' MYSQL_PASSWORD='admin_restore_pass' MYSQL_DATABASE='restaurante_secundaria' \
    BACKUP_HMAC_KEY="$HMAC_TEST" "${CLASE_JAVA[@]}" --restaurar-respaldo "$ORIGINAL" \
    > "$WORKDIR/restauracion_secundaria.log" 2>&1 || { cat "$WORKDIR/restauracion_secundaria.log" >&2; fail "la restauración falló en la BD secundaria"; }
PREVIO="$(sed -n 's/^RESTAURACION_OK respaldo_previo=//p' "$WORKDIR/restauracion_secundaria.log" | tr -d '\r')"
[ -n "$PREVIO" ] || fail "sin marcador RESTAURACION_OK/respaldo_previo en secundaria"
[ -f "$PREVIO" ] || fail "el respaldo previo de emergencia no existe en secundaria"
ok "restauración completada; respaldo previo de emergencia: $(basename "$PREVIO")"
PEDIDOS_ANTES="$("${MYSQL_S[@]}" -e 'SELECT COUNT(*) FROM pedidos')"

echo "== 5. Conciliación financiera identica entre origen y secundaria =="
linea_sumas() {
    local sql
    {
        sql="SELECT CONCAT('pedidos.total=',COALESCE(CAST(SUM(total) AS CHAR),'VACIO'),'|pedidos.total_bs=',COALESCE(CAST(SUM(total_bs) AS CHAR),'VACIO')) FROM pedidos"
        "${MYSQL_O[@]}" -e "$sql"
        "${MYSQL_S[@]}" -e "$sql"
        sql="SELECT CONCAT('detalle=',COALESCE(CAST(SUM(precio*cantidad) AS CHAR),'VACIO')) FROM detalle_pedidos"
        "${MYSQL_O[@]}" -e "$sql"
        "${MYSQL_S[@]}" -e "$sql"
        sql="SELECT CONCAT('pagos.monto=',COALESCE(CAST(SUM(monto) AS CHAR),'VACIO'),'|pagos.usd=',COALESCE(CAST(SUM(monto_usd) AS CHAR),'VACIO'),'|pagos.bs=',COALESCE(CAST(SUM(monto_bs) AS CHAR),'VACIO')) FROM pedido_pagos"
        "${MYSQL_O[@]}" -e "$sql"
        "${MYSQL_S[@]}" -e "$sql"
        sql="SELECT CONCAT('saldos.origen=',COALESCE(CAST(SUM(monto_origen) AS CHAR),'VACIO'),'|saldos.ves=',COALESCE(CAST(SUM(equivalente_ves) AS CHAR),'VACIO')) FROM pedido_saldos_movimientos"
        "${MYSQL_O[@]}" -e "$sql"
        "${MYSQL_S[@]}" -e "$sql"
        sql="SELECT CONCAT('liquid.usd=',COALESCE(CAST(SUM(aplicado_usd) AS CHAR),'VACIO'),'|liquid.ajuste_bs=',COALESCE(CAST(SUM(ajuste_cambio_bs) AS CHAR),'VACIO'),'|liquid.dif=',COALESCE(CAST(SUM(diferencia_usd) AS CHAR),'VACIO')) FROM pedido_liquidaciones"
        "${MYSQL_O[@]}" -e "$sql"
        "${MYSQL_S[@]}" -e "$sql"
    } 2>&1
}
SUMAS="$(linea_sumas)"
PARES_ORIGEN="$(printf '%s\n' "$SUMAS" | awk 'NR%2==1')"
PARES_SECUNDARIA="$(printf '%s\n' "$SUMAS" | awk 'NR%2==0')"
echo "  origen      $(printf '%s\n' "$PARES_ORIGEN" | tr '\n' ' ')"
echo "  secundaria  $(printf '%s\n' "$PARES_SECUNDARIA" | tr '\n' ' ')"
[ "$PARES_ORIGEN" = "$PARES_SECUNDARIA" ] || fail "las sumas financieras difieren entre origen y secundaria"
ok "conciliación idéntica entre servidor de origen y servidor secundaria"

echo "== 6. Comparación exacta de contenido (todas las tablas base) =="
LISTA_RAW="$("${MYSQL_O[@]}" -e "SELECT table_name FROM information_schema.tables WHERE table_schema='restaurante_origen' AND table_type='BASE TABLE' ORDER BY table_name" 2>&1)" \
    || { echo "LISTA_FALLO: $LISTA_RAW" >&2; fail "no pude listar las tablas base del origen"; }
TABLAS="$LISTA_RAW"
[ -n "$TABLAS" ] || fail "sin tablas base en el origen"
mismatch=0
while IFS= read -r tabla; do
    [ -n "$tabla" ] || continue
    CA="$("${MYSQL_O[@]}" -e "SELECT COUNT(*) FROM \`$tabla\`" 2>/dev/null)" || { echo "  ERROR conteo en origen: $tabla"; mismatch=$((mismatch+1)); continue; }
    CS="$("${MYSQL_S[@]}" -e "SELECT COUNT(*) FROM \`$tabla\`" 2>/dev/null)" || { echo "  ERROR conteo en secundaria: $tabla"; mismatch=$((mismatch+1)); continue; }
    if [ "$CA" != "$CS" ]; then
        echo "  DIFERENCIA: $tabla origen=$CA secundaria=$CS"
        mismatch=$((mismatch+1))
    fi
done <<< "$TABLAS"
[ "$mismatch" -eq 0 ] || fail "$mismatch tablas difieren o fallaron entre origen y secundaria"
ok "todas las tablas base conservan el mismo conteo en la secundaria"

echo "== 7. Invariantes post-restauración en la secundaria =="
MAX_O="$("${MYSQL_O[@]}" -e "SELECT COALESCE(MAX(version),0) FROM schema_migrations WHERE estado='APLICADA'")"
MAX_S="$("${MYSQL_S[@]}" -e "SELECT COALESCE(MAX(version),0) FROM schema_migrations WHERE estado='APLICADA'")"
[ "$MAX_O" = "$MAX_S" ] || fail "versiones de migración distinta (origen=$MAX_O secundaria=$MAX_S)"
ok "migración máx aplicada v$MAX_S idéntica en ambos servidores"
ESTADO="$("${MYSQL_S[@]}" -e 'SELECT estado FROM restaurante_control WHERE id=1')"
[ "$ESTADO" = "OPERATIVO" ] || fail "restaurante_control quedó $ESTADO en la secundaria"
ok "restaurante_control=OPERATIVO en la secundaria"

echo "== 8. CHECK TABLE EXTENDED en todas las tablas de la secundaria =="
mal_check=0
while IFS= read -r tabla; do
    [ -n "$tabla" ] || continue
    RESULTADO="$("${MYSQL_S[@]}" -e "CHECK TABLE \`$tabla\` EXTENDED" | awk -F'\t' 'NF>=3 {print $(NF)}' | tr '[:upper:]' '[:lower:]')"
    if [ "$RESULTADO" != "ok" ] && [ "$RESULTADO" != "note" ] && [ "$RESULTADO" != "table is already up to date" ]; then
        echo "  fallo de integridad: $tabla -> $RESULTADO"
        mal_check=$((mal_check+1))
    fi
done <<< "$TABLAS"
[ "$mal_check" -eq 0 ] || fail "$mal_check tablas con CHECK TABLE EXTENDED fallido"
ok "CHECK TABLE EXTENDED limpio en toda la secundaria"

echo "== 9. Verificación pura del respaldo en el TERCER servidor (3309) =="
run_cli "$WORKDIR" env DB_URL="$SQL_VERIFICADOR" DB_USER='admin_verificar' DB_PASSWORD='admin_verify_pass' DB_NAME='restaurante_verif' \
    MYSQL_USER='admin_verificar' MYSQL_PASSWORD='admin_verify_pass' MYSQL_DATABASE='restaurante_verif' \
    BACKUP_HMAC_KEY="$HMAC_TEST" "${CLASE_JAVA[@]}" --verificar-respaldo "$ORIGINAL" \
    > "$WORKDIR/verificar_tercer.log" 2>&1 || { cat "$WORKDIR/verificar_tercer.log" >&2; fail "la verificación falló en el tercer servidor"; }
ok "VERIFICACION_OK en el tercer servidor MySQL independiente"

echo "== 10. Fail-closed en la secundaria: respaldo corrupto y truncado NO se restauran =="
cp -f "$ORIGINAL" "$WORKDIR/corrupto.sql"
TAMANO="$(stat -c %s "$WORKDIR/corrupto.sql")"
printf '\\xff' | dd of="$WORKDIR/corrupto.sql" bs=1 seek=$((TAMANO/2)) conv=notrunc 2>/dev/null
if run_cli "$WORKDIR" env DB_URL="$SQL_SECUNDARIA" DB_USER='admin_restaurar' DB_PASSWORD='admin_restore_pass' DB_NAME='restaurante_secundaria' \
        MYSQL_USER='admin_restaurar' MYSQL_PASSWORD='admin_restore_pass' MYSQL_DATABASE='restaurante_secundaria' \
        BACKUP_HMAC_KEY="$HMAC_TEST" "${CLASE_JAVA[@]}" --restaurar-respaldo "$WORKDIR/corrupto.sql" \
        > "$WORKDIR/restauracion_corrupto.log" 2>&1; then
    fail "la restauración aceptó un respaldo corrupto"
fi
ok "respaldo corrupto rechazado por la secundaria"

TRUNCADO=$((TAMANO - 300))
[ "$TRUNCADO" -gt 0 ] || TRUNCADO=$((TAMANO/2))
head -c "$TRUNCADO" "$ORIGINAL" > "$WORKDIR/truncado.sql"
if run_cli "$WORKDIR" env DB_URL="$SQL_SECUNDARIA" DB_USER='admin_restaurar' DB_PASSWORD='admin_restore_pass' DB_NAME='restaurante_secundaria' \
        MYSQL_USER='admin_restaurar' MYSQL_PASSWORD='admin_restore_pass' MYSQL_DATABASE='restaurante_secundaria' \
        BACKUP_HMAC_KEY="$HMAC_TEST" "${CLASE_JAVA[@]}" --restaurar-respaldo "$WORKDIR/truncado.sql" \
        > "$WORKDIR/restauracion_truncado.log" 2>&1; then
    fail "la restauración aceptó un respaldo truncado"
fi
ok "respaldo truncado rechazado por la secundaria"

PEDIDOS_DESPUES="$("${MYSQL_S[@]}" -e 'SELECT COUNT(*) FROM pedidos')"
[ "$PEDIDOS_DESPUES" = "$PEDIDOS_ANTES" ] || fail "la BD secundaria cambió tras los intentos fallidos ($PEDIDOS_ANTES -> $PEDIDOS_DESPUES)"
ESTADO="$("${MYSQL_S[@]}" -e 'SELECT estado FROM restaurante_control WHERE id=1')"
[ "$ESTADO" = "OPERATIVO" ] || fail "la secundaria quedó $ESTADO tras los intentos fallidos"
ok "la BD secundaria permaneció intacta y OPERATIVA tras ambos intentos fallidos"

echo "== 11. Snapshot REAL de producción verificado en servidor distinto (solo lectura) =="
CLAVE_REAL="$(grep -E '^BACKUP_HMAC_KEY=' .env | head -1 | cut -d= -f2- | tr -d '"' | tr -d "'" | tr -d '[:space:]')"
[ -n "$CLAVE_REAL" ] || fail "BACKUP_HMAC_KEY no legible desde .env"
[ "${#CLAVE_REAL}" -ge 32 ] || fail "BACKUP_HMAC_KEY real no cumple el mínimo (32 bytes)"
RESPALDO_PROD="$(ls -t Restaurante_Ejecutable/respaldos/*.sql | head -1)"
cp -f "$RESPALDO_PROD" "$RESPALDO_PROD.manifest" "$RESPALDO_PROD.sha256" "$WORKDIR/"
echo "  respaldo real existente: $(basename "$RESPALDO_PROD") ($(stat -c%s "$RESPALDO_PROD") bytes) + manifest + sha256"

validar_respaldo_prod_en_verificador() {
    local archivo="$1" log="$2"
    run_cli "$WORKDIR" env DB_URL='jdbc:mysql://127.0.0.1:3309/restaurante_verif?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
        DB_USER='admin_verificar' DB_PASSWORD='admin_verify_pass' DB_NAME='restaurante_verif' \
        MYSQL_USER='admin_verificar' MYSQL_PASSWORD='admin_verify_pass' MYSQL_DATABASE='restaurante_verif' \
        BACKUP_HMAC_KEY="$CLAVE_REAL" "${CLASE_JAVA[@]}" --verificar-respaldo "$archivo" > "$log" 2>&1
}

RESPALDO_PROD_COPIA="$WORKDIR/$(basename "$RESPALDO_PROD")"
if validar_respaldo_prod_en_verificador "$RESPALDO_PROD_COPIA" "$WORKDIR/verificar_prod_real.log"; then
    ok "el respaldo REAL de producción es validado, importado y conciliado en un servidor MySQL distinto (firma HMAC de producción)"
else
    if grep -q "VERIFICACION_FALLIDA: El respaldo no tiene checksum" "$WORKDIR/verificar_prod_real.log"; then
        echo "  hallazgo: el respaldo histórico de producción NO lleva sección de checksum (formato anterior al jar actual)"
        echo "  generando un respaldo NUEVO de producción (solo lectura) con la clave HMAC real para verificarlo en el servidor distinto..."
        DIR_PROD="$EVIDENCIA/respaldos-produccion"
        mkdir -p "$DIR_PROD"
        leer_env() { grep -E "^$1=" "$ROOT/.env" | head -1 | cut -d= -f2- | tr -d '"' | tr -d "'"; }
        PROD_HOST="$(leer_env MYSQL_HOST)"; PROD_PORT="$(leer_env MYSQL_PORT)"
        PROD_USER="$(leer_env MYSQL_USER)"; PROD_PASS="$(leer_env MYSQL_PASSWORD)"; PROD_DB="$(leer_env MYSQL_DATABASE)"
        [ -n "$PROD_HOST" ] && [ -n "$PROD_PORT" ] && [ -n "$PROD_USER" ] && [ -n "$PROD_PASS" ] && [ -n "$PROD_DB" ] \
            || fail "faltan variables MYSQL_* en .env para el respaldo de producción"
        run_cli "$WORKDIR" env DB_URL="jdbc:mysql://${PROD_HOST}:${PROD_PORT}/${PROD_DB}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
            DB_USER="$PROD_USER" DB_PASSWORD="$PROD_PASS" DB_NAME="$PROD_DB" \
            MYSQL_USER="$PROD_USER" MYSQL_PASSWORD="$PROD_PASS" MYSQL_DATABASE="$PROD_DB" \
            BACKUP_HMAC_KEY="$CLAVE_REAL" "${CLASE_JAVA[@]}" --respaldar "$DIR_PROD" \
            > "$WORKDIR/respaldo_prod_fresco.log" 2>&1 \
            || { tail -20 "$WORKDIR/respaldo_prod_fresco.log" >&2; fail "no se pudo generar un respaldo fresco de producción (solo lectura)"; }
        RESPALDO_PROD_FRESCO="$(sed -n 's/^RESPALDO_OK //p' "$WORKDIR/respaldo_prod_fresco.log" | tr -d '\r')"
        [ -f "$RESPALDO_PROD_FRESCO" ] || fail "RESPALDO_OK sin archivo en el respaldo fresco de producción"
        echo "  respaldo fresco: $(basename "$RESPALDO_PROD_FRESCO") ($(stat -c%s "$RESPALDO_PROD_FRESCO") bytes) con manifest y sha256 en $(basename "$DIR_PROD")"
        validar_respaldo_prod_en_verificador "$RESPALDO_PROD_FRESCO" "$WORKDIR/verificar_prod_fresco.log" \
            || { cat "$WORKDIR/verificar_prod_fresco.log" >&2; fail "el respaldo FRESCO de producción no pasó verificación en el servidor distinto"; }
        ok "el respaldo FRESCO de producción (jars actuales, clave HMAC real) se valida, importa y concilia en un servidor MySQL distinto"
    else
        cat "$WORKDIR/verificar_prod_real.log" >&2
        fail "el respaldo real de producción no pasó verificación por un motivo inesperado"
    fi
fi

for f in respaldo1 verificar_secundaria restauracion_secundaria verificar_tercer restauracion_corrupto restauracion_truncado verificar_prod_real respaldo_prod_fresco verificar_prod_fresco; do
    [ -f "$WORKDIR/$f.log" ] && cp -f "$WORKDIR/$f.log" "$EVIDENCIA/secundaria-$f.log"
done
[ -n "${RESPALDO_PROD_FRESCO:-}" ] && cp -f "$RESPALDO_PROD_FRESCO" "$EVIDENCIA/secundaria-respaldo-produccion-fresco.sql"

echo "RESTAURACION_SECUNDARIA_OK"

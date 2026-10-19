#!/usr/bin/env bash
# Redteam: verifica que la autoreparación de esquema (MigradorEsquemaJdbc) lleve una
# BD degradada CON DATOS al esquema v15 sin destruir los datos existentes.
#
# Escenario:
#   0. Fixture con datos de negocio (BD.sql + semilla de integración + datos extra).
#   1. Degradación controlada del esquema (tablas/columnas/índices/FKs/checks de
#      migraciones v5..v15, marcador schema_migrations en v13 y basura intencional),
#      exigiendo que cada objeto degradado esté vacío o en valores default.
#   2. Foto de los datos existentes (columnas de la BD "vieja").
#   3. Se ejecuta la ruta real de actualización del cliente: `Restaurante --migrate-db`.
#   4. Se exige: reparación completa, cero filas perdidas/cambiadas (salvo limpiezas
#      intencionales documentadas), respaldo pre-migración creado y verificable,
#      idempotencia en segunda corrida y reanudación de una migración interrumpida.
#   5. Guardas: BD vacía y BD "del futuro" (v99) deben rechazarse sin escribir nada.
#
# No toca la BD de servicio: usa un MySQL efímero propio en 127.0.0.1:3307.
set -euo pipefail

cd "$(dirname "$0")"
RAIZ="$(pwd)"

command -v docker >/dev/null || { echo "Falta Docker" >&2; exit 1; }
command -v java >/dev/null || { echo "Falta Java 21" >&2; exit 1; }
command -v ant >/dev/null || { echo "Falta Apache Ant" >&2; exit 1; }

if (: >/dev/tcp/127.0.0.1/3307) 2>/dev/null; then
    echo "El puerto 3307 ya está ocupado; el redteam no detendrá contenedores ajenos." >&2
    exit 1
fi

PROYECTO="restaurante-redteam-auto-$$-$(date +%s)"
COMPOSE=(docker compose -p "$PROYECTO" -f docker-compose.integration.yml)
DIRECTORIO="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-redteam-auto-XXXXXX")"

limpiar() {
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    if [ "${REDTEAM_AUTOREPARACION_KEEP:-0}" = "1" ]; then
        echo "== Conservando directorio de trabajo (REDTEAM_AUTOREPARACION_KEEP=1): $DIRECTORIO" >&2
    else
        rm -rf -- "$DIRECTORIO"
    fi
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
# Sin DISPLAY (jamás tocar el escritorio del operador) y con puerto de instancia
# efímero para no chocar con el servicio de producción en 49200.
CLASE_JAVA=(env -u DISPLAY java -Drestaurante.instancia.puerto=0 -cp "$RAIZ/build/classes:$RAIZ/librerias/*" restaurante.Restaurante)
MYSQL=(env MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -P3306 -urestaurante_test_app restaurante_test -N -B)

mysql_exec() {
    "${COMPOSE[@]}" exec -T db "${MYSQL[@]}" -e "$1" </dev/null
}

mysql_root() {
    "${COMPOSE[@]}" exec -T db env MYSQL_PWD=test_root_pass mysql --protocol=TCP -h127.0.0.1 -P3306 -uroot -N -B -e "$1" </dev/null
}

fallo() {
    echo "FALLO: $*" >&2
    exit 1
}

igual() { # esperado obtenido descripcion
    if [ "$1" != "$2" ]; then fallo "$3 (esperado=$1, obtenido=$2)"; fi
    echo "  OK: $3"
}

afirmar() { # descripcion comando...
    local descripcion=$1; shift
    "$@" || fallo "$descripcion"
    echo "  OK: $descripcion"
}

exigir_cero() { # consulta descripcion
    local n
    n="$(mysql_exec "$1")"
    if [ "$n" != "0" ]; then fallo "el fixture no cumple '$2' (obtenido=$n); el redteam no sigue para no destruir datos"; fi
    echo "  OK (fixture seguro): $2"
}

exigir_vacio() {
    local t n
    for t in "$@"; do
        n="$(mysql_exec "SELECT COUNT(*) FROM \`$t\`")"
        if [ "$n" != "0" ]; then fallo "la tabla $t tiene $n filas; el redteam no la destruirá"; fi
    done
}

existe_tabla()   { [ "$(mysql_exec "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='$1'")" = "1" ]; }
existe_columna() { [ "$(mysql_exec "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$1' AND column_name='$2'")" = "1" ]; }
existe_indice()  { [ "$(mysql_exec "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='$1' AND index_name='$2'")" = "1" ]; }
existe_restriccion() { [ "$(mysql_exec "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name='$1' AND constraint_name='$2'")" = "1" ]; }

correr_migracion() { # log [asignaciones env...] ; imprime el rc por stdout
    local log="$1"; shift
    local rc=0
    ( cd "$DIRECTORIO" && env "$@" "${CLASE_JAVA[@]}" --migrate-db ) >"$log" 2>&1 </dev/null || rc=$?
    echo "$rc"
}

contar_backups() { find "$DIRECTORIO" -name 'pre_migracion_restaurante_*.sql' | wc -l; }

# --- Foto de datos: columnas capturadas en la BD "vieja" (post-degradación).
# Se excluyen solo columnas que la degradación elimina a propósito y cuyos valores
# quedan demostrados como default o derivados (check de seguridad más abajo).
TABLAS_FOTO="clientes platos salas config configuracion_sistema usuarios mesoneros categorias plato_categoria plato_favorito pedidos detalle_pedidos pedido_pagos auditoria_pedidos login_intentos cierres_caja"
EXCLUIR_COLUMNAS="categorias.activo pedidos.id_sala_pendiente pedidos.num_mesa_pendiente pedidos.version_precio detalle_pedidos.precio_congelado detalle_pedidos.congelado_motivo detalle_pedidos.congelado_por detalle_pedidos.congelado_en usuarios.nombre_unico configuracion_sistema.actualizado_en"
COLS_FOTO="$DIRECTORIO/columnas.tsv"

capturar_columnas_foto() {
    : > "$COLS_FOTO"
    local t cols filtrados
    for t in $TABLAS_FOTO; do
        cols="$(mysql_exec "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$t'")"
        if [ -z "$cols" ] || [ "$cols" = "NULL" ]; then fallo "tabla $t ausente al capturar columnas"; fi
        filtrados="$(echo "$cols" | tr ',' '\n' | grep -vxF -f <(echo "$EXCLUIR_COLUMNAS" | tr ' ' '\n' | sed "s/^$t\.//" | grep -v '\.') || true)"
        filtrados="$(echo "$filtrados" | paste -sd, -)"
        if [ -z "$filtrados" ]; then fallo "sin columnas para $t"; fi
        printf '%s\t%s\n' "$t" "$filtrados" >> "$COLS_FOTO"
    done
}

foto() { # archivo destino
    local salida="$1" t cols n orden
    : > "$salida"
    while IFS=$'\t' read -r t cols; do
        n="$(awk -F',' '{print NF}' <<<"$cols")"
        orden="$(seq -s, 1 "$n")"
        mysql_exec "SELECT $cols FROM \`$t\` ORDER BY $orden" | sed "s|^|$t\t|" >> "$salida"
    done < "$COLS_FOTO"
}

comparar_fotos() { # antes despues etiqueta
    local antes="$1" despues="$2" etiqueta="$3"
    LC_ALL=C sort "$antes" > "$DIRECTORIO/.antes.ord"
    LC_ALL=C sort "$despues" > "$DIRECTORIO/.despues.ord"
    LC_ALL=C comm -23 "$DIRECTORIO/.antes.ord" "$DIRECTORIO/.despues.ord" > "$DIRECTORIO/.removidos.ord" || true
    # Remociones/cambios intencionales conocidos y documentados:
    #  - huérfano de login_intentos (la FK cascade exige borrarlo);
    #  - claves obsoletas _sec_login_* (purga diseñada);
    #  - normalización del parámetro booleano legacy 'true'->'1' (parámetro migrado desde config).
    grep -a -vE $'^login_intentos\t999999\t|^configuracion_sistema\t_sec_login_|^configuracion_sistema\timprimir_logo_ticket\ttrue\t' "$DIRECTORIO/.removidos.ord" > "$DIRECTORIO/.removidos.inesperados" || true
    if [ -s "$DIRECTORIO/.removidos.inesperados" ]; then
        echo "--- filas perdidas o cambiadas ---" >&2
        cat "$DIRECTORIO/.removidos.inesperados" >&2
        fallo "$etiqueta: la migración alteró datos existentes"
    fi
    echo "  OK: $etiqueta (cero filas perdidas o cambiadas; solo limpiezas intencionales)"
}

echo "== Compilando =="
ant -q compile

echo "== Levantando MySQL efímero (proyecto $PROYECTO) =="
"${COMPOSE[@]}" up -d --wait

echo "== 0. Fixture: datos de negocio sobre la semilla =="
mysql_exec "
INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd, es_legado)
 SELECT id,'EFECTIVO','USD',total,COALESCE(tasa_cambio,1),total_bs,total,1 FROM pedidos WHERE id=100;
INSERT INTO clientes (documento,nombre,telefono,direccion) VALUES ('V-12345678','Cliente Redteam Uno','0414-0000000','Caracas');
INSERT INTO pedidos (id,id_sala,num_mesa,tipo_pedido,subtotal,iva_porcentaje,iva_monto,total,subtotal_bs,iva_bs,total_bs,tasa_cambio,usuario,estado,fecha,cerrada_en,cliente_nombre,cliente_documento)
 VALUES (101,1,2,'MESA',20.00,16.00,3.20,23.20,896.00,143.36,1039.36,44.8000,'admin','FINALIZADO',NOW(),NOW(),'Cliente Redteam Uno','V-12345678');
INSERT INTO detalle_pedidos (id_pedido,nombre,precio,cantidad,aplica_iva,id_plato,moneda_base,precio_incluye_iva,iva_porcentaje)
 VALUES (101,'Plato Redteam',20.00,1,1,1,'USD',0,16.00);
INSERT INTO pedido_pagos (id_pedido,metodo_pago,moneda,monto,tasa_cambio,monto_bs,monto_usd,es_legado)
 VALUES (101,'EFECTIVO','USD',23.20,44.8000,1039.36,23.20,1);
INSERT INTO pedidos (id,id_sala,num_mesa,tipo_pedido,subtotal,iva_porcentaje,iva_monto,total,subtotal_bs,iva_bs,total_bs,tasa_cambio,usuario,estado,cliente_nombre,cliente_documento)
 VALUES (102,1,3,'MESA',5.00,16.00,0.80,5.80,224.00,35.84,259.84,44.8000,'admin','PENDIENTE','Consumidor Final','V-00000000');
INSERT INTO detalle_pedidos (id_pedido,nombre,precio,cantidad,aplica_iva,id_plato,moneda_base,precio_incluye_iva,iva_porcentaje)
 VALUES (102,'Postre Redteam',5.00,1,1,1,'USD',0,16.00);
INSERT INTO login_intentos (usuario_id, intentos) VALUES (1, 2);
"
echo "  datos base: pedidos=$(mysql_exec "SELECT COUNT(*) FROM pedidos") clientes=$(mysql_exec "SELECT COUNT(*) FROM clientes") platos=$(mysql_exec "SELECT COUNT(*) FROM platos") detalle=$(mysql_exec "SELECT COUNT(*) FROM detalle_pedidos") pagos=$(mysql_exec "SELECT COUNT(*) FROM pedido_pagos")"

echo "== 1. Degradación controlada del esquema (BD cliente vieja) =="
exigir_vacio comandas_marchas comandas_marchas_detalle auditoria_cambios_precio pedido_saldos_movimientos pedido_liquidaciones preferencias_tabla
exigir_cero "SELECT COUNT(*) FROM categorias WHERE activo <> 1 OR activo IS NULL" "categorias.activo solo con valor 1"
exigir_cero "SELECT COUNT(*) FROM pedidos WHERE version_precio <> 1 OR version_precio IS NULL" "pedidos.version_precio solo con valor 1"
exigir_cero "SELECT COUNT(*) FROM detalle_pedidos WHERE precio_congelado <> 0 OR congelado_motivo IS NOT NULL OR congelado_por IS NOT NULL OR congelado_en IS NOT NULL" "detalle_pedidos.congelado_* en default"

# La foto de columnas se captura ANTES de degradar: refleja la BD "vieja" del cliente.
capturar_columnas_foto
echo "  foto de referencia: $(wc -l < "$COLS_FOTO") tablas del cliente viejo"

MYSQL_DANO="
DROP TABLE comandas_marchas_detalle;
DROP TABLE comandas_marchas;
DROP TABLE pedido_saldos_movimientos;
ALTER TABLE pedido_pagos DROP FOREIGN KEY fk_pedido_pagos_liquidacion;
DROP TABLE pedido_liquidaciones;
DROP TABLE auditoria_cambios_precio;
DROP TABLE preferencias_tabla;
ALTER TABLE categorias DROP COLUMN activo;
ALTER TABLE pedidos DROP INDEX uq_pedidos_mesa_pendiente;
ALTER TABLE pedidos DROP COLUMN id_sala_pendiente;
ALTER TABLE pedidos DROP COLUMN num_mesa_pendiente;
ALTER TABLE pedidos DROP COLUMN version_precio;
ALTER TABLE detalle_pedidos DROP COLUMN precio_congelado;
ALTER TABLE detalle_pedidos DROP COLUMN congelado_motivo;
ALTER TABLE detalle_pedidos DROP COLUMN congelado_por;
ALTER TABLE detalle_pedidos DROP COLUMN congelado_en;
ALTER TABLE usuarios DROP INDEX uq_usuarios_nombre;
ALTER TABLE usuarios DROP COLUMN nombre_unico;
ALTER TABLE pedidos DROP INDEX idx_pedidos_estado_fecha;
ALTER TABLE pedidos DROP INDEX idx_pedidos_cliente_doc;
ALTER TABLE pedidos DROP INDEX idx_pedidos_mesonero;
ALTER TABLE login_intentos DROP FOREIGN KEY fk_login_intentos_usuario;
ALTER TABLE login_intentos DROP CHECK chk_login_intentos_no_negativos;
ALTER TABLE detalle_pedidos DROP FOREIGN KEY fk_detalle_pedidos_plato;
ALTER TABLE detalle_pedidos DROP CHECK chk_detalle_precio_iva_coherente;
ALTER TABLE config ADD COLUMN modo_atencion VARCHAR(20) NULL DEFAULT NULL;
UPDATE config SET modo_atencion='OPCION_B' WHERE id=1;
DELETE FROM schema_migrations WHERE version >= 14;
INSERT INTO login_intentos (usuario_id, intentos) VALUES (999999, 7);
INSERT INTO configuracion_sistema (clave, valor) VALUES ('_sec_login_basura', '1');
"
mysql_exec "$MYSQL_DANO"
echo "  degradación aplicada"

foto "$DIRECTORIO/foto1.txt"
echo "  filas de datos existentes (base): $(wc -l < "$DIRECTORIO/foto1.txt")"

echo "== 2. Autoreparación: Restaurante --migrate-db (ruta real del cliente) =="
RC="$(correr_migracion "$DIRECTORIO/run1.log")"
igual "0" "$RC" "run1 termina con código 0"
grep -qE "Migraci.n de base de datos validada correctamente" "$DIRECTORIO/run1.log" || { cat "$DIRECTORIO/run1.log" >&2; fallo "run1 no confirmó la migración"; }
echo "  OK: run1 confirmó 'Migración de base de datos validada correctamente'"

echo "== 3. Verificación de la reparación =="
for t in comandas_marchas comandas_marchas_detalle auditoria_cambios_precio pedido_liquidaciones pedido_saldos_movimientos preferencias_tabla; do
    afirmar "tabla reparada: $t" existe_tabla "$t"
done
afirmar "columna reparada: categorias.activo" existe_columna categorias activo
afirmar "columna reparada: pedidos.version_precio" existe_columna pedidos version_precio
afirmar "columna reparada: pedidos.id_sala_pendiente" existe_columna pedidos id_sala_pendiente
afirmar "columna reparada: pedidos.num_mesa_pendiente" existe_columna pedidos num_mesa_pendiente
afirmar "columna reparada: detalle_pedidos.precio_congelado" existe_columna detalle_pedidos precio_congelado
afirmar "columna derivada reparada: usuarios.nombre_unico" existe_columna usuarios nombre_unico
if existe_columna config modo_atencion; then fallo "la columna legado config.modo_atencion no fue eliminada"; fi
echo "  OK: columna legado config.modo_atencion eliminada"
afirmar "índice reparado: uq_pedidos_mesa_pendiente" existe_indice pedidos uq_pedidos_mesa_pendiente
afirmar "índice reparado: idx_pedidos_estado_fecha" existe_indice pedidos idx_pedidos_estado_fecha
afirmar "índice reparado: idx_pedidos_cliente_doc" existe_indice pedidos idx_pedidos_cliente_doc
afirmar "índice reparado: idx_pedidos_mesonero" existe_indice pedidos idx_pedidos_mesonero
afirmar "índice reparado: uq_usuarios_nombre" existe_indice usuarios uq_usuarios_nombre
afirmar "FK reparada: fk_login_intentos_usuario" existe_restriccion login_intentos fk_login_intentos_usuario
afirmar "FK reparada: fk_pedido_pagos_liquidacion" existe_restriccion pedido_pagos fk_pedido_pagos_liquidacion
afirmar "FK reparada: fk_detalle_pedidos_plato" existe_restriccion detalle_pedidos fk_detalle_pedidos_plato
afirmar "CHECK reparado: chk_login_intentos_no_negativos" existe_restriccion login_intentos chk_login_intentos_no_negativos
afirmar "CHECK reparado: chk_detalle_precio_iva_coherente" existe_restriccion detalle_pedidos chk_detalle_precio_iva_coherente

igual "0" "$(mysql_exec "SELECT COUNT(*) FROM login_intentos WHERE usuario_id=999999")" "huérfano de login_intentos eliminado"
igual "1" "$(mysql_exec "SELECT COUNT(*) FROM login_intentos WHERE usuario_id=1 AND intentos=2")" "intento válido preservado"
igual "0" "$(mysql_exec "SELECT COUNT(*) FROM configuracion_sistema WHERE clave='_sec_login_basura'")" "clave _sec_login_* purgada"
igual "OPCION_B" "$(mysql_exec "SELECT valor FROM configuracion_sistema WHERE clave='modo_atencion'")" "configuracion_sistema.modo_atencion intacto"
igual "APLICADA" "$(mysql_exec "SELECT estado FROM schema_migrations WHERE version=15")" "schema_migrations v15 APLICADA"
igual "OPERATIVO" "$(mysql_exec "SELECT estado FROM restaurante_control WHERE id=1")" "estado de control OPERATIVO"
igual "NULL" "$(mysql_exec "SELECT IFNULL(operacion_id,'NULL') FROM restaurante_control WHERE id=1")" "sin operación de mantenimiento colgada"
igual "0" "$(mysql_exec "SELECT COUNT(*) FROM categorias WHERE activo <> 1 OR activo IS NULL")" "categorias.activo restaurado como default 1"
igual "0" "$(mysql_exec "SELECT COUNT(*) FROM pedidos WHERE estado='PENDIENTE' AND (id_sala_pendiente<>id_sala OR num_mesa_pendiente<>num_mesa)")" "columnas generadas coherentes en PENDIENTES"
igual "0" "$(mysql_exec "SELECT COUNT(*) FROM pedidos WHERE estado<>'PENDIENTE' AND (id_sala_pendiente IS NOT NULL OR num_mesa_pendiente IS NOT NULL)")" "columnas generadas nulas en no PENDIENTES"
igual "0" "$(mysql_exec "SELECT COUNT(*) FROM usuarios WHERE nombre_unico <> LOWER(TRIM(nombre))")" "usuarios.nombre_unico coherente"
igual "1" "$(mysql_exec "SELECT valor FROM configuracion_sistema WHERE clave='imprimir_logo_ticket'")" "parámetro legacy normalizado: imprimir_logo_ticket 'true'->'1' (equivalente para la app)"

echo "== 4. Integridad de datos existentes (foto antes/después) =="
foto "$DIRECTORIO/foto2.txt"
comparar_fotos "$DIRECTORIO/foto1.txt" "$DIRECTORIO/foto2.txt" "datos intactos tras la autoreparación"

echo "== 5. Respaldo previo a la migración =="
igual "1" "$(contar_backups)" "exactamente un respaldo pre_migracion_* creado"
BKP="$(find "$DIRECTORIO" -name 'pre_migracion_restaurante_*.sql' -print -quit)"
echo "  respaldo: $(basename "$BKP")"
# El archivo de respaldo se verifica con la ruta real de verificación del CLI.
set +e
( cd "$DIRECTORIO" && "${CLASE_JAVA[@]}" --verificar-respaldo "$BKP" ) > "$DIRECTORIO/verificar-respaldo.log" 2>&1 </dev/null
RCV=$?
set -e
if [ "$RCV" -ne 0 ]; then
    sed -n '1,120p' "$DIRECTORIO/verificar-respaldo.log" >&2
    fallo "el respaldo previo no pasa --verificar-respaldo (rc=$RCV)"
fi
echo "  OK: el respaldo previo pasa --verificar-respaldo"
grep -q "VERIFICACION_OK" "$DIRECTORIO/verificar-respaldo.log" || fallo "verificación sin VERIFICACION_OK"

echo "== 6. Idempotencia: segunda corrida no cambia nada =="
RC="$(correr_migracion "$DIRECTORIO/run2.log")"
igual "0" "$RC" "run2 termina con código 0"
igual "1" "$(contar_backups)" "run2 no crea respaldos adicionales (esquema ya aplicado)"
foto "$DIRECTORIO/foto3.txt"
comparar_fotos "$DIRECTORIO/foto2.txt" "$DIRECTORIO/foto3.txt" "datos estables tras segunda corrida"

echo "== 7. Reanudación de migración interrumpida (EN_CURSO + artefacto faltante) =="
mysql_exec "UPDATE schema_migrations SET estado='EN_CURSO', paso_actual='asegurar_esquema' WHERE version=15"
mysql_exec "DROP TABLE comandas_marchas_detalle; DROP TABLE comandas_marchas;"
RC="$(correr_migracion "$DIRECTORIO/run3.log")"
igual "0" "$RC" "run3 termina con código 0"
igual "APLICADA" "$(mysql_exec "SELECT estado FROM schema_migrations WHERE version=15")" "v15 vuelve a APLICADA"
afirmar "tabla reparada tras interrupción: comandas_marchas" existe_tabla comandas_marchas
igual "2" "$(contar_backups)" "la reanudación creó su propio respaldo (total 2)"
foto "$DIRECTORIO/foto4.txt"
comparar_fotos "$DIRECTORIO/foto2.txt" "$DIRECTORIO/foto4.txt" "datos intactos tras reanudación"

echo "== 8. Guardas fail-closed =="
mysql_root "CREATE DATABASE redteam_vacia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
RC="$(correr_migracion "$DIRECTORIO/guardB.log" DB_URL='jdbc:mysql://127.0.0.1:3307/redteam_vacia?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' DB_NAME=redteam_vacia MYSQL_DATABASE=redteam_vacia)"
if [ "$RC" = "0" ]; then fallo "la migración no debe aceptar una BD vacía"; fi
grep -q "no tiene esquema" "$DIRECTORIO/guardB.log" || { cat "$DIRECTORIO/guardB.log" >&2; fallo "guardB no rechazó por esquema vacío"; }
igual "0" "$(mysql_root "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='redteam_vacia'")" "BD vacía quedó intacta (0 tablas creadas)"
echo "  OK: una BD vacía se rechaza sin escribir nada"

mysql_root "CREATE DATABASE redteam_futura CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
"${COMPOSE[@]}" exec -T db bash -c "MYSQL_PWD=test_root_pass mysqldump --protocol=TCP -h127.0.0.1 -P3306 -uroot --skip-triggers --no-data --skip-comments --skip-dump-date restaurante_test | MYSQL_PWD=test_root_pass mysql --protocol=TCP -h127.0.0.1 -P3306 -uroot redteam_futura" >/dev/null 2>&1
mysql_root "INSERT INTO redteam_futura.schema_migrations (version,nombre,checksum,estado,paso_actual) VALUES (99,'futuro','0','APLICADA','x')"
RC="$(correr_migracion "$DIRECTORIO/guardC.log" DB_URL='jdbc:mysql://127.0.0.1:3307/redteam_futura?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' DB_NAME=redteam_futura MYSQL_DATABASE=redteam_futura)"
if [ "$RC" = "0" ]; then fallo "la migración no debe aceptar una BD más nueva"; fi
grep -qE "m.s nueva que esta aplicaci.n" "$DIRECTORIO/guardC.log" || { cat "$DIRECTORIO/guardC.log" >&2; fallo "guardC no rechazó por versión futura"; }
igual "0" "$(mysql_root "SELECT COUNT(*) FROM redteam_futura.schema_migrations WHERE version=15")" "BD futura sin rastro de migración aplicada"
echo "  OK: una BD de versión futura se rechaza sin escribir esquema v15"

igual "2" "$(contar_backups)" "las guardas no crearon respaldos"

echo
echo "REDTEAM_AUTOREPARACION_OK"

#!/usr/bin/env bash
# Drill de corte eléctrico: demuestra que los datos comprometidos sobreviven
# a la muerte abrupta de la BD (SIGKILL sin apagado limpio = corte de luz) y
# que InnoDB se recupera solo al reiniciar, sin intervención.
# Fases:
#   1) BD desechable en 3307 con datos comprometidos y una transacción a medias.
#   2) docker kill --signal=KILL (corte eléctrico) con escritores activos.
#   3) Restart del mismo contenedor (mismo volumen) -> recuperación InnoDB.
#   4) Conciliación: todo COMMIT con acuse recibido existe; lo no comprometido
#      no dejó residuos (atomicidad); CHECK TABLE y conteo de integridad.
#   5) Segundo corte y segunda recuperación (cortes repetidos de luz).
# No toca la BD de servicio ni datos reales: solo compose efímero -p drillcorte.
set -euo pipefail

cd "$(dirname "$0")"

command -v docker >/dev/null || { echo "Falta Docker" >&2; exit 1; }

PUERTO=${DRILL_CORTE_PUERTO:-3307}
if (: >/dev/tcp/127.0.0.1/"$PUERTO") 2>/dev/null; then
    echo "El puerto $PUERTO ya está ocupado; el drill no detendrá contenedores ajenos." >&2
    exit 1
fi

PROYECTO="drillcorte"
COMPOSE=(docker compose -p "$PROYECTO" -f docker-compose.e2e.yml)
DIRECTORIO="$(mktemp -d /tmp/opencode/drill-corte-XXXXXX)"
RECIBO="$DIRECTORIO/recibo.txt"

limpiar() {
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    rm -rf -- "$DIRECTORIO"
    # usados en reintentos del iterador del drill
    docker ps -a --format '{{.Names}}' | grep -F "$PROYECTO-db-1" | \
        xargs --no-run-if-empty docker rm -f >/dev/null 2>&1 || true
}
trap limpiar EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

MYSQL_USUARIA='restaurante_test_app'
MYSQL_CLAVE='test_only_app_password'

mysql_exec() {
    # env MYSQL_PWD corre DENTRO del contenedor (sandbox del exec)
    "${COMPOSE[@]}" exec -T db env MYSQL_PWD="$MYSQL_CLAVE" \
        mysql --protocol=TCP -h127.0.0.1 -urestaurante_test_app -e "$1" restaurante_test -N -B
}

esperar_bd() { # ms_totales
    local tope=$(( $(date +%s%3N) + $1 ))
    until mysql_exec "SELECT 1" >/dev/null 2>&1; do
        if [ "$(date +%s%3N)" -gt "$tope" ]; then
            echo "BD no respondió tras reinicio" >&2; return 1
        fi
        sleep 0.3
    done
}

corte_killo() { # corte eléctrico real: SIGKILL sin apagado ni flush
    "${COMPOSE[@]}" kill --signal=KILL db >/dev/null
}

# Escritor: cada INSERT autocommit con acuse persistido en el recibo (host).
escritor() {
    local marco="$1" recibido="$RECIBO" n=0
    : > "$recibido"
    while [ "$n" -lt 200 ]; do
        n=$((n + 1))
        if mysql_exec "INSERT INTO drill_corte_prueba (payload) VALUES ('${marco}-$n')" 2>/dev/null; then
            echo "$n" >> "$recibido"
        else
            break  # la BD murió (o se detuvo): no más acuse
        fi
    done
}

preparar_borrador() { # fanza transacción que nunca alcanzará COMMIT
    mysql_exec "CREATE TABLE IF NOT EXISTS drill_atomico (\
id BIGINT AUTO_INCREMENT PRIMARY KEY, marcador VARCHAR(64) NOT NULL) ENGINE=InnoDB" >/dev/null
    "${COMPOSE[@]}" exec -d db sh -lc \
        "MYSQL_PWD='$MYSQL_CLAVE' mysql --protocol=TCP -h127.0.0.1 -urestaurante_test_app \
         restaurante_test -e \"BEGIN; INSERT INTO drill_atomico (marcador) VALUES ('BORRADOR-$1'); \
         SELECT SLEEP(12); COMMIT;\""
}

fase_recuperacion_y_conciliacion() { # marco bortador
    local marco="$1" acuses
    echo "== $marco: reinicio del mismo contenedor (mismo volumen) =="
    docker start "$PROYECTO-db-1" >/dev/null
    esperar_bd 90000
    echo "== recuperación InnoDB en los logs del contenedor =="
    docker logs --since 5m "$PROYECTO-db-1" 2>&1 | grep -iE "crash recovery|Aplicando un lote de redo|Applying a batch of|recover" | tail -4 || true

    acuses=$(wc -l < "$RECIBO")
    echo "último acuse en el recibo: $(tail -1 "$RECIBO")"
    echo "acuses de COMMIT recibidos por el escritor: $acuses"

    local perdidas totales extras borradores
    perdidas=$(mysql_exec "SELECT COUNT(*) FROM drill_corte_prueba WHERE payload LIKE '${marco}-%'")
    totales=$(mysql_exec "SELECT COUNT(*) FROM drill_corte_prueba")
    extras=$((totales - perdidas))
    borradores=$(mysql_exec "SELECT COUNT(*) FROM drill_atomico WHERE marcador = 'BORRADOR-$marco'" 2>/dev/null || echo "?")
    echo "acuses de COMMIT del escritor $marco: $acuses; filas suyas en BD: $perdidas (filas de otros cortes: $extras); borradores sin COMMIT rezagados: $borradores"

    if [ "$perdidas" != "$acuses" ]; then
        echo "FALLO: $acuses COMMIT acuseados pero $perdidas filas suyas existen ⇒ se perdió un COMMIT" >&2
        return 1
    fi
    if [ "$borradores" != 0 ] 2>/dev/null; then
        echo "FALLO: la transacción sin COMMIT dejó residuos tras el corte" >&2
        return 1
    fi
    echo "CORTE_${marco}_OK durabilidad (0 perdidas) + atomicidad (0 residuos)"
}

fase_corte() {
    local marco="$1"
    preparar_borrador "$marco"
    sleep 0.6  # la fanza transacción ya lleva su INSERT sin COMMIT dentro
    ( escritor "$marco" ) &
    escritor_pid=$!
    # espera a que haya un mínimo de COMMIT acuseados antes del corte
    local tope=$(( $(date +%s%3N) + 45000 ))
    while [ "$(wc -l < "$RECIBO" 2>/dev/null || echo 0)" -lt 12 ]; do
        if [ "$(date +%s%3N)" -gt "$tope" ]; then
            echo "El escritor no acumuló acuses antes del corte" >&2; return 1
        fi
        sleep 0.2
    done
    echo "acuses al momento del corte $(wc -l < "$RECIBO")"
    corte_killo
    wait "$escritor_pid" || true
    fase_recuperacion_y_conciliacion "$marco"
}

echo "== Levantando MySQL efímero (proyecto $PROYECTO, puerto $PUERTO) =="
"${COMPOSE[@]}" up -d --wait
esperar_bd 120000

echo "== Cargando esquema del restaurant + datos base =="
./inicializar_bd_test.sh --datos >/dev/null
echo "BD_INICIALIZADA_OK"

echo "== Tabla de zona de pruebas =="
mysql_exec "CREATE TABLE IF NOT EXISTS drill_corte_prueba (\
id BIGINT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(64) NOT NULL, \
creado DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB" >/dev/null

echo "== Configuración de durabilidad (MySQL 8.4, valores por defecto) =="
mysql_exec "SHOW VARIABLES WHERE Variable_name IN \
('innodb_flush_log_at_trx_commit','sync_binlog','innodb_doublewrite','innodb_log_file_size','innodb_flush_method')"

echo "== Corte eléctrico #1: SIGKILL con escritor en curso y transacción sin COMMIT =="
fase_corte 1

echo "== Corte eléctrico #2 (repetido, mismo volumen) =="
fase_corte 2

echo "== Integridad del esquema tras 2 cortes =="
mysql_exec "CHECK TABLE pedidos, detalle_pedidos, usuarios, configuracion_sistema" | sed 's/\t/ /'
mysql_exec "SELECT (SELECT COUNT(*) FROM pedidos) AS pedidos, \
(SELECT COUNT(DISTINCT id_pedido) FROM detalle_pedidos) AS pedidos_con_lineas"

echo "DRILL_CORTE_ELECTRICO_OK"

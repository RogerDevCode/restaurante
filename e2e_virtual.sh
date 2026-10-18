#!/usr/bin/env bash
#
# e2e_virtual.sh — E2E en pantalla virtual con mouse/teclado independientes.
#
# Levanta Xvfb (:99, 1366x711), ejecuta el E2E y apaga todo al salir.
# El display :0 del operador no se toca: sin robo de foco ni de mouse.
#
# Uso:
#   ./e2e_virtual.sh                      # corre Vista.E2EVirtual (mesa 1, 1+2 pollos)
#   ./e2e_virtual.sh <clase-main> [args]  # ej: ./e2e_virtual.sh Vista.E2EVirtual
#
# Requiere: Xvfb, JDK 21, MySQL arriba (el E2E crea y borra 1 pedido real).
# El E2E exige -De2e.permitir=SI (lo pone este script).
#
set -euo pipefail
DISP=":99"
GEOM="1366x711x24"
cd "$(dirname "$0")"
RAIZ_PROYECTO="$(pwd)"

command -v Xvfb >/dev/null || { echo "Falta Xvfb (apt install xvfb)" >&2; exit 1; }
command -v xdotool >/dev/null || { echo "Falta xdotool" >&2; exit 1; }
command -v flock >/dev/null || { echo "Falta flock (util-linux)" >&2; exit 1; }

# Todo E2E que se lanza por este wrapper puede escribir datos. Solo se admite
# el MySQL de integración local; así un .env productivo no es un destino válido.
if [[ "${E2E_BD_DESECHABLE:-}" != "SI" \
      || "${DB_NAME:-}" != "restaurante_test" \
      || "${DB_USER:-}" != "restaurante_test_app" \
      || ! "${DB_URL:-}" =~ ^jdbc:mysql://(127\.0\.0\.1|localhost):3307/restaurante_test(\?.*)?$ ]]; then
    echo "E2E rechazado: requiere E2E_BD_DESECHABLE=SI y MySQL local restaurante_test en 127.0.0.1:3307." >&2
    exit 1
fi

LOCK_FILE="/tmp/restaurante-e2e-display-${DISP#:}.lock"
exec 9>"$LOCK_FILE"
flock -n 9 || { echo "Ya hay otro E2E usando DISPLAY=$DISP" >&2; exit 1; }

if DISPLAY="$DISP" xdotool getdisplaygeometry >/dev/null 2>&1; then
    echo "DISPLAY=$DISP ya está activo; no se tocará esa pantalla virtual." >&2
    exit 1
fi

XVFB_PID=""
limpiar() {
    if [[ -n "$XVFB_PID" ]] && kill -0 "$XVFB_PID" 2>/dev/null; then
        kill "$XVFB_PID" 2>/dev/null || true
        wait "$XVFB_PID" 2>/dev/null || true
    fi
}
trap limpiar EXIT

# Xvfb puede dejar archivos de socket/lock tras una terminación abrupta.
if [[ -f "/tmp/.X${DISP#:}-lock" ]]; then
    PID_STALE="$(tr -cd '0-9' < "/tmp/.X${DISP#:}-lock" 2>/dev/null || true)"
    if [[ -n "$PID_STALE" ]] && kill -0 "$PID_STALE" 2>/dev/null; then
        echo "El lock de DISPLAY=$DISP pertenece al proceso activo $PID_STALE." >&2
        exit 1
    fi
    rm -f "/tmp/.X${DISP#:}-lock" "/tmp/.X11-unix/X${DISP#:}"
fi

echo "== Compilando tests =="
ant -q compile-test

echo "== Xvfb $DISP ($GEOM) =="
mkdir -p /tmp/opencode
DISPLAY="$DISP" Xvfb "$DISP" -screen 0 "$GEOM" > /tmp/opencode/xvfb-e2e.log 2>&1 &
XVFB_PID=$!
for i in $(seq 1 20); do
    kill -0 "$XVFB_PID" 2>/dev/null || { echo "Xvfb terminó antes de iniciar" >&2; exit 1; }
    DISPLAY="$DISP" xdotool getdisplaygeometry >/dev/null 2>&1 && break
    sleep 0.5
done
DISPLAY="$DISP" xdotool getdisplaygeometry || { echo "Xvfb no respondió" >&2; exit 1; }

CLASE="${1:-Vista.E2EVirtual}"
shift || true
echo "== E2E: $CLASE (DISPLAY=$DISP) =="

JAVA_DB_ARGS=()
if [ -n "${DB_URL:-}" ]; then JAVA_DB_ARGS+=("-DDB_URL=$DB_URL"); fi
if [ -n "${DB_USER:-}" ]; then JAVA_DB_ARGS+=("-DDB_USER=$DB_USER"); fi
if [ -n "${DB_PASSWORD:-}" ]; then JAVA_DB_ARGS+=("-DDB_PASSWORD=$DB_PASSWORD"); fi
if [ -n "${DB_NAME:-}" ]; then JAVA_DB_ARGS+=("-DDB_NAME=$DB_NAME"); fi
if [ -n "${MYSQL_DATABASE:-}" ]; then JAVA_DB_ARGS+=("-DMYSQL_DATABASE=$MYSQL_DATABASE"); fi
if [ -n "${BACKUP_HMAC_KEY:-}" ]; then JAVA_DB_ARGS+=("-DBACKUP_HMAC_KEY=$BACKUP_HMAC_KEY"); fi

# Clases y librerías se resuelven desde el repo aunque el E2E use un cwd temporal.
CLASSPATH="$RAIZ_PROYECTO/build/classes:$RAIZ_PROYECTO/build/test/classes:$RAIZ_PROYECTO/librerias/*"
DIRECTORIO_EJECUCION="${E2E_WORKDIR:-$RAIZ_PROYECTO}"
mkdir -p "$DIRECTORIO_EJECUCION"
cd "$DIRECTORIO_EJECUCION"

# shellcheck disable=SC2086
DISPLAY="$DISP" java -De2e.permitir=SI ${JAVA_DB_ARGS[@]+"${JAVA_DB_ARGS[@]}"} -cp "$CLASSPATH" "$CLASE" "$@"

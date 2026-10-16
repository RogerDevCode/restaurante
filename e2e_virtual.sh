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

command -v Xvfb >/dev/null || { echo "Falta Xvfb (apt install xvfb)" >&2; exit 1; }

limpiar() {
    pkill -f "Xvfb $DISP" 2>/dev/null || true
    rm -f "/tmp/.X${DISP#:}-lock" "/tmp/.X11-unix/X${DISP#:}"
}
trap limpiar EXIT
limpiar
echo "== Compilando tests =="
ant -q compile-test

echo "== Xvfb $DISP ($GEOM) =="
mkdir -p /tmp/opencode
DISPLAY="$DISP" setsid nohup Xvfb "$DISP" -screen 0 "$GEOM" > /tmp/opencode/xvfb-e2e.log 2>&1 < /dev/null &
for i in $(seq 1 20); do
    DISPLAY="$DISP" xdotool getdisplaygeometry >/dev/null 2>&1 && break
    sleep 0.5
done
DISPLAY="$DISP" xdotool getdisplaygeometry || { echo "Xvfb no respondió" >&2; exit 1; }

CLASE="${1:-Vista.E2EVirtual}"
shift || true
echo "== E2E: $CLASE (DISPLAY=$DISP) =="
# shellcheck disable=SC2086
DISPLAY="$DISP" java -De2e.permitir=SI -cp "build/classes:build/test/classes:librerias/*" "$CLASE" $*

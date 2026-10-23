#!/usr/bin/env bash
#
# cargar_bd_test.sh — carga BD-test.sql en la BD de pruebas 3307 y la verifica.
#
# Casos de uso: QA/manual sobre un arnés E2E ya levantado (ej. tras
# e2e_virtual.sh o un contenedor compose e2e activo en 3307).
#
# Operaciones:
#   1. inicializar_bd_test.sh --datos  (recrear esquema + cargar BD-test.sql con FK activas)
#   2. verificar_bd_test.sh            (109 platos, mapeos sin huérfanos, config 40/16,
#                                       admin, salas, tablas operativas vacías)
#
# La BD queda lista para abrirla con la app (restaurante_test, 127.0.0.1:3307,
# usuario restaurante_test_app) o para ejecutar tests unitarios contra ella.
set -euo pipefail

cd "$(dirname "$0")"

./inicializar_bd_test.sh --datos
./verificar_bd_test.sh
echo "BD_TEST_CARGADA_OK: BD-test.sql cargado y verificado en 3307."

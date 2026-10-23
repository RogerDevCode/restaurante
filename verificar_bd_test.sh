#!/usr/bin/env bash
#
# verificar_bd_test.sh — valida la carga de BD-test.sql en la BD de pruebas 3307.
#
# Verifica (solo lectura; jamás sobre la BD de producción):
#   * 109 platos E2ESEED (108 combos 18x6 + base POLLO) con moneda/iva/precio coherentes.
#   * 109 mapeos plato_categoria↔platos sin huérfanos, con la categoría E2ESEED-CAT.
#   * config con tasa 40 / IVA 16; bootstrap_inicial_completado=1.
#   * admin id=1 (Administrador) activo con hash bcrypt; consumidor V-00000000.
#   * Sala Principal con 4 mesas (arnes unitario usa mesas 3/4); número de mesas en columna `mesas` de salas.
#   * Todas las tablas operativas vacías.
#
# Salida: BD_TEST_OK (exit 0) o error descriptivo (exit 1).
set -euo pipefail

cd "$(dirname "$0")"

CONTENEDOR="$(docker ps --format '{{.Names}}:{{.Ports}}' | awk 'index($0, "127.0.0.1:3307->") {sub(/:.*/, ""); print; exit}')"
if [[ -z "$CONTENEDOR" ]]; then
    echo "No hay contenedor E2E activo en 127.0.0.1:3307." >&2
    exit 1
fi

CONSULTA=$(cat <<'SQL'
SELECT 'platos_total', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-'
UNION ALL SELECT 'platos_base', COUNT(*) FROM `platos` WHERE `nombre` = 'E2ESEED-POLLO'
UNION ALL SELECT 'combo_usd_exe', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-USD-EXE-[0-9]{2}$'
UNION ALL SELECT 'combo_usd_iva', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-USD-IVA-[0-9]{2}$'
UNION ALL SELECT 'combo_usd_inc', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-USD-INC-[0-9]{2}$'
UNION ALL SELECT 'combo_ves_exe', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-VES-EXE-[0-9]{2}$'
UNION ALL SELECT 'combo_ves_iva', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-VES-IVA-[0-9]{2}$'
UNION ALL SELECT 'combo_ves_inc', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-VES-INC-[0-9]{2}$'
UNION ALL SELECT 'mapeos', COUNT(*) FROM `plato_categoria`
UNION ALL SELECT 'mapeos_huerfano_plato', COUNT(*) FROM `plato_categoria` m LEFT JOIN `platos` p ON p.`nombre_clave` = m.`nombre_clave` WHERE p.`id` IS NULL
UNION ALL SELECT 'mapeos_huerfano_categoria', COUNT(*) FROM `plato_categoria` m LEFT JOIN `categorias` c ON c.`id` = m.`id_categoria` WHERE c.`id` IS NULL
UNION ALL SELECT 'mapeos_categoria_mal', COUNT(*) FROM `plato_categoria` m LEFT JOIN `categorias` c ON c.`id` = m.`id_categoria` WHERE c.`id` IS NOT NULL AND lower(c.`nombre`) <> 'e2eseed-cat'
UNION ALL SELECT 'precio_incoherente', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-' AND NOT (((`moneda_base`='USD' AND `precio_usd` = `precio` AND `precio_ves` = ROUND(`precio`*40, 2)) OR (`moneda_base`='VES' AND `precio_ves` = `precio` AND `precio_usd` = ROUND(`precio`/40, 2))))
UNION ALL SELECT 'clasif_incoherente', COUNT(*) FROM `platos` WHERE `nombre` RLIKE '^E2ESEED-(USD|VES)-(EXE|IVA|INC)-[0-9]{2}$' AND NOT ((`nombre` RLIKE '^E2ESEED-(USD|VES)-EXE-[0-9]{2}$' AND `aplica_iva` = 0) OR (`nombre` RLIKE '^E2ESEED-(USD|VES)-IVA-[0-9]{2}$' AND `aplica_iva` = 1 AND COALESCE(`precio_incluye_iva`,0) = 0) OR (`nombre` RLIKE '^E2ESEED-(USD|VES)-INC-[0-9]{2}$' AND `aplica_iva` = 1 AND `precio_incluye_iva` = 1))
UNION ALL SELECT 'config_rows', COUNT(*) FROM `config`
UNION ALL SELECT 'config_params', COUNT(*) FROM `config` WHERE `tasa_dolar` = 40.0000 AND `iva_porcentaje` = 16.00 AND `cliente_predeterminado_documento` = 'V-00000000'
UNION ALL SELECT 'bootstrap_done', COUNT(*) FROM `configuracion_sistema` WHERE `clave` = 'bootstrap_inicial_completado' AND `valor` = '1'
UNION ALL SELECT 'usuarios_total', COUNT(*) FROM `usuarios`
UNION ALL SELECT 'admin_ok', COUNT(*) FROM `usuarios` WHERE `id` = 1 AND lower(`correo`) = 'admin' AND lower(`rol`) = 'administrador' AND `activo` = 1 AND CHAR_LENGTH(`pass`) >= 55
UNION ALL SELECT 'consumidor_final', COUNT(*) FROM `clientes` WHERE `documento` = 'V-00000000'
UNION ALL SELECT 'clientes_total', COUNT(*) FROM `clientes`
UNION ALL SELECT 'salas_total', COUNT(*) FROM `salas`
UNION ALL SELECT 'salas_principal', COUNT(*) FROM `salas` WHERE `nombre` = 'Sala Principal' AND `mesas` = 4
UNION ALL SELECT 'op_pedidos', COUNT(*) FROM `pedidos`
UNION ALL SELECT 'op_detalle', COUNT(*) FROM `detalle_pedidos`
UNION ALL SELECT 'op_pagos', COUNT(*) FROM `pedido_pagos`
UNION ALL SELECT 'op_movs', COUNT(*) FROM `pedido_saldos_movimientos`
UNION ALL SELECT 'op_liqui', COUNT(*) FROM `pedido_liquidaciones`
UNION ALL SELECT 'op_marchas', COUNT(*) FROM `comandas_marchas`
UNION ALL SELECT 'op_marchas_det', COUNT(*) FROM `comandas_marchas_detalle`
UNION ALL SELECT 'op_aud_ped', COUNT(*) FROM `auditoria_pedidos`
UNION ALL SELECT 'op_aud_precio', COUNT(*) FROM `auditoria_cambios_precio`
UNION ALL SELECT 'op_cierres', COUNT(*) FROM `cierres_caja`
UNION ALL SELECT 'op_logint', COUNT(*) FROM `login_intentos`
UNION ALL SELECT 'op_favoritos', COUNT(*) FROM `plato_favorito`
UNION ALL SELECT 'op_mesoneros', COUNT(*) FROM `mesoneros`
UNION ALL SELECT 'op_preferencias', COUNT(*) FROM `preferencias_tabla`
UNION ALL SELECT 'rest_control', COUNT(*) FROM `restaurante_control`
SQL
)

RESULTADOS="$(echo "$CONSULTA" | docker exec -i "$CONTENEDOR" sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N restaurante_test' 2>/dev/null)" || RESULTADOS=""

echo "$RESULTADOS" | python3 -c '
import sys

esperados = {
    "platos_total": 109,
    "platos_base": 1,
    "combo_usd_exe": 18, "combo_usd_iva": 18, "combo_usd_inc": 18,
    "combo_ves_exe": 18, "combo_ves_iva": 18, "combo_ves_inc": 18,
    "mapeos": 109,
    "mapeos_huerfano_plato": 0, "mapeos_huerfano_categoria": 0, "mapeos_categoria_mal": 0,
    "precio_incoherente": 0, "clasif_incoherente": 0,
    "config_rows": 1, "config_params": 1, "bootstrap_done": 1,
    "usuarios_total": 1, "admin_ok": 1,
    "consumidor_final": 1, "clientes_total": 1,
    "salas_total": 1, "salas_principal": 1,
    "op_pedidos": 0, "op_detalle": 0, "op_pagos": 0, "op_movs": 0,
    "op_liqui": 0, "op_marchas": 0, "op_marchas_det": 0,
    "op_aud_ped": 0, "op_aud_precio": 0, "op_cierres": 0,
    "op_logint": 0, "op_favoritos": 0, "op_mesoneros": 0, "op_preferencias": 0,
    "rest_control": 1,
}

recibidos = {}
for linea in sys.stdin:
    partes = linea.rstrip("\n").split("\t")
    if len(partes) == 2 and partes[0] and partes[0][0] != "-" and partes[1].lstrip("-").isdigit():
        recibidos[partes[0]] = int(partes[1])

if not recibidos:
    print("FAIL sin datos: ¿cargó ./cargar_bd_test.sh en un contenedor 3307?")
    sys.exit(1)

fallo = False
for clave, esperado in esperados.items():
    valor = recibidos.get(clave)
    if valor is None:
        print(f"FAIL {clave}: sin resultado del servidor MySQL")
        fallo = True
    elif valor != esperado:
        print(f"FAIL {clave}: esperado {esperado}, obtenido {valor}")
        fallo = True
    else:
        print(f"OK   {clave} = {valor}")

if fallo:
    sys.exit(1)
print("BD_TEST_OK")
'

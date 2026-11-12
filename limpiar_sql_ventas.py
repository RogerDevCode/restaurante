#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
limpiar_sql_ventas.py — Elimina ventas, pedidos, tickets, pagos y cierres de un archivo .sql
Preserva 100% de platos, categorias, salas, mesas, usuarios, clientes y configuracion.
Reinicia los contadores AUTO_INCREMENT de pedidos y detalle a 1.
"""
import sys
import os
import re

EMPTY_TABLES = {
    "pedidos",
    "detalle_pedidos",
    "pedido_pagos",
    "pedido_liquidaciones",
    "pedido_saldos_movimientos",
    "cierres_caja",
    "auditoria_pedidos",
    "auditoria_cambios_precio",
    "login_intentos",
    "plato_favorito",
    "comandas_marchas",
    "comandas_marchas_detalle",
    "ledger_fiscal",
    "ledger_fiscal_ancla",
}

def clean_sql_dump(input_file, output_file=None):
    if not os.path.isfile(input_file):
        print(f"[ERROR] No se encontro el archivo de entrada: {input_file}", file=sys.stderr)
        return False

    if not output_file:
        base, ext = os.path.splitext(input_file)
        output_file = f"{base}-sin-ventas{ext}"

    print(f"Leyendo: {input_file} ({os.path.getsize(input_file) / 1024:.1f} KB)...")
    with open(input_file, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    # 1. Reset AUTO_INCREMENT in CREATE TABLE for empty_tables
    def fix_create_table(match):
        table = match.group(1)
        body_and_opts = match.group(2)
        if table in EMPTY_TABLES:
            body_and_opts = re.sub(r"AUTO_INCREMENT=\d+", "AUTO_INCREMENT=1", body_and_opts)
        return f"CREATE TABLE `{table}` {body_and_opts}"

    content = re.sub(
        r"CREATE TABLE `([^`]+)`\s*(\(.*?\)\s*ENGINE=[^;]+;)",
        fix_create_table,
        content,
        flags=re.DOTALL
    )

    # 2. Process Data blocks
    def clean_data_block(match):
        table = match.group(1)
        data = match.group(2)
        if table in EMPTY_TABLES:
            return f"/*Data for the table `{table}` */\n\n"
        else:
            return f"/*Data for the table `{table}` */\n{data}"

    content = re.sub(
        r"/\*Data for the table `([^`]+)` \*/\s*(.*?)(?=(?:/\*Table structure for table|\Z))",
        clean_data_block,
        content,
        flags=re.DOTALL
    )

    with open(output_file, "w", encoding="utf-8") as f:
        f.write(content)

    in_size = os.path.getsize(input_file) / 1024
    out_size = os.path.getsize(output_file) / 1024
    print(f"[OK] Archivo generado exitosamente:")
    print(f"  -> Destino: {output_file}")
    print(f"  -> Tamano original: {in_size:.1f} KB")
    print(f"  -> Tamano depurado: {out_size:.1f} KB")
    print(f"  -> Tablas transaccionales vaciadas: {len(EMPTY_TABLES)}")
    print(f"  -> AUTO_INCREMENT de pedidos y tickets reiniciado a 1.")
    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        # Default fallback to a1.sql if present in cwd
        if os.path.isfile("a1.sql"):
            clean_sql_dump("a1.sql", "a1-sin-ventas.sql")
        else:
            print("Uso: python3 limpiar_sql_ventas.py <archivo_dump.sql> [archivo_salida.sql]")
            sys.exit(1)
    else:
        in_path = sys.argv[1]
        out_path = sys.argv[2] if len(sys.argv) > 2 else None
        ok = clean_sql_dump(in_path, out_path)
        sys.exit(0 if ok else 1)

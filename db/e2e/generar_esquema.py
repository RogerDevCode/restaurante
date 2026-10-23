#!/usr/bin/env python3
"""Genera db/e2e/001-esquema.sql: el esquema oficial de BD.sql sin datos.

Regla de derivación (debe coincidir EXACTAMENTE con
test/infraestructura/EsquemaE2ETest.java, que verifica la paridad):

1. Desde cada línea `LOCK TABLES ... WRITE;` hasta la siguiente
   `UNLOCK TABLES;` inclusive, se descartan todas las líneas
   (borra los INSERT y sus líneas de DISABLE/ENABLE KEYS).
2. Cualquier INSERT restante (multilinea) se descarta: desde una línea
   que comienza con `INSERT INTO` hasta la línea que termina en `;`.

Todo lo demas se copia byte a byte (DROP TABLE, CREATE TABLE, SETs de
cabecera/pie, comentarios). El resultado es cargable en MySQL 8.4 vacio
y queda a la par del esquema DDL de BD.sql (probar con este mismo script
o con la comparacion byte a byte del gate).

Uso:
    python3 db/e2e/generar_esquema.py            # genera/actualiza 001-esquema.sql
    python3 db/e2e/generar_esquema.py --verificar # solo verifica paridad, sin escribir
"""
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent.parent
FUENTE = RAIZ / "BD.sql"
DESTINO = RAIZ / "db" / "e2e" / "001-esquema.sql"


def derivar_esquema(texto: str) -> str:
    """Aplica las 2 reglas del contrato y devuelve el texto derivado."""
    lineas = texto.split("\n")
    salida = []
    en_bloque_lock = False
    en_insert = False
    for linea in lineas:
        if en_bloque_lock:
            if linea.strip() == "UNLOCK TABLES;":
                en_bloque_lock = False
            continue
        if en_insert:
            if linea.rstrip().endswith(";"):
                en_insert = False
            continue
        if linea.startswith("LOCK TABLES ") and linea.rstrip().endswith(";"):
            en_bloque_lock = True
            continue
        if linea.startswith("INSERT INTO"):
            if not linea.rstrip().endswith(";"):
                en_insert = True
            continue
        salida.append(linea)
    return "\n".join(salida)


def main(argv: list[str]) -> int:
    if not FUENTE.exists():
        print(f"No se encontro {FUENTE}", file=sys.stderr)
        return 1
    derivado = derivar_esquema(FUENTE.read_text(encoding="utf-8"))
    if "--verificar" in argv:
        actual = destino_actual()
        if actual == derivado:
            print(f"ESQUEMA_E2E_OK: {DESTINO.name} a la par de BD.sql")
            return 0
        print("ESQUEMA_E2E_DESVIADO: regenerar con python3 db/e2e/generar_esquema.py",
              file=sys.stderr)
        return 1
    DESTINO.parent.mkdir(parents=True, exist_ok=True)
    DESTINO.write_text(derivado, encoding="utf-8", newline="\n")
    print(f"ESQUEMA_E2E_OK: {DESTINO} generado ({len(derivado.encode('utf-8'))} bytes)")
    return 0


def destino_actual() -> str:
    if not DESTINO.exists():
        return ""
    return DESTINO.read_text(encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

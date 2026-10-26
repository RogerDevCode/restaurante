#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script inteligente para concatenar documentos PDF en un único archivo sermig.pdf.

Características:
1. Ordenación numérica inteligente basada en el primer dígito/número del nombre
   (ej. 1-carta.pdf, 2-cedula.pdf, 10-anexo.pdf se ordenan como 1, 2, ..., 10).
2. Normalización de tamaño de hoja inteligente:
   - Ajusta páginas heterogéneas (tickets, recibos, oficios, A4, etc.) al tamaño
     objetivo estándar (Carta por defecto, o A4/Oficio).
   - Mantiene la relación de aspecto original (sin deformar, estirar ni recortar texto).
   - Centra el contenido y aplica márgenes limpios y configurables.
   - Respeta y detecta la orientación original de cada página (vertical u horizontal).
3. Excluye automáticamente el archivo de salida (sermig.pdf) y archivos temporales.
"""

import sys
import os
import re
import argparse
from pathlib import Path

# Formatos de hoja estándar en puntos tipográficos (1 pulgada = 72 puntos, 1 mm = 72 / 25.4 pt)
PT_POR_MM = 72.0 / 25.4

FORMATOS_PAGINA = {
    "carta": {"ancho": 215.9 * PT_POR_MM, "alto": 279.4 * PT_POR_MM, "nombre": "Carta (Letter - 8.5 x 11 in)"},
    "letter": {"ancho": 215.9 * PT_POR_MM, "alto": 279.4 * PT_POR_MM, "nombre": "Carta (Letter - 8.5 x 11 in)"},
    "a4": {"ancho": 210.0 * PT_POR_MM, "alto": 297.0 * PT_POR_MM, "nombre": "A4 (210 x 297 mm)"},
    "oficio": {"ancho": 215.9 * PT_POR_MM, "alto": 355.6 * PT_POR_MM, "nombre": "Oficio (Legal - 8.5 x 14 in)"},
    "legal": {"ancho": 215.9 * PT_POR_MM, "alto": 355.6 * PT_POR_MM, "nombre": "Oficio (Legal - 8.5 x 14 in)"},
    "folio": {"ancho": 215.9 * PT_POR_MM, "alto": 330.2 * PT_POR_MM, "nombre": "Folio (8.5 x 13 in)"},
}

def verificar_dependencias():
    """Verifica e importa pypdf, guiando al usuario si no está instalado."""
    try:
        import pypdf
        return pypdf
    except ImportError:
        print("\n[ERROR] La biblioteca 'pypdf' no está instalada en el entorno de Python.")
        print("Para instalarla, ejecute el siguiente comando:")
        print("    pip install pypdf")
        print("o en distribuciones basadas en Debian/Ubuntu:")
        print("    sudo apt install python3-pypdf")
        sys.exit(1)

def extraer_clave_orden(ruta_archivo: Path):
    """
    Extrae la clave de ordenación según el número inicial en el nombre del archivo.
    Ejemplos:
      '1-carta.pdf' -> (0, 1, '1-carta.pdf')
      '2_recibo.pdf' -> (0, 2, '2_recibo.pdf')
      '10-anexo.pdf' -> (0, 10, '10-anexo.pdf')
      'sin_numero.pdf' -> (1, inf, 'sin_numero.pdf')
    """
    nombre = ruta_archivo.name.strip()
    match = re.match(r"^\s*(\d+)", nombre)
    if match:
        numero = int(match.group(1))
        return (0, numero, nombre.lower())
    else:
        return (1, float("inf"), nombre.lower())

def listar_archivos_pdf(directorio: Path, nombre_salida: str):
    """
    Encuentra todos los archivos .pdf en el directorio (excluyendo el archivo de salida
    y archivos temporales ocultos), ordenados numéricamente.
    """
    archivos = []
    salida_lower = nombre_salida.lower()

    for item in directorio.iterdir():
        if not item.is_file():
            continue
        nombre = item.name
        # Ignorar archivos temporales u ocultos
        if nombre.startswith(".") or nombre.startswith("~"):
            continue
        # Debe tener extensión .pdf
        if item.suffix.lower() != ".pdf":
            continue
        # Ignorar el propio archivo de salida
        if nombre.lower() == salida_lower:
            continue
        archivos.append(item)

    # Ordenar por el número inicial
    archivos.sort(key=extraer_clave_orden)
    return archivos

def procesar_pagina_inteligente(pagina, formato_base, margen_pt, forzar_vertical=False, sin_escalar=False):
    """
    Ajusta una página al tamaño de hoja objetivo de forma inteligente:
    - Normaliza la rotación visual al contenido.
    - Respeta o fuerza la orientación.
    - Escala proporcionalmente (fit/contain) sin deformar el contenido.
    - Centra en la hoja objetivo con margen de seguridad.
    """
    from pypdf import PageObject, Transformation

    if sin_escalar:
        return pagina

    # Normalizar rotación interna si existe (90, 180, 270)
    try:
        pagina.transfer_rotation_to_content()
    except Exception:
        pass

    ancho_orig = float(pagina.cropbox.width)
    alto_orig = float(pagina.cropbox.height)

    if ancho_orig <= 0 or alto_orig <= 0:
        return pagina

    es_horizontal = ancho_orig > alto_orig

    # Dimensiones de la hoja de salida
    base_w = formato_base["ancho"]
    base_h = formato_base["alto"]

    if forzar_vertical:
        # Siempre orientación vertical
        canvas_w = min(base_w, base_h)
        canvas_h = max(base_w, base_h)
    else:
        # Respetar orientación de la página
        if es_horizontal:
            canvas_w = max(base_w, base_h)
            canvas_h = min(base_w, base_h)
        else:
            canvas_w = min(base_w, base_h)
            canvas_h = max(base_w, base_h)

    # Área disponible respetando márgenes
    area_disp_w = max(10.0, canvas_w - (2 * margen_pt))
    area_disp_h = max(10.0, canvas_h - (2 * margen_pt))

    # Factor de escala uniforme (el menor entre ancho y alto para evitar recortes)
    factor_escala = min(area_disp_w / ancho_orig, area_disp_h / alto_orig)

    ancho_escalado = ancho_orig * factor_escala
    alto_escalado = alto_orig * factor_escala

    # Desplazamiento para centrar perfectamente en el canvas
    crop_x = float(pagina.cropbox.left)
    crop_y = float(pagina.cropbox.bottom)

    dx = margen_pt + ((area_disp_w - ancho_escalado) / 2.0) - (crop_x * factor_escala)
    dy = margen_pt + ((area_disp_h - alto_escalado) / 2.0) - (crop_y * factor_escala)

    # Crear hoja en blanco con el tamaño objetivo y estampar la página escalada
    canvas = PageObject.create_blank_page(width=canvas_w, height=canvas_h)
    transformacion = Transformation().scale(factor_escala, factor_escala).translate(dx, dy)
    canvas.merge_transformed_page(pagina, transformacion)

    return canvas

def concatenar_documentos(directorio_trabajo: Path, archivo_salida: str, formato_clave: str, margen_mm: float,
                          forzar_vertical: bool, sin_escalar: bool):
    """Ejecuta el flujo completo de detección, ordenación, ajuste y concatenación."""
    pypdf = verificar_dependencias()
    from pypdf import PdfReader, PdfWriter

    formato_info = FORMATOS_PAGINA.get(formato_clave.lower(), FORMATOS_PAGINA["carta"])
    margen_pt = max(0.0, margen_mm * PT_POR_MM)
    ruta_salida = directorio_trabajo / archivo_salida

    archivos = listar_archivos_pdf(directorio_trabajo, archivo_salida)

    print("=" * 76)
    print("      CONCATENADOR INTELIGENTE DE DOCUMENTOS PDF (SERMIG)")
    print("=" * 76)
    print(f" Directo de trabajo: {directorio_trabajo.resolve()}")
    print(f" Formato objetivo:   {formato_info['nombre']}")
    print(f" Margen de ajuste:   {margen_mm:.1f} mm ({margen_pt:.1f} pt)")
    print(f" Orientación:        {'Forzar a Vertical' if forzar_vertical else 'Automática (conserva horizontal/vertical)'}")
    print(f" Modo escalado:      {'Desactivado (tamaños originales)' if sin_escalar else 'Inteligente proporcional y centrado'}")
    print(f" Archivo de salida:  {archivo_salida}")
    print("-" * 76)

    if not archivos:
        print("[!] No se encontraron archivos PDF para concatenar en este directorio.")
        print("    Asegúrate de nombrar los archivos iniciando con un número correlativo, ej.:")
        print("      1-carta_presentacion.pdf")
        print("      2-documento_identidad.pdf")
        print("      3-anexos_legales.pdf")
        return False

    print(f"Se encontraron {len(archivos)} archivo(s) PDF para procesar:\n")
    for idx, arch in enumerate(archivos, 1):
        clave = extraer_clave_orden(arch)
        num_str = f"#{clave[1]}" if clave[0] == 0 else "S/N"
        print(f"  [{idx:02d}] {num_str:<5} -> {arch.name}")

    print("\nIniciando proceso de ensamblado y ajuste de páginas...")
    writer = PdfWriter()
    total_paginas = 0

    for idx, arch in enumerate(archivos, 1):
        print(f"\n-> [{idx}/{len(archivos)}] Procesando: {arch.name}")
        try:
            reader = PdfReader(str(arch))
            num_paginas_doc = len(reader.pages)
            print(f"   Leyendo {num_paginas_doc} página(s)...")

            for pag_num, pagina in enumerate(reader.pages, 1):
                ancho_p = float(pagina.cropbox.width) / PT_POR_MM
                alto_p = float(pagina.cropbox.height) / PT_POR_MM

                pag_procesada = procesar_pagina_inteligente(
                    pagina,
                    formato_info,
                    margen_pt,
                    forzar_vertical=forzar_vertical,
                    sin_escalar=sin_escalar
                )
                writer.add_page(pag_procesada)
                total_paginas += 1
                orientacion = "Horizontal" if ancho_p > alto_p else "Vertical"
                print(f"   • Pág {pag_num:02d}: Original {ancho_p:.1f}x{alto_p:.1f} mm ({orientacion}) -> Ajustada a {formato_info['nombre'].split()[0]}")

        except Exception as err:
            print(f"   [ERROR] Falló al procesar el archivo '{arch.name}': {err}")
            print("   Se continuará con los demás documentos.")

    if total_paginas == 0:
        print("\n[ERROR] No se pudo procesar ninguna página válida.")
        return False

    print(f"\nEscribiendo archivo consolidado: {ruta_salida}...")
    try:
        with open(ruta_salida, "wb") as f_out:
            writer.write(f_out)
        tamano_kb = ruta_salida.stat().st_size / 1024.0
        tamano_mb = tamano_kb / 1024.0
        print("=" * 76)
        print(" ¡CONCATENACIÓN COMPLETADA CON ÉXITO!")
        print(f"  Documento generado: {ruta_salida.name}")
        print(f"  Ruta absoluta:      {ruta_salida.resolve()}")
        print(f"  Documentos unidos:  {len(archivos)}")
        print(f"  Páginas totales:    {total_paginas}")
        print(f"  Tamaño final:       {tamano_kb:.1f} KB ({tamano_mb:.2f} MB)")
        print("=" * 76)
        return True
    except Exception as err:
        print(f"\n[ERROR] No se pudo guardar el archivo final: {err}")
        return False

def main():
    parser = argparse.ArgumentParser(
        description="Concatena y normaliza documentos PDF ordenados numéricamente en sermig.pdf."
    )
    parser.add_argument(
        "--dir", "-d",
        type=str,
        default=".",
        help="Directorio donde se encuentran los archivos PDF (por defecto: directorio actual)."
    )
    parser.add_argument(
        "--salida", "-s",
        type=str,
        default="sermig.pdf",
        help="Nombre del archivo PDF resultante (por defecto: sermig.pdf)."
    )
    parser.add_argument(
        "--formato", "-f",
        type=str,
        choices=["carta", "letter", "a4", "oficio", "legal", "folio"],
        default="carta",
        help="Tamaño de hoja objetivo al que se ajustarán las páginas (por defecto: carta)."
    )
    parser.add_argument(
        "--margen", "-m",
        type=float,
        default=5.0,
        help="Margen de seguridad en milímetros alrededor del contenido escalado (por defecto: 5.0 mm)."
    )
    parser.add_argument(
        "--forzar-vertical",
        action="store_true",
        help="Fuerza todas las páginas a orientación vertical (Portrait)."
    )
    parser.add_argument(
        "--sin-escalar",
        action="store_true",
        help="Une los PDFs manteniendo los tamaños y dimensiones originales de cada hoja sin escalar."
    )

    args = parser.parse_args()

    # Si se pasa '.' o no se especifica, tomar la carpeta donde reside el script o el cwd
    dir_path = Path(args.dir)
    if not dir_path.is_dir():
        print(f"[ERROR] El directorio '{args.dir}' no existe o no es válido.")
        sys.exit(1)

    exito = concatenar_documentos(
        directorio_trabajo=dir_path,
        archivo_salida=args.salida,
        formato_clave=args.formato,
        margen_mm=args.margen,
        forzar_vertical=args.forzar_vertical,
        sin_escalar=args.sin_escalar
    )

    sys.exit(0 if exito else 1)

if __name__ == "__main__":
    main()

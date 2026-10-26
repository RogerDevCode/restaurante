#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pruebas automáticas para el concatenador inteligente sermig.
"""

import unittest
import tempfile
import shutil
from pathlib import Path
from pypdf import PdfWriter, PdfReader, PageObject

# Importar funciones del script sermig
import concatenar_sermig as sermig

class TestSermigConcatenador(unittest.TestCase):

    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_sermig_"))

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def _crear_pdf_prueba(self, nombre, paginas_tamanios):
        """Crea un PDF sintético con páginas de dimensiones específicas (en pt)."""
        writer = PdfWriter()
        for w, h in paginas_tamanios:
            p = PageObject.create_blank_page(width=w, height=h)
            writer.add_page(p)
        ruta = self.temp_dir / nombre
        with open(ruta, "wb") as f:
            writer.write(f)
        return ruta

    def test_ordenamiento_natural_por_digito_inicial(self):
        """Verifica que 1, 2 y 10 se ordenen numéricamente y no alfabéticamente (1, 10, 2)."""
        self._crear_pdf_prueba("10-anexos.pdf", [(612, 792)])
        self._crear_pdf_prueba("1-carta.pdf", [(612, 792)])
        self._crear_pdf_prueba("2-cedula.pdf", [(612, 792)])
        self._crear_pdf_prueba("sermig.pdf", [(612, 792)]) # Debe ignorarse

        archivos = sermig.listar_archivos_pdf(self.temp_dir, "sermig.pdf")
        nombres = [a.name for a in archivos]

        self.assertEqual(len(nombres), 3)
        self.assertEqual(nombres, ["1-carta.pdf", "2-cedula.pdf", "10-anexos.pdf"])

    def test_concatenacion_y_ajuste_tamanos_heterogeneos(self):
        """Verifica que páginas heterogéneas (ticket, horizontal, legal) se normalicen a Carta."""
        # Doc 1: Ticket pequeño (300 x 500)
        self._crear_pdf_prueba("1-ticket.pdf", [(300, 500)])
        # Doc 2: Plano Horizontal (900 x 500)
        self._crear_pdf_prueba("2-plano.pdf", [(900, 500)])
        # Doc 3: Oficio / Legal Vertical (612 x 1008)
        self._crear_pdf_prueba("3-oficio.pdf", [(612, 1008)])

        salida = "sermig.pdf"
        exito = sermig.concatenar_documentos(
            directorio_trabajo=self.temp_dir,
            archivo_salida=salida,
            formato_clave="carta",
            margen_mm=5.0,
            forzar_vertical=False,
            sin_escalar=False
        )

        self.assertTrue(exito)
        ruta_salida = self.temp_dir / salida
        self.assertTrue(ruta_salida.exists())

        reader = PdfReader(str(ruta_salida))
        self.assertEqual(len(reader.pages), 3)

        # Página 1 (era ticket vertical) -> Carta Vertical (612 x 792)
        p1 = reader.pages[0]
        self.assertAlmostEqual(float(p1.mediabox.width), 612.0, delta=1.0)
        self.assertAlmostEqual(float(p1.mediabox.height), 792.0, delta=1.0)

        # Página 2 (era horizontal) -> Carta Horizontal (792 x 612)
        p2 = reader.pages[1]
        self.assertAlmostEqual(float(p2.mediabox.width), 792.0, delta=1.0)
        self.assertAlmostEqual(float(p2.mediabox.height), 612.0, delta=1.0)

        # Página 3 (era legal vertical) -> Carta Vertical (612 x 792)
        p3 = reader.pages[2]
        self.assertAlmostEqual(float(p3.mediabox.width), 612.0, delta=1.0)
        self.assertAlmostEqual(float(p3.mediabox.height), 792.0, delta=1.0)

    def test_forzar_vertical(self):
        """Verifica la opción para forzar todas las páginas a orientación vertical."""
        self._crear_pdf_prueba("1-horizontal.pdf", [(900, 500)])

        salida = "sermig.pdf"
        sermig.concatenar_documentos(
            directorio_trabajo=self.temp_dir,
            archivo_salida=salida,
            formato_clave="carta",
            margen_mm=5.0,
            forzar_vertical=True,
            sin_escalar=False
        )

        reader = PdfReader(str(self.temp_dir / salida))
        p = reader.pages[0]
        self.assertAlmostEqual(float(p.mediabox.width), 612.0, delta=1.0)
        self.assertAlmostEqual(float(p.mediabox.height), 792.0, delta=1.0)

    def test_re_ejecucion_no_auto_concatena_salida(self):
        """Verifica que una segunda ejecución no incluya sermig.pdf entre las entradas."""
        self._crear_pdf_prueba("1-doc.pdf", [(612, 792)])
        salida = "sermig.pdf"

        # Primera ejecución
        sermig.concatenar_documentos(self.temp_dir, salida, "carta", 5.0, False, False)
        reader1 = PdfReader(str(self.temp_dir / salida))
        self.assertEqual(len(reader1.pages), 1)

        # Segunda ejecución (sermig.pdf ya existe en el directorio)
        sermig.concatenar_documentos(self.temp_dir, salida, "carta", 5.0, False, False)
        reader2 = PdfReader(str(self.temp_dir / salida))
        self.assertEqual(len(reader2.pages), 1)

if __name__ == "__main__":
    unittest.main()

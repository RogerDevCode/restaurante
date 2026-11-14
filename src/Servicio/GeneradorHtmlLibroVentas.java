package Servicio;

import Modelo.Config;
import Modelo.ErrorAplicacionException;
import Modelo.FiltroLibroVentas;
import Modelo.RegistroLibroVenta;
import Modelo.ResumenFiscalVentas;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Genera el Libro de Ventas Fiscal oficial en formato HTML5 moderno, interactivo
 * y optimizado para impresión apaisada (Landscape) y auditoría contable.
 */
public final class GeneradorHtmlLibroVentas {

    private final Path directorioSalida;

    public GeneradorHtmlLibroVentas() {
        this(resolverDirectorioPorDefecto());
    }

    public GeneradorHtmlLibroVentas(Path directorioSalida) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida no puede ser nulo.");
        }
        this.directorioSalida = directorioSalida;
    }

    private static Path resolverDirectorioPorDefecto() {
        String override = System.getProperty("PDF_FACTURAS_DIR");
        if (override == null || override.trim().isEmpty()) {
            override = System.getenv("PDF_FACTURAS_DIR");
        }
        if (override != null && !override.trim().isEmpty()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home", "."), "Restaurante", "facturas");
    }

    public Path generar(List<RegistroLibroVenta> registros, ResumenFiscalVentas resumen, FiltroLibroVentas filtro, Config config, String usuarioEmisor) {
        if (registros == null) {
            throw ErrorAplicacionException.validacion("La lista de registros no puede ser nula.");
        }
        if (resumen == null) {
            resumen = ResumenFiscalVentas.desdeRegistros(registros);
        }

        try {
            Files.createDirectories(directorioSalida);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se pudo crear el directorio de salida: " + ex.getMessage(), ex);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreArchivo = String.format("libro_ventas_seniat_%d_%02d_%s.html", filtro.getAno(), filtro.getMes(), UUID.randomUUID().toString().substring(0, 8));
        Path archivoSalida = directorioSalida.resolve(nombreArchivo);

        String razonSocial = config != null && config.getNombre() != null ? config.getNombre() : "RESTAURANTE";
        String rif = config != null && config.getRuc() != null ? config.getRuc() : "J-00000000-0";
        String direccion = config != null && config.getDireccion() != null ? config.getDireccion() : "VENEZUELA";
        String fechaHoraImpresion = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        StringBuilder sb = new StringBuilder(64000);
        sb.append("""
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Libro de Ventas Fiscal SENIAT · %s</title>
              <style>
                @page {
                  size: letter landscape;
                  margin: 8mm 6mm;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
                  font-size: 8.5pt;
                  color: #0f172a;
                  background: #f8fafc;
                  padding: 16px;
                }
                .container {
                  max-width: 1480px;
                  margin: 0 auto;
                  background: #ffffff;
                  border: 1px solid #cbd5e1;
                  border-radius: 8px;
                  padding: 20px;
                  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
                }
                .btn-imprimir {
                  background: #0284c7;
                  color: #ffffff;
                  border: none;
                  padding: 8px 16px;
                  border-radius: 6px;
                  font-size: 9pt;
                  font-weight: bold;
                  cursor: pointer;
                  display: inline-flex;
                  align-items: center;
                  gap: 6px;
                  margin-bottom: 16px;
                }
                .btn-imprimir:hover { background: #0369a1; }
                @media print {
                  body { background: #ffffff; padding: 0; font-size: 7.5pt; }
                  .container { border: none; box-shadow: none; padding: 0; max-width: 100%%; }
                  .btn-imprimir { display: none; }
                }
                .header-fiscal {
                  border-bottom: 2px solid #0284c7;
                  padding-bottom: 12px;
                  margin-bottom: 14px;
                }
                .header-fiscal h1 {
                  font-size: 13pt;
                  color: #0f172a;
                  text-transform: uppercase;
                  margin-bottom: 4px;
                }
                .grid-header {
                  display: grid;
                  grid-template-columns: 2fr 1fr;
                  gap: 12px;
                }
                .info-empresa { font-size: 8.5pt; color: #334155; }
                .info-periodo {
                  text-align: right;
                  font-size: 8.5pt;
                  color: #334155;
                }
                .badge-periodo {
                  display: inline-block;
                  background: #e0f2fe;
                  color: #0369a1;
                  font-weight: bold;
                  padding: 2px 8px;
                  border-radius: 4px;
                  margin-top: 4px;
                }
                table.libro {
                  width: 100%%;
                  border-collapse: collapse;
                  font-size: 7.5pt;
                  margin-bottom: 16px;
                }
                table.libro th {
                  background: #1e293b;
                  color: #ffffff;
                  padding: 5px 4px;
                  border: 1px solid #334155;
                  text-align: center;
                  font-weight: bold;
                }
                table.libro td {
                  padding: 4px 4px;
                  border: 1px solid #cbd5e1;
                  color: #1e293b;
                  vertical-align: middle;
                }
                table.libro tr:nth-child(even) { background: #f8fafc; }
                table.libro tr.anulada { background: #fee2e2; color: #991b1b; }
                .num { text-align: right; font-family: 'Courier New', monospace; font-size: 7.5pt; }
                .center { text-align: center; }
                .badge-tag {
                  font-size: 6.5pt;
                  padding: 1px 4px;
                  border-radius: 3px;
                  font-weight: bold;
                }
                .tag-reg { background: #dbeafe; color: #1e40af; }
                .tag-anu { background: #fee2e2; color: #991b1b; }
                
                /* TABLA DE RESUMEN AL PIE */
                .resumen-fiscal {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                  gap: 16px;
                  margin-top: 14px;
                }
                table.resumen-tabla {
                  width: 100%%;
                  border-collapse: collapse;
                  font-size: 8pt;
                }
                table.resumen-tabla th {
                  background: #f1f5f9;
                  color: #0f172a;
                  padding: 6px 8px;
                  border: 1px solid #cbd5e1;
                  text-align: left;
                }
                table.resumen-tabla td {
                  padding: 5px 8px;
                  border: 1px solid #cbd5e1;
                }
                .pie-legal {
                  margin-top: 16px;
                  padding-top: 8px;
                  border-top: 1px solid #e2e8f0;
                  font-size: 7pt;
                  color: #64748b;
                  text-align: center;
                }
              </style>
            </head>
            <body>
            <div class="container">
              <button class="btn-imprimir" onclick="window.print()">🖨️ Imprimir / Guardar en PDF</button>

              <div class="header-fiscal">
                <h1>LIBRO DE VENTAS FISCAL (NORMATIVA SENIAT)</h1>
                <div class="grid-header">
                  <div class="info-empresa">
                    <strong>Razón Social:</strong> %s<br>
                    <strong>N° de R.I.F.:</strong> %s<br>
                    <strong>Domicilio Fiscal:</strong> %s
                  </div>
                  <div class="info-periodo">
                    <strong>PERÍODO FISCAL:</strong><br>
                    <span class="badge-periodo">%s</span><br>
                    <span style="font-size: 7pt; color: #64748b;">Emisión: %s · Operador: %s</span>
                  </div>
                </div>
              </div>

              <table class="libro">
                <thead>
                  <tr>
                    <th rowspan="2">Op</th>
                    <th rowspan="2">Fecha</th>
                    <th rowspan="2">R.I.F. / C.I.</th>
                    <th rowspan="2">Nombre o Razón Social</th>
                    <th rowspan="2">N° Factura</th>
                    <th rowspan="2">N° Control</th>
                    <th rowspan="2">N° N/C</th>
                    <th rowspan="2">Fact. Afectada</th>
                    <th rowspan="2">Tipo</th>
                    <th rowspan="2">Total Ventas Incl. IVA (Bs.)</th>
                    <th rowspan="2">Ventas Exentas (Bs.)</th>
                    <th colspan="3">Ventas Gravadas por Alícuota General (16%%)</th>
                    <th rowspan="2">IVA Retenido (Bs.)</th>
                    <th rowspan="2">IGTF 3%% (Bs.)</th>
                  </tr>
                  <tr>
                    <th>Base Imponible (Bs.)</th>
                    <th>%%</th>
                    <th>Impuesto IVA (Bs.)</th>
                  </tr>
                </thead>
                <tbody>
            """.formatted(
                filtro.getDescripcionPeriodo(),
                escapeHtml(razonSocial),
                escapeHtml(rif),
                escapeHtml(direccion),
                filtro.getDescripcionPeriodo(),
                fechaHoraImpresion,
                escapeHtml(usuarioEmisor != null ? usuarioEmisor : "Sistema")
        ));

        for (RegistroLibroVenta r : registros) {
            String claseFila = r.esAnulada() ? "class=\"anulada\"" : "";
            String badgeTipo = r.esAnulada()
                    ? "<span class=\"badge-tag tag-anu\">03-ANU</span>"
                    : "<span class=\"badge-tag tag-reg\">01-REG</span>";

            sb.append(String.format("""
                  <tr %s>
                    <td class="center">%d</td>
                    <td class="center">%s</td>
                    <td>%s</td>
                    <td>%s</td>
                    <td class="center">%s</td>
                    <td class="center">%s</td>
                    <td class="center">%s</td>
                    <td class="center">%s</td>
                    <td class="center">%s</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                    <td class="center">%s</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                  </tr>
                """,
                    claseFila,
                    r.getNumOperacion(),
                    r.getFechaOperacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    escapeHtml(r.getClienteDocumento()),
                    escapeHtml(r.getClienteNombre()),
                    escapeHtml(r.getNroFactura()),
                    escapeHtml(r.getNroControl()),
                    escapeHtml(r.getNroNotaCredito()),
                    escapeHtml(r.getFacturaAfectada()),
                    badgeTipo,
                    formatearBs(r.getTotalVentasBs()),
                    formatearBs(r.getVentasExentasBs()),
                    formatearBs(r.getBaseImponibleBs()),
                    r.getAlicuotaPorcentaje().setScale(0).toString() + "%",
                    formatearBs(r.getIvaBs()),
                    formatearBs(r.getIvaRetenidoBs()),
                    formatearBs(r.getIgtfPercibidoBs())
            ));
        }

        sb.append(String.format("""
                </tbody>
                <tfoot>
                  <tr style="font-weight: bold; background: #e2e8f0; border-top: 2px solid #0f172a;">
                    <td colspan="9" style="text-align: right; padding: 6px;">TOTALES GENERALES DEL PERÍODO (Bs.):</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                    <td class="center">16%%</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                    <td class="num">%s</td>
                  </tr>
                </tfoot>
              </table>

              <!-- RESUMEN TRIBUTARIO FORMA 30 SENIAT -->
              <div class="resumen-fiscal">
                <div>
                  <h3 style="font-size: 9.5pt; color: #0284c7; margin-bottom: 6px;">RESUMEN DECLARACIÓN MENSUAL DEL IVA (FORMA 30)</h3>
                  <table class="resumen-tabla">
                    <thead>
                      <tr><th>Concepto Fiscal</th><th style="text-align: right;">Base Imponible (Bs.)</th><th style="text-align: right;">Débito Fiscal (Bs.)</th></tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>Ventas Internas No Gravadas y/o Exentas (Renglón 20)</td>
                        <td class="num">%s</td>
                        <td class="num">-</td>
                      </tr>
                      <tr>
                        <td>Ventas Internas Gravadas por Alícuota General 16%% (Renglón 22 y 23)</td>
                        <td class="num">%s</td>
                        <td class="num">%s</td>
                      </tr>
                      <tr style="font-weight: bold; background: #f8fafc;">
                        <td>TOTAL VENTAS Y DÉBITOS FISCALES</td>
                        <td class="num">%s</td>
                        <td class="num">%s</td>
                      </tr>
                      <tr>
                        <td>Total IVA Retenido por Clientes en el Período</td>
                        <td class="num">-</td>
                        <td class="num">%s</td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <div>
                  <h3 style="font-size: 9.5pt; color: #0284c7; margin-bottom: 6px;">RESUMEN DECLARACIÓN DE IGTF Y ESTADÍSTICAS</h3>
                  <table class="resumen-tabla">
                    <thead>
                      <tr><th>Concepto / Métrica</th><th style="text-align: right;">Valor</th></tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>Total Operaciones Registradas en el Período</td>
                        <td class="num">%d</td>
                      </tr>
                      <tr>
                        <td>Total Facturas Emitidas Válidas</td>
                        <td class="num">%d</td>
                      </tr>
                      <tr>
                        <td>Total Facturas y Operaciones Anuladas</td>
                        <td class="num">%d</td>
                      </tr>
                      <tr style="font-weight: bold; background: #fdf4ff;">
                        <td>Total Percepción IGTF 3%% Cobrado en Divisas a Enterar (Bs.)</td>
                        <td class="num" style="color: #86198f;">%s</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div class="pie-legal">
                Documento tributario probatorio expedido conforme a la Ley del Impuesto al Valor Agregado, Artículos 70 y 72 del Reglamento de la Ley del IVA, Providencias Administrativas SNAT/2011/0071 y SNAT/2014/0032.
              </div>
            </div>
            </body>
            </html>
            """,
                formatearBs(resumen.totalVentasConIvaBs()),
                formatearBs(resumen.totalVentasExentasBs()),
                formatearBs(resumen.totalBaseImponibleGeneralBs()),
                formatearBs(resumen.totalDebitoFiscalGeneralBs()),
                formatearBs(resumen.totalIvaRetenidoBs()),
                formatearBs(resumen.totalIgtfPercibidoBs()),

                formatearBs(resumen.totalVentasExentasBs()),
                formatearBs(resumen.totalBaseImponibleGeneralBs()),
                formatearBs(resumen.totalDebitoFiscalGeneralBs()),
                formatearBs(resumen.totalBaseImponibleGeneralBs().add(resumen.totalVentasExentasBs())),
                formatearBs(resumen.totalDebitoFiscalGeneralBs()),
                formatearBs(resumen.totalIvaRetenidoBs()),

                resumen.totalOperaciones(),
                resumen.totalFacturasEmitidas(),
                resumen.totalFacturasAnuladas(),
                formatearBs(resumen.totalIgtfPercibidoBs())
        ));

        try {
            Files.writeString(archivoSalida, sb.toString(), StandardCharsets.UTF_8);
            return archivoSalida;
        } catch (IOException ex) {
            throw new ErrorAplicacionException("Error al guardar archivo HTML del libro de ventas: " + ex.getMessage(), ex);
        }
    }

    private static String formatearBs(BigDecimal monto) {
        if (monto == null) return "0,00";
        return String.format(java.util.Locale.GERMANY, "%,.2f", monto);
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

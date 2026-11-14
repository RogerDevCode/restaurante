package Servicio;

import Modelo.Config;
import Modelo.ErrorAplicacionException;
import Modelo.FiltroLibroVentas;
import Modelo.RegistroLibroVenta;
import Modelo.ResumenFiscalVentas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Genera el Libro de Ventas Fiscal en formato CSV estructurado (compatible directamente con Microsoft Excel)
 * para el procesamiento contable y fiscal de los auditores y contadores del restaurante.
 */
public final class GeneradorExcelLibroVentas {

    private final Path directorioSalida;

    public GeneradorExcelLibroVentas() {
        this(resolverDirectorioPorDefecto());
    }

    public GeneradorExcelLibroVentas(Path directorioSalida) {
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

    public Path generar(List<RegistroLibroVenta> registros, ResumenFiscalVentas resumen, FiltroLibroVentas filtro, Config config) {
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

        String nombreArchivo = String.format("libro_ventas_seniat_%d_%02d_%s.csv", filtro.getAno(), filtro.getMes(), UUID.randomUUID().toString().substring(0, 8));
        Path archivoSalida = directorioSalida.resolve(nombreArchivo);

        String razonSocial = config != null && config.getNombre() != null ? config.getNombre() : "RESTAURANTE";
        String rif = config != null && config.getRuc() != null ? config.getRuc() : "J-00000000-0";

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(archivoSalida), StandardCharsets.UTF_8))) {
            // Escribir Byte Order Mark (BOM) para compatibilidad nativa con Excel en español
            writer.write("\uFEFF");

            // Encabezado Legal
            writer.write("LIBRO DE VENTAS FISCAL (NORMATIVA SENIAT VENEZUELA)\n");
            writer.write("RAZÓN SOCIAL:;" + sanitizar(razonSocial) + ";RIF:;" + sanitizar(rif) + "\n");
            writer.write("PERÍODO:;" + sanitizar(filtro.getDescripcionPeriodo()) + ";FECHA EMISIÓN:;" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "\n\n");

            // Encabezados de Columna (22 Columnas SENIAT)
            writer.write("N° Operación;Fecha Factura;RIF / C.I.;Nombre o Razón Social;N° Factura;N° Control;N° Nota Débito;N° Nota Crédito;Factura Afectada;Tipo Transacción;Total Ventas Incl. IVA (Bs.);Ventas Exentas (Bs.);Base Imponible 16% (Bs.);% Alícuota;Impuesto IVA 16% (Bs.);Base Imponible Reducida (Bs.);IVA Reducido (Bs.);Base Imponible Adicional (Bs.);IVA Adicional (Bs.);IVA Retenido (Bs.);N° Comprobante Retención;IGTF 3% Percibido (Bs.)\n");

            // Renglones de transacciones
            for (RegistroLibroVenta r : registros) {
                writer.write(String.format(
                        "%d;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s\n",
                        r.getNumOperacion(),
                        r.getFechaOperacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        sanitizar(r.getClienteDocumento()),
                        sanitizar(r.getClienteNombre()),
                        sanitizar(r.getNroFactura()),
                        sanitizar(r.getNroControl()),
                        sanitizar(r.getNroNotaDebito()),
                        sanitizar(r.getNroNotaCredito()),
                        sanitizar(r.getFacturaAfectada()),
                        sanitizar(r.getTipoTransaccion()),
                        formatear(r.getTotalVentasBs()),
                        formatear(r.getVentasExentasBs()),
                        formatear(r.getBaseImponibleBs()),
                        r.getAlicuotaPorcentaje().toString(),
                        formatear(r.getIvaBs()),
                        formatear(r.getBaseImponibleReducidaBs()),
                        formatear(r.getIvaReducidoBs()),
                        formatear(r.getBaseImponibleAdicionalBs()),
                        formatear(r.getIvaAdicionalBs()),
                        formatear(r.getIvaRetenidoBs()),
                        sanitizar(r.getNroComprobanteRetencion()),
                        formatear(r.getIgtfPercibidoBs())
                ));
            }

            // Totales al pie
            writer.write("\nTOTALES DEL PERÍODO;;;;;;;;;"
                    + formatear(resumen.totalVentasConIvaBs()) + ";"
                    + formatear(resumen.totalVentasExentasBs()) + ";"
                    + formatear(resumen.totalBaseImponibleGeneralBs()) + ";;"
                    + formatear(resumen.totalDebitoFiscalGeneralBs()) + ";"
                    + formatear(resumen.totalBaseImponibleReducidaBs()) + ";"
                    + formatear(resumen.totalDebitoFiscalReducidoBs()) + ";"
                    + formatear(resumen.totalBaseImponibleAdicionalBs()) + ";"
                    + formatear(resumen.totalDebitoFiscalAdicionalBs()) + ";"
                    + formatear(resumen.totalIvaRetenidoBs()) + ";;"
                    + formatear(resumen.totalIgtfPercibidoBs()) + "\n\n");

            // Resumen Forma 30
            writer.write("RESUMEN PARA DECLARACIÓN FORMA 30 SENIAT:\n");
            writer.write("Total Ventas Internas No Gravadas / Exentas (Renglón 20):;" + formatear(resumen.totalVentasExentasBs()) + "\n");
            writer.write("Total Base Imponible General 16% (Renglón 22):;" + formatear(resumen.totalBaseImponibleGeneralBs()) + "\n");
            writer.write("Total Débito Fiscal General 16% (Renglón 23):;" + formatear(resumen.totalDebitoFiscalGeneralBs()) + "\n");
            writer.write("Total IVA Retenido por Clientes:;" + formatear(resumen.totalIvaRetenidoBs()) + "\n");
            writer.write("Total IGTF 3% Percibido en Divisas a Enterar:;" + formatear(resumen.totalIgtfPercibidoBs()) + "\n");

            return archivoSalida;
        } catch (IOException ex) {
            throw new ErrorAplicacionException("Error al exportar archivo CSV de Libro de Ventas: " + ex.getMessage(), ex);
        }
    }

    private static String formatear(BigDecimal valor) {
        if (valor == null) return "0.00";
        return valor.setScale(2).toString();
    }

    private static String sanitizar(String texto) {
        if (texto == null) return "";
        return texto.replace(";", " ").replace("\n", " ").trim();
    }
}

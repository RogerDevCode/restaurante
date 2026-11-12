package Servicio;

import Modelo.Config;
import Modelo.ErrorAplicacionException;
import Modelo.FiltroReporteAnulados;
import Modelo.TicketAnuladoReporte;

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
 * Genera el reporte de tickets anulados en formato HTML moderno, interactivo y listo para imprimir.
 */
public final class GeneradorHtmlReporteAnulados {

    private final Path directorioSalida;

    public GeneradorHtmlReporteAnulados() {
        this(resolverDirectorioPorDefecto());
    }

    public GeneradorHtmlReporteAnulados(Path directorioSalida) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida es obligatorio.");
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

    public Path generar(List<TicketAnuladoReporte> tickets, FiltroReporteAnulados filtro, Config config, String usuarioEmisor) {
        if (tickets == null) {
            throw ErrorAplicacionException.validacion("La lista de tickets anulados no puede ser nula.");
        }

        try {
            Files.createDirectories(directorioSalida);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se pudo crear el directorio de salida: " + ex.getMessage(), ex);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreArchivo = String.format("reporte_anulaciones_%s_%s.html", timestamp, UUID.randomUUID().toString().substring(0, 8));
        Path archivoSalida = directorioSalida.resolve(nombreArchivo);

        int totalTickets = tickets.size();
        BigDecimal totalUsd = BigDecimal.ZERO;
        BigDecimal totalBs = BigDecimal.ZERO;
        int cantFugas = 0;
        int cantFacturas = 0;

        for (TicketAnuladoReporte t : tickets) {
            totalUsd = totalUsd.add(t.getTotalUsd());
            totalBs = totalBs.add(t.getTotalBs());
            if (t.isEraFacturaCobrada()) cantFacturas++;
            else cantFugas++;
        }

        String nomEmpresa = (config != null && config.getNombre() != null && !config.getNombre().isBlank()) ? config.getNombre().trim() : "RESTAURANTE 2026";
        String rifEmpresa = (config != null && config.getRif() != null && !config.getRif().isBlank()) ? config.getRif().trim() : "RIF: J-00000000-0";
        String dirEmpresa = (config != null && config.getDireccion() != null && !config.getDireccion().isBlank()) ? config.getDireccion().trim() : "";
        String emision = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"es\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("<title>Reporte de Auditoría: Tickets Anulados — ").append(nomEmpresa).append("</title>\n")
          .append("<style>\n")
          .append("  :root { --color-primary: #1e293b; --color-danger: #dc2626; --color-border: #e2e8f0; }\n")
          .append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f8fafc; color: #1e293b; margin: 0; padding: 24px; }\n")
          .append("  .container { max-width: 1200px; margin: 0 auto; background: #fff; border: 1px solid var(--color-border); border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); padding: 28px; }\n")
          .append("  .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid var(--color-danger); padding-bottom: 16px; margin-bottom: 20px; }\n")
          .append("  .title { font-size: 20px; font-weight: 800; color: var(--color-danger); margin: 0 0 4px 0; text-transform: uppercase; }\n")
          .append("  .subtitle { font-size: 12px; color: #64748b; margin: 0; }\n")
          .append("  .company-name { font-size: 18px; font-weight: 700; color: #0f172a; margin: 0 0 4px 0; }\n")
          .append("  .company-info { font-size: 12px; color: #475569; margin: 0; }\n")
          .append("  .filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; background: #f1f5f9; padding: 12px 16px; border-radius: 8px; margin-bottom: 20px; font-size: 13px; }\n")
          .append("  .filter-item strong { color: #334155; }\n")
          .append("  .kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 24px; }\n")
          .append("  .kpi-card { background: #fef2f2; border: 1px solid #fecaca; border-radius: 8px; padding: 14px; text-align: center; }\n")
          .append("  .kpi-label { font-size: 11px; font-weight: 700; color: #991b1b; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 4px; }\n")
          .append("  .kpi-num { font-size: 20px; font-weight: 800; color: #dc2626; }\n")
          .append("  table { width: 100%; border-collapse: collapse; font-size: 12px; text-align: left; margin-bottom: 20px; }\n")
          .append("  th { background: #1e293b; color: #fff; padding: 10px 12px; font-weight: 600; text-transform: uppercase; font-size: 11px; }\n")
          .append("  td { padding: 9px 12px; border-bottom: 1px solid var(--color-border); color: #334155; }\n")
          .append("  tr:nth-child(even) td { background: #f8fafc; }\n")
          .append("  .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 10px; font-weight: 700; }\n")
          .append("  .badge-danger { background: #fee2e2; color: #b91c1c; }\n")
          .append("  .badge-success { background: #dcfce7; color: #15803d; }\n")
          .append("  .actions { display: flex; justify-content: flex-end; gap: 10px; margin-bottom: 16px; }\n")
          .append("  .btn { background: #0284c7; color: #fff; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; gap: 6px; }\n")
          .append("  .btn:hover { background: #0369a1; }\n")
          .append("  .btn-print { background: #1e293b; }\n")
          .append("  .btn-print:hover { background: #0f172a; }\n")
          .append("  @media print {\n")
          .append("    body { background: #fff; padding: 0; }\n")
          .append("    .container { border: none; box-shadow: none; padding: 0; max-width: 100%; }\n")
          .append("    .actions { display: none; }\n")
          .append("  }\n")
          .append("</style>\n</head>\n<body>\n")
          .append("<div class=\"container\">\n")
          .append("  <div class=\"actions\">\n")
          .append("    <button class=\"btn btn-print\" onclick=\"window.print()\">🖨️ Imprimir Reporte</button>\n")
          .append("  </div>\n")
          .append("  <div class=\"header\">\n")
          .append("    <div>\n")
          .append("      <h1 class=\"company-name\">").append(nomEmpresa).append("</h1>\n")
          .append("      <p class=\"company-info\">").append(rifEmpresa).append(" ").append(dirEmpresa).append("</p>\n")
          .append("    </div>\n")
          .append("    <div style=\"text-align: right;\">\n")
          .append("      <h2 class=\"title\">Auditoría de Tickets Anulados</h2>\n")
          .append("      <p class=\"subtitle\">Emitido: ").append(emision).append(" | Operador: ").append(usuarioEmisor != null ? usuarioEmisor : "Sistema").append("</p>\n")
          .append("    </div>\n")
          .append("  </div>\n");

        // Filtros
        sb.append("  <div class=\"filters\">\n")
          .append("    <div class=\"filter-item\"><strong>Período:</strong> ").append(filtro != null ? filtro.getDescripcionRangoFechas() : "Histórico").append("</div>\n")
          .append("    <div class=\"filter-item\"><strong>Mesa:</strong> ").append(filtro != null && filtro.getNumMesa() != null ? "Mesa " + filtro.getNumMesa() : "Todas").append("</div>\n")
          .append("    <div class=\"filter-item\"><strong>Usuario:</strong> ").append(filtro != null && filtro.getUsuario() != null && !filtro.getUsuario().isBlank() ? filtro.getUsuario() : "Todos").append("</div>\n")
          .append("    <div class=\"filter-item\"><strong>Tipo:</strong> ").append(filtro != null && filtro.getTipo() != null ? filtro.getTipo().getDescripcion() : "Todas").append("</div>\n")
          .append("  </div>\n");

        // KPIs
        sb.append("  <div class=\"kpi-grid\">\n")
          .append("    <div class=\"kpi-card\"><div class=\"kpi-label\">Total Anulados</div><div class=\"kpi-num\">").append(totalTickets).append("</div></div>\n")
          .append("    <div class=\"kpi-card\"><div class=\"kpi-label\">Monto Total (USD)</div><div class=\"kpi-num\">$ ").append(String.format(java.util.Locale.US, "%.2f", totalUsd)).append("</div></div>\n")
          .append("    <div class=\"kpi-card\"><div class=\"kpi-label\">Monto Total (VES)</div><div class=\"kpi-num\">Bs. ").append(String.format(java.util.Locale.US, "%.2f", totalBs)).append("</div></div>\n")
          .append("    <div class=\"kpi-card\"><div class=\"kpi-label\">Desglose</div><div class=\"kpi-num\" style=\"font-size:15px;\">").append(cantFugas).append(" Fugas / ").append(cantFacturas).append(" Facturas</div></div>\n")
          .append("  </div>\n");

        // Tabla
        sb.append("  <table>\n")
          .append("    <thead>\n")
          .append("      <tr>\n")
          .append("        <th>Ticket #</th>\n")
          .append("        <th>Fecha Anulación</th>\n")
          .append("        <th>Ubicación</th>\n")
          .append("        <th>Atendido Por</th>\n")
          .append("        <th>Anulado Por</th>\n")
          .append("        <th>Motivo de Anulación</th>\n")
          .append("        <th style=\"text-align:right;\">Total USD</th>\n")
          .append("        <th style=\"text-align:right;\">Total Bs</th>\n")
          .append("        <th style=\"text-align:center;\">Tipo</th>\n")
          .append("      </tr>\n")
          .append("    </thead>\n")
          .append("    <tbody>\n");

        if (tickets.isEmpty()) {
            sb.append("      <tr><td colspan=\"9\" style=\"text-align:center; padding: 24px; color: #94a3b8;\">No se encontraron tickets anulados para los filtros seleccionados.</td></tr>\n");
        } else {
            for (TicketAnuladoReporte t : tickets) {
                sb.append("      <tr>\n")
                  .append("        <td><strong>#").append(String.format(java.util.Locale.US, "%06d", t.getIdPedido())).append("</strong></td>\n")
                  .append("        <td>").append(t.getFechaAnulacionFormateada()).append("</td>\n")
                  .append("        <td>").append(t.getUbicacionFormateada()).append("</td>\n")
                  .append("        <td>").append(t.getAtendidoPor()).append("</td>\n")
                  .append("        <td><strong>").append(t.getAnuladoPor()).append("</strong></td>\n")
                  .append("        <td>").append(t.getMotivo()).append("</td>\n")
                  .append("        <td style=\"text-align:right; font-weight:700;\">$ ").append(String.format(java.util.Locale.US, "%.2f", t.getTotalUsd())).append("</td>\n")
                  .append("        <td style=\"text-align:right;\">Bs. ").append(String.format(java.util.Locale.US, "%.2f", t.getTotalBs())).append("</td>\n")
                  .append("        <td style=\"text-align:center;\"><span class=\"badge ").append(t.isEraFacturaCobrada() ? "badge-success" : "badge-danger").append("\">")
                  .append(t.isEraFacturaCobrada() ? "Factura" : "Fuga/Mesa").append("</span></td>\n")
                  .append("      </tr>\n");
            }
        }

        sb.append("    </tbody>\n  </table>\n")
          .append("  <p style=\"font-size: 11px; color: #94a3b8; text-align: center; margin-top: 24px;\">Documento confidencial emitido exclusivamente para fines de control administrativo y auditoría fiscal interna — Sistema Restaurante 2026</p>\n")
          .append("</div>\n</body>\n</html>");

        try {
            Files.writeString(archivoSalida, sb.toString(), StandardCharsets.UTF_8);
            return archivoSalida;
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se pudo escribir el archivo HTML del reporte: " + ex.getMessage(), ex);
        }
    }
}

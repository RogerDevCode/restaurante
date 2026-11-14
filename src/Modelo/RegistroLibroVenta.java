package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa un renglón individual del Libro de Ventas Fiscal
 * conforme a las 22 columnas reglamentarias de la normativa del SENIAT (Venezuela).
 * Todos los montos se expresan de manera inmutable en Bolívares (VES) con escala a 2 decimales.
 */
public final class RegistroLibroVenta {

    private final int numOperacion;
    private final LocalDate fechaOperacion;
    private final String clienteDocumento;
    private final String clienteNombre;
    private final String nroFactura;
    private final String nroControl;
    private final String nroNotaDebito;
    private final String nroNotaCredito;
    private final String facturaAfectada;
    private final String tipoTransaccion; // 01-REG, 02-COM, 03-ANU
    private final BigDecimal totalVentasBs;
    private final BigDecimal ventasExentasBs;
    private final BigDecimal baseImponibleBs;
    private final BigDecimal alicuotaPorcentaje;
    private final BigDecimal ivaBs;
    private final BigDecimal baseImponibleReducidaBs;
    private final BigDecimal alicuotaReducidaPorcentaje;
    private final BigDecimal ivaReducidoBs;
    private final BigDecimal baseImponibleAdicionalBs;
    private final BigDecimal alicuotaAdicionalPorcentaje;
    private final BigDecimal ivaAdicionalBs;
    private final BigDecimal ivaRetenidoBs;
    private final String nroComprobanteRetencion;
    private final BigDecimal igtfPercibidoBs;

    public RegistroLibroVenta(
            int numOperacion,
            LocalDate fechaOperacion,
            String clienteDocumento,
            String clienteNombre,
            String nroFactura,
            String nroControl,
            String nroNotaDebito,
            String nroNotaCredito,
            String facturaAfectada,
            String tipoTransaccion,
            BigDecimal totalVentasBs,
            BigDecimal ventasExentasBs,
            BigDecimal baseImponibleBs,
            BigDecimal alicuotaPorcentaje,
            BigDecimal ivaBs,
            BigDecimal baseImponibleReducidaBs,
            BigDecimal alicuotaReducidaPorcentaje,
            BigDecimal ivaReducidoBs,
            BigDecimal baseImponibleAdicionalBs,
            BigDecimal alicuotaAdicionalPorcentaje,
            BigDecimal ivaAdicionalBs,
            BigDecimal ivaRetenidoBs,
            String nroComprobanteRetencion,
            BigDecimal igtfPercibidoBs) {

        this.numOperacion = numOperacion;
        this.fechaOperacion = Objects.requireNonNull(fechaOperacion, "La fecha de la operación no puede ser nula.");
        this.clienteDocumento = normalizarTexto(clienteDocumento, "V-00000000");
        this.clienteNombre = normalizarTexto(clienteNombre, "Consumidor Final");
        this.nroFactura = normalizarTexto(nroFactura, "00000000");
        this.nroControl = normalizarTexto(nroControl, "");
        this.nroNotaDebito = normalizarTexto(nroNotaDebito, "");
        this.nroNotaCredito = normalizarTexto(nroNotaCredito, "");
        this.facturaAfectada = normalizarTexto(facturaAfectada, "");
        this.tipoTransaccion = normalizarTexto(tipoTransaccion, "01-REG");

        this.totalVentasBs = normalizarMonto(totalVentasBs);
        this.ventasExentasBs = normalizarMonto(ventasExentasBs);
        this.baseImponibleBs = normalizarMonto(baseImponibleBs);
        this.alicuotaPorcentaje = normalizarMonto(alicuotaPorcentaje != null ? alicuotaPorcentaje : new BigDecimal("16.00"));
        this.ivaBs = normalizarMonto(ivaBs);

        this.baseImponibleReducidaBs = normalizarMonto(baseImponibleReducidaBs);
        this.alicuotaReducidaPorcentaje = normalizarMonto(alicuotaReducidaPorcentaje);
        this.ivaReducidoBs = normalizarMonto(ivaReducidoBs);

        this.baseImponibleAdicionalBs = normalizarMonto(baseImponibleAdicionalBs);
        this.alicuotaAdicionalPorcentaje = normalizarMonto(alicuotaAdicionalPorcentaje);
        this.ivaAdicionalBs = normalizarMonto(ivaAdicionalBs);

        this.ivaRetenidoBs = normalizarMonto(ivaRetenidoBs);
        this.nroComprobanteRetencion = normalizarTexto(nroComprobanteRetencion, "");
        this.igtfPercibidoBs = normalizarMonto(igtfPercibidoBs);
    }

    private static String normalizarTexto(String valor, String defecto) {
        if (valor == null || valor.trim().isEmpty()) {
            return defecto;
        }
        return valor.trim();
    }

    private static BigDecimal normalizarMonto(BigDecimal monto) {
        if (monto == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    public int getNumOperacion() { return numOperacion; }
    public LocalDate getFechaOperacion() { return fechaOperacion; }
    public String getClienteDocumento() { return clienteDocumento; }
    public String getClienteNombre() { return clienteNombre; }
    public String getNroFactura() { return nroFactura; }
    public String getNroControl() { return nroControl; }
    public String getNroNotaDebito() { return nroNotaDebito; }
    public String getNroNotaCredito() { return nroNotaCredito; }
    public String getFacturaAfectada() { return facturaAfectada; }
    public String getTipoTransaccion() { return tipoTransaccion; }
    public BigDecimal getTotalVentasBs() { return totalVentasBs; }
    public BigDecimal getVentasExentasBs() { return ventasExentasBs; }
    public BigDecimal getBaseImponibleBs() { return baseImponibleBs; }
    public BigDecimal getAlicuotaPorcentaje() { return alicuotaPorcentaje; }
    public BigDecimal getIvaBs() { return ivaBs; }
    public BigDecimal getBaseImponibleReducidaBs() { return baseImponibleReducidaBs; }
    public BigDecimal getAlicuotaReducidaPorcentaje() { return alicuotaReducidaPorcentaje; }
    public BigDecimal getIvaReducidoBs() { return ivaReducidoBs; }
    public BigDecimal getBaseImponibleAdicionalBs() { return baseImponibleAdicionalBs; }
    public BigDecimal getAlicuotaAdicionalPorcentaje() { return alicuotaAdicionalPorcentaje; }
    public BigDecimal getIvaAdicionalBs() { return ivaAdicionalBs; }
    public BigDecimal getIvaRetenidoBs() { return ivaRetenidoBs; }
    public String getNroComprobanteRetencion() { return nroComprobanteRetencion; }
    public BigDecimal getIgtfPercibidoBs() { return igtfPercibidoBs; }

    public boolean esAnulada() {
        return "03-ANU".equalsIgnoreCase(tipoTransaccion) || !nroNotaCredito.isEmpty();
    }
}

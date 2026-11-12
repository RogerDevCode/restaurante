package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

/**
 * DTO para la representación detallada de un ticket o factura anulada
 * en los reportes operativos y fiscales de auditoría.
 */
public class TicketAnuladoReporte {

    private final int idPedido;
    private final String sala;
    private final int numMesa;
    private final String tipoPedido;
    private final String atendidoPor;
    private final String fechaEmision;
    private final Timestamp fechaAnulacion;
    private final BigDecimal totalUsd;
    private final BigDecimal totalBs;
    private final BigDecimal tasaCambio;
    private final String clienteNombre;
    private final String clienteDocumento;
    private final String anuladoPor;
    private final String motivo;
    private final String accionAuditoria;
    private final boolean eraFacturaCobrada;

    public TicketAnuladoReporte(int idPedido, String sala, int numMesa, String tipoPedido,
                                String atendidoPor, String fechaEmision, Timestamp fechaAnulacion,
                                BigDecimal totalUsd, BigDecimal totalBs, BigDecimal tasaCambio,
                                String clienteNombre, String clienteDocumento, String anuladoPor,
                                String motivo, String accionAuditoria, boolean eraFacturaCobrada) {
        this.idPedido = idPedido;
        this.sala = (sala != null && !sala.isBlank()) ? sala.trim() : "General";
        this.numMesa = numMesa;
        this.tipoPedido = (tipoPedido != null && !tipoPedido.isBlank()) ? tipoPedido.trim().toUpperCase() : "MESA";
        this.atendidoPor = (atendidoPor != null && !atendidoPor.isBlank()) ? atendidoPor.trim() : "N/D";
        this.fechaEmision = fechaEmision != null ? fechaEmision : "—";
        this.fechaAnulacion = fechaAnulacion != null ? fechaAnulacion : new Timestamp(System.currentTimeMillis());
        this.totalUsd = totalUsd != null ? totalUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        this.totalBs = totalBs != null ? totalBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        this.tasaCambio = tasaCambio != null ? tasaCambio.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
        this.clienteNombre = (clienteNombre != null && !clienteNombre.isBlank()) ? clienteNombre.trim() : "Consumidor Final";
        this.clienteDocumento = (clienteDocumento != null && !clienteDocumento.isBlank()) ? clienteDocumento.trim() : "V-00000000";
        this.anuladoPor = (anuladoPor != null && !anuladoPor.isBlank()) ? anuladoPor.trim() : "Sistema";
        this.motivo = (motivo != null && !motivo.isBlank()) ? motivo.trim() : "Sin justificación registrada";
        this.accionAuditoria = (accionAuditoria != null && !accionAuditoria.isBlank()) ? accionAuditoria.trim() : "ANULACION";
        this.eraFacturaCobrada = eraFacturaCobrada;
    }

    public int getIdPedido() {
        return idPedido;
    }

    public String getSala() {
        return sala;
    }

    public int getNumMesa() {
        return numMesa;
    }

    public String getTipoPedido() {
        return tipoPedido;
    }

    public boolean esPedidoRapido() {
        return "RAPIDO".equalsIgnoreCase(tipoPedido) || numMesa <= 0;
    }

    public String getUbicacionFormateada() {
        if (esPedidoRapido()) {
            return "Pedido Rápido (Barra)";
        }
        return sala + " — Mesa " + numMesa;
    }

    public String getAtendidoPor() {
        return atendidoPor;
    }

    public String getFechaEmision() {
        return fechaEmision;
    }

    public Timestamp getFechaAnulacion() {
        return fechaAnulacion;
    }

    public String getFechaAnulacionFormateada() {
        if (fechaAnulacion == null) return "—";
        return fechaAnulacion.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public BigDecimal getTotalUsd() {
        return totalUsd;
    }

    public BigDecimal getTotalBs() {
        return totalBs;
    }

    public BigDecimal getTasaCambio() {
        return tasaCambio;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteDocumento() {
        return clienteDocumento;
    }

    public String getAnuladoPor() {
        return anuladoPor;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getAccionAuditoria() {
        return accionAuditoria;
    }

    public boolean isEraFacturaCobrada() {
        return eraFacturaCobrada;
    }

    public String getTipoAnulacionEtiqueta() {
        return eraFacturaCobrada ? "Factura Cobrada (Reversión)" : "Cuenta Pendiente (Fuga/Abandono)";
    }
}

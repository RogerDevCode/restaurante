package Modelo;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Modelo para la auditoría de movimientos de saldo (CREDITO, DEVOLUCION, ANULACION)
 * sobre comandas cerradas o saldos remanentes (Task 7).
 */
public class MovimientoSaldoPedido {
    private long id;
    private int idPedido;
    private Long idLiquidacion;
    private String tipo; // 'CREDITO', 'DEVOLUCION', 'ANULACION'
    private String moneda; // 'USD', 'VES'
    private BigDecimal montoOrigen;
    private BigDecimal equivalenteVes;
    private BigDecimal tasaCambio;
    private String actor;
    private String motivo;
    private String idempotencyKey;
    private Timestamp creadoEn;

    public MovimientoSaldoPedido() {
    }

    public MovimientoSaldoPedido(int idPedido, Long idLiquidacion, String tipo, String moneda,
                                 BigDecimal montoOrigen, BigDecimal equivalenteVes, BigDecimal tasaCambio,
                                 String actor, String motivo, String idempotencyKey) {
        this.idPedido = idPedido;
        this.idLiquidacion = idLiquidacion;
        this.tipo = tipo;
        this.moneda = moneda;
        this.montoOrigen = montoOrigen;
        this.equivalenteVes = equivalenteVes;
        this.tasaCambio = tasaCambio;
        this.actor = actor;
        this.motivo = motivo;
        this.idempotencyKey = idempotencyKey;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public Long getIdLiquidacion() { return idLiquidacion; }
    public void setIdLiquidacion(Long idLiquidacion) { this.idLiquidacion = idLiquidacion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public BigDecimal getMontoOrigen() { return montoOrigen; }
    public void setMontoOrigen(BigDecimal montoOrigen) { this.montoOrigen = montoOrigen; }

    public BigDecimal getEquivalenteVes() { return equivalenteVes; }
    public void setEquivalenteVes(BigDecimal equivalenteVes) { this.equivalenteVes = equivalenteVes; }

    public BigDecimal getTasaCambio() { return tasaCambio; }
    public void setTasaCambio(BigDecimal tasaCambio) { this.tasaCambio = tasaCambio; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Timestamp getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Timestamp creadoEn) { this.creadoEn = creadoEn; }
}

package Modelo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa un registro de auditoría de un pedido (anulaciones, reimpresiones, etc.).
 * Permite mantener trazabilidad sobre quién realizó la acción, por qué motivo y cuándo.
 */
public class AuditoriaPedido {

    private int id;
    private int idPedido;
    private String accion;
    private String motivo;
    private String usuario;
    private Timestamp fechaHora;

    public AuditoriaPedido() {
    }

    public AuditoriaPedido(int idPedido, String accion, String motivo, String usuario) {
        this(0, idPedido, accion, motivo, usuario, Timestamp.valueOf(LocalDateTime.now()));
    }

    public AuditoriaPedido(int id, int idPedido, String accion, String motivo, String usuario, Timestamp fechaHora) {
        this.id = id;
        this.idPedido = idPedido;
        this.accion = accion != null ? accion.trim().toUpperCase() : "ACCION";
        this.motivo = motivo != null ? motivo.trim() : "";
        this.usuario = usuario != null && !usuario.isBlank() ? usuario.trim() : "Sistema";
        this.fechaHora = fechaHora != null ? fechaHora : Timestamp.valueOf(LocalDateTime.now());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion != null ? accion.trim().toUpperCase() : "ACCION";
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo != null ? motivo.trim() : "";
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario != null && !usuario.isBlank() ? usuario.trim() : "Sistema";
    }

    public Timestamp getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(Timestamp fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getFechaHoraFormateada() {
        if (fechaHora == null) {
            return "";
        }
        return fechaHora.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public String toString() {
        return "AuditoriaPedido{" +
                "id=" + id +
                ", idPedido=" + idPedido +
                ", accion='" + accion + '\'' +
                ", motivo='" + motivo + '\'' +
                ", usuario='" + usuario + '\'' +
                ", fechaHora=" + fechaHora +
                '}';
    }
}

package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Pedidos {
    private int id;
    private int id_sala;
    private int num_mesa;
    private String fecha;
    private BigDecimal subtotal;
    private BigDecimal ivaPorcentaje;
    private BigDecimal ivaMonto;
    private BigDecimal total = BigDecimal.ZERO;
    private String sala;
    private String usuario;
    private String estado;
    private BigDecimal tasaCambio;
    private BigDecimal subtotalBs;
    private BigDecimal ivaBs;
    private BigDecimal totalBs;
    private String clienteNombre = "Consumidor Final";
    private String clienteDocumento = "V-00000000";
    private String metodoPago = "EFECTIVO";

    public Pedidos() {
    }

    public Pedidos(int id, int id_sala, int num_mesa, String fecha, BigDecimal total, String sala, String usuario, String estado) {
        this.id = id;
        this.id_sala = id_sala;
        this.num_mesa = num_mesa;
        this.fecha = fecha;
        setTotalDecimal(total);
        this.sala = sala;
        this.usuario = usuario;
        this.estado = estado;
    }

    public Pedidos(int id, int id_sala, int num_mesa, String fecha, BigDecimal total, String sala, String usuario, String estado, BigDecimal tasaCambio, BigDecimal totalBs) {
        this(id, id_sala, num_mesa, fecha, total, sala, usuario, estado);
        this.tasaCambio = tasaCambio;
        this.totalBs = totalBs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId_sala() {
        return id_sala;
    }

    public void setId_sala(int id_sala) {
        this.id_sala = id_sala;
    }

    public int getNum_mesa() {
        return num_mesa;
    }

    public void setNum_mesa(int num_mesa) {
        this.num_mesa = num_mesa;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public BigDecimal getSubtotal() {
        return subtotal != null ? subtotal : total;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal != null ? subtotal.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getIvaPorcentaje() {
        return ivaPorcentaje != null ? ivaPorcentaje : BigDecimal.ZERO;
    }

    public void setIvaPorcentaje(BigDecimal ivaPorcentaje) {
        this.ivaPorcentaje = ivaPorcentaje;
    }

    public BigDecimal getIvaMonto() {
        return ivaMonto != null ? ivaMonto : BigDecimal.ZERO;
    }

    public void setIvaMonto(BigDecimal ivaMonto) {
        this.ivaMonto = ivaMonto != null ? ivaMonto.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getTotalDecimal() {
        return total;
    }

    public void setTotalDecimal(BigDecimal total) {
        if (total == null) {
            throw ErrorAplicacionException.validacion("El total del pedido es obligatorio.");
        }
        this.total = total.setScale(2, RoundingMode.HALF_UP);
    }

    public String getSala() {
        return sala;
    }

    public void setSala(String sala) {
        this.sala = sala;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public BigDecimal getTasaCambio() {
        return tasaCambio;
    }

    public void setTasaCambio(BigDecimal tasaCambio) {
        this.tasaCambio = tasaCambio;
    }

    public BigDecimal getSubtotalBs() {
        return subtotalBs;
    }

    public void setSubtotalBs(BigDecimal subtotalBs) {
        this.subtotalBs = subtotalBs != null ? subtotalBs.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getIvaBs() {
        return ivaBs;
    }

    public void setIvaBs(BigDecimal ivaBs) {
        this.ivaBs = ivaBs != null ? ivaBs.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getTotalBs() {
        return totalBs;
    }

    public void setTotalBs(BigDecimal totalBs) {
        this.totalBs = totalBs != null ? totalBs.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public String getClienteNombre() {
        return clienteNombre == null || clienteNombre.trim().isEmpty()
                ? "Consumidor Final" : clienteNombre.trim();
    }

    public void setClienteNombre(String clienteNombre) {
        if (clienteNombre == null || clienteNombre.trim().isEmpty()) {
            this.clienteNombre = "Consumidor Final";
        } else {
            this.clienteNombre = clienteNombre.trim();
        }
    }

    public String getClienteDocumento() {
        return clienteDocumento == null || clienteDocumento.trim().isEmpty()
                ? "V-00000000" : clienteDocumento.trim();
    }

    public void setClienteDocumento(String clienteDocumento) {
        if (clienteDocumento == null || clienteDocumento.trim().isEmpty()) {
            this.clienteDocumento = "V-00000000";
        } else {
            this.clienteDocumento = clienteDocumento.trim();
        }
    }

    public String getMetodoPago() {
        return metodoPago == null || metodoPago.trim().isEmpty() ? "EFECTIVO" : metodoPago.trim().toUpperCase();
    }

    public void setMetodoPago(String metodoPago) {
        if (metodoPago == null || metodoPago.trim().isEmpty()) {
            this.metodoPago = "EFECTIVO";
            return;
        }
        String normalizado = metodoPago.trim().toUpperCase();
        switch (normalizado) {
            case "EFECTIVO":
            case "TRANSFERENCIA":
            case "TARJETA":
            case "PAGO_MOVIL":
            case "MIXTO":
                this.metodoPago = normalizado;
                break;
            default:
                throw ErrorAplicacionException.validacion(
                        "Método de pago no válido: " + metodoPago + ". Debe ser EFECTIVO, TRANSFERENCIA, TARJETA, PAGO_MOVIL o MIXTO.");
        }
    }
}

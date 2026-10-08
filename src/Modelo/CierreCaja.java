package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modelo de datos representativo del Cierre de Caja del Restaurante (Corte X y Corte Z).
 * Diseñado para emitirse en tickera térmica de 80 mm con desglose bimonetario.
 */
public class CierreCaja {

    public enum TipoCierre {
        PARCIAL("CORTE X - CIERRE PARCIAL"),
        TOTAL("CORTE Z - CIERRE TOTAL");

        private final String etiqueta;

        TipoCierre(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String getEtiqueta() {
            return etiqueta;
        }
    }

    public static class ResumenMetodoPago {
        private final String metodo;
        private final int cantidadTransacciones;
        private final BigDecimal montoUsd;
        private final BigDecimal montoBs;

        public ResumenMetodoPago(String metodo, int cantidad, BigDecimal montoUsd, BigDecimal montoBs) {
            this.metodo = metodo != null && !metodo.isBlank() ? metodo.trim().toUpperCase() : "EFECTIVO";
            this.cantidadTransacciones = cantidad;
            this.montoUsd = montoUsd != null ? montoUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            this.montoBs = montoBs != null ? montoBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        }

        public String getMetodo() {
            return metodo;
        }

        public int getCantidadTransacciones() {
            return cantidadTransacciones;
        }

        public BigDecimal getMontoUsd() {
            return montoUsd;
        }

        public BigDecimal getMontoBs() {
            return montoBs;
        }
    }

    public static class ResumenSala {
        private final String sala;
        private final int cantidadPedidos;
        private final BigDecimal montoUsd;
        private final BigDecimal montoBs;

        public ResumenSala(String sala, int cantidadPedidos, BigDecimal montoUsd, BigDecimal montoBs) {
            this.sala = sala != null && !sala.isBlank() ? sala.trim() : "General";
            this.cantidadPedidos = cantidadPedidos;
            this.montoUsd = montoUsd != null ? montoUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            this.montoBs = montoBs != null ? montoBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        }

        public String getSala() {
            return sala;
        }

        public int getCantidadPedidos() {
            return cantidadPedidos;
        }

        public BigDecimal getMontoUsd() {
            return montoUsd;
        }

        public BigDecimal getMontoBs() {
            return montoBs;
        }
    }

    public static class ResumenUsuario {
        private final String usuario;
        private final int cantidadPedidos;
        private final BigDecimal montoUsd;

        public ResumenUsuario(String usuario, int cantidadPedidos, BigDecimal montoUsd) {
            this.usuario = usuario != null && !usuario.isBlank() ? usuario.trim() : "Operador";
            this.cantidadPedidos = cantidadPedidos;
            this.montoUsd = montoUsd != null ? montoUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        }

        public String getUsuario() {
            return usuario;
        }

        public int getCantidadPedidos() {
            return cantidadPedidos;
        }

        public BigDecimal getMontoUsd() {
            return montoUsd;
        }
    }

    public static class PlatoVendido {
        private final String nombre;
        private final int cantidad;
        private final BigDecimal totalUsd;

        public PlatoVendido(String nombre, int cantidad, BigDecimal totalUsd) {
            this.nombre = nombre != null ? nombre.trim() : "Plato";
            this.cantidad = cantidad;
            this.totalUsd = totalUsd != null ? totalUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        }

        public String getNombre() {
            return nombre;
        }

        public int getCantidad() {
            return cantidad;
        }

        public BigDecimal getTotalUsd() {
            return totalUsd;
        }
    }

    private TipoCierre tipo = TipoCierre.PARCIAL;
    private String fecha = "";
    private String fechaHoraEmision = "";
    private String usuarioEmisor = "Sistema";
    private BigDecimal totalVentasUsd = BigDecimal.ZERO.setScale(2);
    private BigDecimal totalVentasBs = BigDecimal.ZERO.setScale(2);
    private BigDecimal subtotalUsd = BigDecimal.ZERO.setScale(2);
    private BigDecimal subtotalBs = BigDecimal.ZERO.setScale(2);
    private BigDecimal ivaUsd = BigDecimal.ZERO.setScale(2);
    private BigDecimal ivaBs = BigDecimal.ZERO.setScale(2);
    private BigDecimal tasaCambioReferencia = new BigDecimal("36.5000");
    private int pedidosFinalizados = 0;
    private int pedidosPendientes = 0;
    private int totalArticulos = 0;
    private BigDecimal ticketPromedioUsd = BigDecimal.ZERO.setScale(2);
    private BigDecimal ticketPromedioBs = BigDecimal.ZERO.setScale(2);

    private List<ResumenMetodoPago> desgloseMetodos = new ArrayList<>();
    private List<ResumenSala> desgloseSalas = new ArrayList<>();
    private List<ResumenUsuario> desgloseUsuarios = new ArrayList<>();
    private List<PlatoVendido> topPlatos = new ArrayList<>();

    public CierreCaja() {
    }

    public TipoCierre getTipo() {
        return tipo;
    }

    public void setTipo(TipoCierre tipo) {
        this.tipo = tipo != null ? tipo : TipoCierre.PARCIAL;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha != null ? fecha.trim() : "";
    }

    public String getFechaHoraEmision() {
        return fechaHoraEmision;
    }

    public void setFechaHoraEmision(String fechaHoraEmision) {
        this.fechaHoraEmision = fechaHoraEmision != null ? fechaHoraEmision.trim() : "";
    }

    public String getUsuarioEmisor() {
        return usuarioEmisor;
    }

    public void setUsuarioEmisor(String usuarioEmisor) {
        this.usuarioEmisor = usuarioEmisor != null ? usuarioEmisor.trim() : "Sistema";
    }

    public BigDecimal getTotalVentasUsd() {
        return totalVentasUsd;
    }

    public void setTotalVentasUsd(BigDecimal totalVentasUsd) {
        this.totalVentasUsd = totalVentasUsd != null ? totalVentasUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getTotalVentasBs() {
        return totalVentasBs;
    }

    public void setTotalVentasBs(BigDecimal totalVentasBs) {
        this.totalVentasBs = totalVentasBs != null ? totalVentasBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getSubtotalUsd() {
        return subtotalUsd;
    }

    public void setSubtotalUsd(BigDecimal subtotalUsd) {
        this.subtotalUsd = subtotalUsd != null ? subtotalUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getSubtotalBs() {
        return subtotalBs;
    }

    public void setSubtotalBs(BigDecimal subtotalBs) {
        this.subtotalBs = subtotalBs != null ? subtotalBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getIvaUsd() {
        return ivaUsd;
    }

    public void setIvaUsd(BigDecimal ivaUsd) {
        this.ivaUsd = ivaUsd != null ? ivaUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getIvaBs() {
        return ivaBs;
    }

    public void setIvaBs(BigDecimal ivaBs) {
        this.ivaBs = ivaBs != null ? ivaBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getTasaCambioReferencia() {
        return tasaCambioReferencia;
    }

    public void setTasaCambioReferencia(BigDecimal tasaCambioReferencia) {
        this.tasaCambioReferencia = tasaCambioReferencia != null ? tasaCambioReferencia : new BigDecimal("36.5000");
    }

    public int getPedidosFinalizados() {
        return pedidosFinalizados;
    }

    public void setPedidosFinalizados(int pedidosFinalizados) {
        this.pedidosFinalizados = pedidosFinalizados;
    }

    public int getPedidosPendientes() {
        return pedidosPendientes;
    }

    public void setPedidosPendientes(int pedidosPendientes) {
        this.pedidosPendientes = pedidosPendientes;
    }

    public int getTotalArticulos() {
        return totalArticulos;
    }

    public void setTotalArticulos(int totalArticulos) {
        this.totalArticulos = totalArticulos;
    }

    public BigDecimal getTicketPromedioUsd() {
        return ticketPromedioUsd;
    }

    public void setTicketPromedioUsd(BigDecimal ticketPromedioUsd) {
        this.ticketPromedioUsd = ticketPromedioUsd != null ? ticketPromedioUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getTicketPromedioBs() {
        return ticketPromedioBs;
    }

    public void setTicketPromedioBs(BigDecimal ticketPromedioBs) {
        this.ticketPromedioBs = ticketPromedioBs != null ? ticketPromedioBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public List<ResumenMetodoPago> getDesgloseMetodos() {
        return Collections.unmodifiableList(desgloseMetodos);
    }

    public void setDesgloseMetodos(List<ResumenMetodoPago> desgloseMetodos) {
        this.desgloseMetodos = desgloseMetodos != null ? new ArrayList<>(desgloseMetodos) : new ArrayList<>();
    }

    public List<ResumenSala> getDesgloseSalas() {
        return Collections.unmodifiableList(desgloseSalas);
    }

    public void setDesgloseSalas(List<ResumenSala> desgloseSalas) {
        this.desgloseSalas = desgloseSalas != null ? new ArrayList<>(desgloseSalas) : new ArrayList<>();
    }

    public List<ResumenUsuario> getDesgloseUsuarios() {
        return Collections.unmodifiableList(desgloseUsuarios);
    }

    public void setDesgloseUsuarios(List<ResumenUsuario> desgloseUsuarios) {
        this.desgloseUsuarios = desgloseUsuarios != null ? new ArrayList<>(desgloseUsuarios) : new ArrayList<>();
    }

    public List<PlatoVendido> getTopPlatos() {
        return Collections.unmodifiableList(topPlatos);
    }

    public void setTopPlatos(List<PlatoVendido> topPlatos) {
        this.topPlatos = topPlatos != null ? new ArrayList<>(topPlatos) : new ArrayList<>();
    }

    private BigDecimal efectivoDeclaradoBs = null;
    private BigDecimal efectivoDeclaradoUsd = null;
    private BigDecimal efectivoEsperadoBsPersistido;
    private BigDecimal efectivoEsperadoUsdPersistido;
    private int pagosMixtosSinDesglose;

    public void setResumenEfectivo(BigDecimal efectivoBs, BigDecimal efectivoUsd, int pagosMixtosSinDesglose) {
        if (efectivoBs == null || efectivoUsd == null || efectivoBs.signum() < 0
                || efectivoUsd.signum() < 0 || pagosMixtosSinDesglose < 0) {
            throw ErrorAplicacionException.validacion("El resumen de efectivo del cierre no es válido.");
        }
        this.efectivoEsperadoBsPersistido = efectivoBs.setScale(2, RoundingMode.HALF_UP);
        this.efectivoEsperadoUsdPersistido = efectivoUsd.setScale(2, RoundingMode.HALF_UP);
        this.pagosMixtosSinDesglose = pagosMixtosSinDesglose;
    }

    public int getPagosMixtosSinDesglose() {
        return pagosMixtosSinDesglose;
    }

    public BigDecimal getEfectivoDeclaradoBs() {
        return efectivoDeclaradoBs;
    }

    public void setEfectivoDeclaradoBs(BigDecimal efectivoDeclaradoBs) {
        if (efectivoDeclaradoBs != null && efectivoDeclaradoBs.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El efectivo contado en Bs no puede ser negativo.");
        }
        this.efectivoDeclaradoBs = efectivoDeclaradoBs != null ? efectivoDeclaradoBs.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getEfectivoDeclaradoUsd() {
        return efectivoDeclaradoUsd;
    }

    public void setEfectivoDeclaradoUsd(BigDecimal efectivoDeclaradoUsd) {
        if (efectivoDeclaradoUsd != null && efectivoDeclaradoUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El efectivo contado en USD no puede ser negativo.");
        }
        this.efectivoDeclaradoUsd = efectivoDeclaradoUsd != null ? efectivoDeclaradoUsd.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public boolean tieneConciliacionEfectivo() {
        return tieneConciliacionBs() || tieneConciliacionUsd();
    }

    public boolean tieneConciliacionBs() {
        return efectivoDeclaradoBs != null;
    }

    public boolean tieneConciliacionUsd() {
        return efectivoDeclaradoUsd != null;
    }

    public BigDecimal getTotalEfectivoBs() {
        if (efectivoEsperadoBsPersistido != null) {
            return efectivoEsperadoBsPersistido;
        }
        if (desgloseMetodos == null || desgloseMetodos.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return desgloseMetodos.stream()
                .filter(m -> "EFECTIVO_BS".equalsIgnoreCase(m.getMetodo()) || "EFECTIVO".equalsIgnoreCase(m.getMetodo()))
                .map(ResumenMetodoPago::getMontoBs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalEfectivoUsd() {
        if (efectivoEsperadoUsdPersistido != null) {
            return efectivoEsperadoUsdPersistido;
        }
        if (desgloseMetodos == null || desgloseMetodos.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return desgloseMetodos.stream()
                .filter(m -> "EFECTIVO_USD".equalsIgnoreCase(m.getMetodo()))
                .map(ResumenMetodoPago::getMontoUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getDiferenciaEfectivoBs() {
        if (efectivoDeclaradoBs == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return efectivoDeclaradoBs.subtract(getTotalEfectivoBs()).setScale(2, RoundingMode.HALF_UP);
    }

    public String getEstadoConciliacionBs() {
        if (efectivoDeclaradoBs == null) {
            return "NO DECLARADO";
        }
        int cmp = getDiferenciaEfectivoBs().compareTo(BigDecimal.ZERO);
        if (cmp == 0) {
            return "EXACTO";
        } else if (cmp > 0) {
            return "SOBRANTE";
        } else {
            return "FALTANTE";
        }
    }

    public BigDecimal getDiferenciaEfectivoUsd() {
        if (efectivoDeclaradoUsd == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return efectivoDeclaradoUsd.subtract(getTotalEfectivoUsd()).setScale(2, RoundingMode.HALF_UP);
    }

    public String getEstadoConciliacionUsd() {
        if (efectivoDeclaradoUsd == null) {
            return "NO DECLARADO";
        }
        int cmp = getDiferenciaEfectivoUsd().compareTo(BigDecimal.ZERO);
        if (cmp == 0) {
            return "EXACTO";
        } else if (cmp > 0) {
            return "SOBRANTE";
        } else {
            return "FALTANTE";
        }
    }
}

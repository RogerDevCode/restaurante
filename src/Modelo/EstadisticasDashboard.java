package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class EstadisticasDashboard {

    public static class ItemEstadistica {
        private final String nombre;
        private final int cantidad;
        private final BigDecimal totalDolares;
        private final BigDecimal totalBs;

        public ItemEstadistica(String nombre, int cantidad, BigDecimal totalDolares, BigDecimal totalBs) {
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.totalDolares = totalDolares != null ? totalDolares : BigDecimal.ZERO;
            this.totalBs = totalBs != null ? totalBs : BigDecimal.ZERO;
        }

        public String getNombre() {
            return nombre;
        }

        public int getCantidad() {
            return cantidad;
        }

        public BigDecimal getTotalDolares() {
            return totalDolares;
        }

        public BigDecimal getTotalBs() {
            return totalBs;
        }
    }

    private BigDecimal ventasHoyDolares = BigDecimal.ZERO;
    private BigDecimal ventasHoyBs = BigDecimal.ZERO;
    private int pedidosHoy = 0;

    private BigDecimal ventasHistoricasDolares = BigDecimal.ZERO;
    private BigDecimal ventasHistoricasBs = BigDecimal.ZERO;
    private int pedidosHistoricos = 0;

    private int totalClientes = 0;
    private int mesasOcupadasActuales = 0;

    private List<ItemEstadistica> topPlatos = new ArrayList<>();
    private List<ItemEstadistica> topSalas = new ArrayList<>();
    private List<ItemEstadistica> estadisticasMesoneros = new ArrayList<>();

    public EstadisticasDashboard() {
    }

    public BigDecimal getVentasHoyDolares() {
        return ventasHoyDolares;
    }

    public void setVentasHoyDolares(BigDecimal ventasHoyDolares) {
        this.ventasHoyDolares = ventasHoyDolares != null ? ventasHoyDolares : BigDecimal.ZERO;
    }

    public BigDecimal getVentasHoyBs() {
        return ventasHoyBs;
    }

    public void setVentasHoyBs(BigDecimal ventasHoyBs) {
        this.ventasHoyBs = ventasHoyBs != null ? ventasHoyBs : BigDecimal.ZERO;
    }

    public int getPedidosHoy() {
        return pedidosHoy;
    }

    public void setPedidosHoy(int pedidosHoy) {
        this.pedidosHoy = pedidosHoy;
    }

    public BigDecimal getVentasHistoricasDolares() {
        return ventasHistoricasDolares;
    }

    public void setVentasHistoricasDolares(BigDecimal ventasHistoricasDolares) {
        this.ventasHistoricasDolares = ventasHistoricasDolares != null ? ventasHistoricasDolares : BigDecimal.ZERO;
    }

    public BigDecimal getVentasHistoricasBs() {
        return ventasHistoricasBs;
    }

    public void setVentasHistoricasBs(BigDecimal ventasHistoricasBs) {
        this.ventasHistoricasBs = ventasHistoricasBs != null ? ventasHistoricasBs : BigDecimal.ZERO;
    }

    public int getPedidosHistoricos() {
        return pedidosHistoricos;
    }

    public void setPedidosHistoricos(int pedidosHistoricos) {
        this.pedidosHistoricos = pedidosHistoricos;
    }

    public int getTotalClientes() {
        return totalClientes;
    }

    public void setTotalClientes(int totalClientes) {
        this.totalClientes = totalClientes;
    }

    public int getMesasOcupadasActuales() {
        return mesasOcupadasActuales;
    }

    public void setMesasOcupadasActuales(int mesasOcupadasActuales) {
        this.mesasOcupadasActuales = mesasOcupadasActuales;
    }

    public List<ItemEstadistica> getTopPlatos() {
        return topPlatos;
    }

    public void setTopPlatos(List<ItemEstadistica> topPlatos) {
        this.topPlatos = topPlatos != null ? topPlatos : new ArrayList<>();
    }

    public List<ItemEstadistica> getTopSalas() {
        return topSalas;
    }

    public void setTopSalas(List<ItemEstadistica> topSalas) {
        this.topSalas = topSalas != null ? topSalas : new ArrayList<>();
    }

    public BigDecimal getTicketPromedioHoyDolares() {
        if (pedidosHoy <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return ventasHoyDolares.divide(BigDecimal.valueOf(pedidosHoy), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTicketPromedioHoyBs() {
        if (pedidosHoy <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return ventasHoyBs.divide(BigDecimal.valueOf(pedidosHoy), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTicketPromedioHistoricoDolares() {
        if (pedidosHistoricos <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return ventasHistoricasDolares.divide(BigDecimal.valueOf(pedidosHistoricos), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTicketPromedioHistoricoBs() {
        if (pedidosHistoricos <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return ventasHistoricasBs.divide(BigDecimal.valueOf(pedidosHistoricos), 2, RoundingMode.HALF_UP);
    }

    public List<ItemEstadistica> getEstadisticasMesoneros() {
        return estadisticasMesoneros;
    }

    public void setEstadisticasMesoneros(List<ItemEstadistica> estadisticasMesoneros) {
        this.estadisticasMesoneros = estadisticasMesoneros != null ? estadisticasMesoneros : new ArrayList<>();
    }
}

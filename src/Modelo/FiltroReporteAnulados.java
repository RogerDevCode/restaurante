package Modelo;

import java.time.LocalDate;

/**
 * Criterios de filtrado multicriterio para el reporte de tickets anulados.
 */
public class FiltroReporteAnulados {

    public enum TipoFiltroAnulacion {
        TODOS("Todas las anulaciones"),
        SOLO_PENDIENTES_FUGAS("Solo Cuentas Pendientes (Fugas / Abandonos)"),
        SOLO_FACTURAS_COBRADAS("Solo Facturas Cobradas (Revertidas en Caja)");

        private final String descripcion;

        TipoFiltroAnulacion(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }

        @Override
        public String toString() {
            return descripcion;
        }
    }

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private Integer numMesa;
    private String usuario;
    private TipoFiltroAnulacion tipo = TipoFiltroAnulacion.TODOS;
    private String textoBusqueda;

    public FiltroReporteAnulados() {
    }

    public static FiltroReporteAnulados paraHoy() {
        FiltroReporteAnulados f = new FiltroReporteAnulados();
        f.setFechaDesde(LocalDate.now());
        f.setFechaHasta(LocalDate.now());
        return f;
    }

    public static FiltroReporteAnulados paraMesActual() {
        FiltroReporteAnulados f = new FiltroReporteAnulados();
        LocalDate now = LocalDate.now();
        f.setFechaDesde(now.withDayOfMonth(1));
        f.setFechaHasta(now.withDayOfMonth(now.lengthOfMonth()));
        return f;
    }

    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public void setFechaDesde(LocalDate fechaDesde) {
        this.fechaDesde = fechaDesde;
    }

    public LocalDate getFechaHasta() {
        return fechaHasta;
    }

    public void setFechaHasta(LocalDate fechaHasta) {
        this.fechaHasta = fechaHasta;
    }

    public Integer getNumMesa() {
        return numMesa;
    }

    public void setNumMesa(Integer numMesa) {
        this.numMesa = numMesa;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public TipoFiltroAnulacion getTipo() {
        return tipo;
    }

    public void setTipo(TipoFiltroAnulacion tipo) {
        this.tipo = tipo != null ? tipo : TipoFiltroAnulacion.TODOS;
    }

    public String getTextoBusqueda() {
        return textoBusqueda;
    }

    public void setTextoBusqueda(String textoBusqueda) {
        this.textoBusqueda = textoBusqueda;
    }

    public String getDescripcionRangoFechas() {
        if (fechaDesde == null && fechaHasta == null) {
            return "Histórico Completo (Sin filtro de fecha)";
        }
        if (fechaDesde != null && fechaDesde.equals(fechaHasta)) {
            if (fechaDesde.equals(LocalDate.now())) {
                return "Hoy (" + fechaDesde + ")";
            }
            return "Día " + fechaDesde;
        }
        String d = fechaDesde != null ? fechaDesde.toString() : "Inicio";
        String h = fechaHasta != null ? fechaHasta.toString() : "Hoy";
        return d + " al " + h;
    }
}

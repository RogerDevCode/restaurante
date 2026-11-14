package Modelo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Filtro de consulta para la generación del Libro de Ventas SENIAT.
 * Soporta meses completos y cortes quincenales (Contribuyentes Especiales).
 */
public final class FiltroLibroVentas {

    public enum TipoPeriodo {
        MES_COMPLETO("Mes Completo"),
        PRIMERA_QUINCENA("1ra Quincena (01 al 15)"),
        SEGUNDA_QUINCENA("2da Quincena (16 al fin de mes)");

        private final String etiqueta;

        TipoPeriodo(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    private final int ano;
    private final int mes;
    private final TipoPeriodo periodo;

    public FiltroLibroVentas(int ano, int mes, TipoPeriodo periodo) {
        if (ano < 2000 || ano > 2100) {
            throw ErrorAplicacionException.validacion("El año fiscal indicado no es válido: " + ano);
        }
        if (mes < 1 || mes > 12) {
            throw ErrorAplicacionException.validacion("El mes fiscal debe estar entre 1 y 12.");
        }
        this.ano = ano;
        this.mes = mes;
        this.periodo = Objects.requireNonNullElse(periodo, TipoPeriodo.MES_COMPLETO);
    }

    public static FiltroLibroVentas mesActual() {
        LocalDate hoy = LocalDate.now();
        return new FiltroLibroVentas(hoy.getYear(), hoy.getMonthValue(), TipoPeriodo.MES_COMPLETO);
    }

    public int getAno() { return ano; }
    public int getMes() { return mes; }
    public TipoPeriodo getPeriodo() { return periodo; }

    public LocalDate getFechaDesde() {
        if (periodo == TipoPeriodo.SEGUNDA_QUINCENA) {
            return LocalDate.of(ano, mes, 16);
        }
        return LocalDate.of(ano, mes, 1);
    }

    public LocalDate getFechaHasta() {
        YearMonth ym = YearMonth.of(ano, mes);
        if (periodo == TipoPeriodo.PRIMERA_QUINCENA) {
            return LocalDate.of(ano, mes, 15);
        }
        return ym.atEndOfMonth();
    }

    public String getDescripcionPeriodo() {
        String nombreMes = java.time.Month.of(mes).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.of("es", "VE")).toUpperCase();
        return switch (periodo) {
            case PRIMERA_QUINCENA -> "1RA QUINCENA DE " + nombreMes + " " + ano;
            case SEGUNDA_QUINCENA -> "2DA QUINCENA DE " + nombreMes + " " + ano;
            case MES_COMPLETO -> nombreMes + " " + ano;
        };
    }
}

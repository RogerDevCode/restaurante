package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Registro inmutable (Java 21 Record) que encapsula el cálculo del
 * Impuesto a las Grandes Transacciones Financieras (IGTF) del 3%
 * sobre pagos percibidos en moneda extranjera (efectivo USD) sin intermediación
 * bancaria nacional por Sujetos Pasivos Especiales.
 *
 * <p>Base legal: Reforma Parcial del Decreto con Rango, Valor y Fuerza de Ley
 * de Impuesto a las Grandes Transacciones Financieras (G.O. N° 6.687 Extraordinario)
 * y Providencia Administrativa SENIAT N° SNAT/2022/000013.</p>
 */
public record IgtfRecord(
        BigDecimal baseImponibleUsd,
        BigDecimal porcentaje,
        BigDecimal montoIgtfUsd,
        BigDecimal baseImponibleBs,
        BigDecimal montoIgtfBs,
        BigDecimal tasaCambio) {

    public static final BigDecimal ALICUOTA_PREDETERMINADA = new BigDecimal("3.00");
    private static final BigDecimal CERO_2DEC = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CIEN = new BigDecimal("100.00");

    public IgtfRecord {
        Objects.requireNonNull(baseImponibleUsd, "La base imponible del IGTF en USD no puede ser nula.");
        Objects.requireNonNull(porcentaje, "El porcentaje de alícuota IGTF no puede ser nulo.");
        Objects.requireNonNull(montoIgtfUsd, "El monto de IGTF en USD no puede ser nulo.");
        Objects.requireNonNull(baseImponibleBs, "La base imponible del IGTF en Bs. no puede ser nula.");
        Objects.requireNonNull(montoIgtfBs, "El monto de IGTF en Bs. no puede ser nulo.");
        Objects.requireNonNull(tasaCambio, "La tasa de cambio del IGTF no puede ser nula.");

        if (baseImponibleUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("La base imponible del IGTF en USD no puede ser negativa.");
        }
        if (montoIgtfUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El monto de IGTF en USD no puede ser negativo.");
        }
        if (baseImponibleBs.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("La base imponible del IGTF en Bs. no puede ser negativa.");
        }
        if (montoIgtfBs.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El monto de IGTF en Bs. no puede ser negativo.");
        }
        if (porcentaje.compareTo(BigDecimal.ZERO) < 0 || porcentaje.compareTo(CIEN) > 0) {
            throw ErrorAplicacionException.validacion("El porcentaje de IGTF debe estar entre 0.00% y 100.00%.");
        }
        if (tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio para el IGTF debe ser mayor a cero.");
        }
    }

    /**
     * Calcula el IGTF aplicando la alícuota predeterminada del 3.00%.
     *
     * @param baseDivisaUsd Monto efectivamente pagado en divisas (USD).
     * @param tasaCambio    Tasa de cambio oficial BCV en el momento del cobro.
     * @return Instancia inmutable con el cálculo verificado a 2 decimales.
     */
    public static IgtfRecord calcular(BigDecimal baseDivisaUsd, BigDecimal tasaCambio) {
        return calcular(baseDivisaUsd, tasaCambio, ALICUOTA_PREDETERMINADA);
    }

    /**
     * Calcula el IGTF con una alícuota personalizada (ej. 3.00% o configurable por empresa).
     *
     * @param baseDivisaUsd Monto efectivamente pagado en divisas (USD).
     * @param tasaCambio    Tasa de cambio oficial BCV en el momento del cobro.
     * @param alicuota      Porcentaje a aplicar (ej. 3.00). Si es null o cero, no causa IGTF.
     * @return Instancia inmutable con los desgloses en USD y Bs.
     */
    public static IgtfRecord calcular(BigDecimal baseDivisaUsd, BigDecimal tasaCambio, BigDecimal alicuota) {
        if (tasaCambio == null || tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio para calcular IGTF debe ser válida y positiva.");
        }
        if (baseDivisaUsd == null || baseDivisaUsd.signum() <= 0) {
            return sinIgtf(tasaCambio);
        }

        BigDecimal pct = (alicuota != null && alicuota.signum() >= 0)
                ? alicuota.setScale(2, RoundingMode.HALF_UP)
                : ALICUOTA_PREDETERMINADA;

        if (pct.signum() == 0) {
            return sinIgtf(tasaCambio);
        }

        BigDecimal baseUsd = baseDivisaUsd.setScale(2, RoundingMode.HALF_UP);
        BigDecimal igtfUsd = baseUsd.multiply(pct)
                .divide(CIEN, 8, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal baseBs = baseUsd.multiply(tasaCambio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igtfBs = igtfUsd.multiply(tasaCambio).setScale(2, RoundingMode.HALF_UP);

        return new IgtfRecord(baseUsd, pct, igtfUsd, baseBs, igtfBs, tasaCambio.setScale(4, RoundingMode.HALF_UP));
    }

    /**
     * Retorna una representación sin IGTF (cuando el pago es 100% en Bs. o el comercio no es Sujeto Pasivo Especial).
     */
    public static IgtfRecord sinIgtf(BigDecimal tasaCambio) {
        BigDecimal tasa = (tasaCambio != null && tasaCambio.signum() > 0)
                ? tasaCambio.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        return new IgtfRecord(CERO_2DEC, CERO_2DEC, CERO_2DEC, CERO_2DEC, CERO_2DEC, tasa);
    }

    /**
     * Retorna true si este registro generó un monto a percibir mayor a cero.
     */
    public boolean tieneIgtf() {
        return montoIgtfUsd.signum() > 0;
    }

    /**
     * Suma el IGTF calculado al total de la cuenta en USD.
     */
    public BigDecimal totalConIgtfUsd(BigDecimal totalPedidoUsd) {
        if (totalPedidoUsd == null) return montoIgtfUsd;
        return totalPedidoUsd.add(montoIgtfUsd).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Suma el IGTF calculado al total de la cuenta en Bs.
     */
    public BigDecimal totalConIgtfBs(BigDecimal totalPedidoBs) {
        if (totalPedidoBs == null) return montoIgtfBs;
        return totalPedidoBs.add(montoIgtfBs).setScale(2, RoundingMode.HALF_UP);
    }
}

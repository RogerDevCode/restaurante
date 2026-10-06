package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Registro inmutable (Java 21 Record) que encapsula el cálculo fiscal completo:
 * Subtotal (Base imponible), IVA (%) y monto calculado, y Total General,
 * tanto en USD como en Bolívares (Bs.) según la tasa de cambio oficial.
 */
public record CalculoFiscalRecord(
        BigDecimal subtotalUsd,
        BigDecimal ivaPorcentaje,
        BigDecimal ivaUsd,
        BigDecimal totalUsd,
        BigDecimal tasaCambio,
        BigDecimal subtotalBs,
        BigDecimal ivaBs,
        BigDecimal totalBs) {

    public CalculoFiscalRecord {
        Objects.requireNonNull(subtotalUsd, "El subtotal en USD no puede ser nulo.");
        Objects.requireNonNull(ivaPorcentaje, "El porcentaje de IVA no puede ser nulo.");
        Objects.requireNonNull(ivaUsd, "El monto de IVA en USD no puede ser nulo.");
        Objects.requireNonNull(totalUsd, "El total en USD no puede ser nulo.");
        Objects.requireNonNull(tasaCambio, "La tasa de cambio no puede ser nula.");
        Objects.requireNonNull(subtotalBs, "El subtotal en Bs. no puede ser nulo.");
        Objects.requireNonNull(ivaBs, "El monto de IVA en Bs. no puede ser nulo.");
        Objects.requireNonNull(totalBs, "El total en Bs. no puede ser nulo.");

        if (subtotalUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El subtotal en USD no puede ser negativo.");
        }
        if (ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0 || ivaPorcentaje.compareTo(new BigDecimal("100.00")) > 0) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA debe estar entre 0.00% y 100.00%.");
        }
        if (ivaPorcentaje.scale() > 2) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA debe tener hasta dos decimales.");
        }
        if (tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio debe ser positiva.");
        }
    }

    /**
     * Calcula de forma atómica y precisa los montos fiscales a partir del subtotal neto,
     * el porcentaje de IVA y la tasa de cambio del día.
     *
     * @param subtotalUsd Base imponible acumulada en USD
     * @param ivaPorcentaje Porcentaje de IVA aplicable (ej. 16.00)
     * @param tasaCambio Tasa de cambio oficial ($ a Bs.)
     * @return CalculoFiscalRecord con todos los valores redondeados a 2 decimales (HALF_UP)
     */
    public static CalculoFiscalRecord calcular(BigDecimal subtotalUsd, BigDecimal ivaPorcentaje, BigDecimal tasaCambio) {
        if (subtotalUsd != null && subtotalUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El subtotal en USD no puede ser negativo.");
        }
        if (ivaPorcentaje != null) {
            if (ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0 || ivaPorcentaje.compareTo(new BigDecimal("100.00")) > 0) {
                throw ErrorAplicacionException.validacion("El porcentaje de IVA debe estar entre 0.00% y 100.00%.");
            }
            if (ivaPorcentaje.scale() > 2) {
                throw ErrorAplicacionException.validacion("El porcentaje de IVA debe tener hasta dos decimales.");
            }
        }
        if (tasaCambio != null && tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio debe ser positiva.");
        }

        BigDecimal subUsd = subtotalUsd != null ? subtotalUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal ivaPct = ivaPorcentaje != null ? ivaPorcentaje.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal tasa = tasaCambio != null && tasaCambio.compareTo(BigDecimal.ZERO) > 0 ? tasaCambio : new BigDecimal("36.5000");

        BigDecimal cien = new BigDecimal("100");
        BigDecimal ivaUsd = subUsd.multiply(ivaPct).divide(cien, 2, RoundingMode.HALF_UP);
        BigDecimal totalUsd = subUsd.add(ivaUsd).setScale(2, RoundingMode.HALF_UP);

        BigDecimal subBs = subUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaBs = ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totBs = totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        return new CalculoFiscalRecord(subUsd, ivaPct, ivaUsd, totalUsd, tasa, subBs, ivaBs, totBs);
    }
}

package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
        BigDecimal totalBs,
        BigDecimal baseImponibleUsd,
        BigDecimal exentoUsd,
        BigDecimal baseImponibleBs,
        BigDecimal exentoBs) {

    public CalculoFiscalRecord {
        Objects.requireNonNull(subtotalUsd, "El subtotal en USD no puede ser nulo.");
        Objects.requireNonNull(ivaPorcentaje, "El porcentaje de IVA no puede ser nulo.");
        Objects.requireNonNull(ivaUsd, "El monto de IVA en USD no puede ser nulo.");
        Objects.requireNonNull(totalUsd, "El total en USD no puede ser nulo.");
        Objects.requireNonNull(tasaCambio, "La tasa de cambio no puede ser nula.");
        Objects.requireNonNull(subtotalBs, "El subtotal en Bs. no puede ser nulo.");
        Objects.requireNonNull(ivaBs, "El monto de IVA en Bs. no puede ser nulo.");
        Objects.requireNonNull(totalBs, "El total en Bs. no puede ser nulo.");
        Objects.requireNonNull(baseImponibleUsd, "La base imponible en USD no puede ser nula.");
        Objects.requireNonNull(exentoUsd, "El monto exento en USD no puede ser nulo.");
        Objects.requireNonNull(baseImponibleBs, "La base imponible en Bs. no puede ser nula.");
        Objects.requireNonNull(exentoBs, "El monto exento en Bs. no puede ser nulo.");

        if (subtotalUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El subtotal en USD no puede ser negativo.");
        }
        if (baseImponibleUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("La base imponible en USD no puede ser negativa.");
        }
        if (exentoUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El monto exento en USD no puede ser negativo.");
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

    public CalculoFiscalRecord(
            BigDecimal subtotalUsd,
            BigDecimal ivaPorcentaje,
            BigDecimal ivaUsd,
            BigDecimal totalUsd,
            BigDecimal tasaCambio,
            BigDecimal subtotalBs,
            BigDecimal ivaBs,
            BigDecimal totalBs) {
        this(subtotalUsd, ivaPorcentaje, ivaUsd, totalUsd, tasaCambio, subtotalBs, ivaBs, totalBs,
                subtotalUsd, BigDecimal.ZERO.setScale(2), subtotalBs, BigDecimal.ZERO.setScale(2));
    }

    /**
     * Calcula los montos fiscales asumiendo que todo el subtotal está gravado con IVA.
     */
    public static CalculoFiscalRecord calcular(BigDecimal subtotalUsd, BigDecimal ivaPorcentaje, BigDecimal tasaCambio) {
        if (subtotalUsd != null && subtotalUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El subtotal en USD no puede ser negativo.");
        }
        return calcular(subtotalUsd, BigDecimal.ZERO, ivaPorcentaje, tasaCambio);
    }

    /** Calcula el impuesto desde la condición fiscal guardada en cada detalle del pedido. */
    public static CalculoFiscalRecord calcular(List<DetallePedido> detalles, BigDecimal ivaPorcentaje, BigDecimal tasaCambio) {
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir detalles para calcular sus impuestos.");
        }
        for (DetallePedido d : detalles) {
            if (d == null) {
                throw ErrorAplicacionException.validacion("El detalle no puede ser nulo.");
            }
            if (d.getMonedaBase() == null || d.getMonedaBase().isBlank()) {
                throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' no tiene moneda base asignada (requiere clasificación).");
            }
            if (d.getAplicaIva() == null) {
                throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' no tiene condición fiscal de IVA definida.");
            }
            if (d.getPrecioIncluyeIva() == null) {
                throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' no tiene clasificada la condición de IVA incluido.");
            }
            if (d.getIvaPorcentaje() == null) {
                throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' no tiene tasa de IVA asignada.");
            }
        }
        if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0 || ivaPorcentaje.compareTo(new BigDecimal("100.00")) > 0) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA es obligatorio y debe estar entre 0.00% y 100.00%.");
        }
        if (tasaCambio == null || tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio debe ser positiva.");
        }
        BigDecimal tasaValida = tasaCambio.setScale(4, RoundingMode.HALF_UP);
        BigDecimal ivaValido = ivaPorcentaje.setScale(2, RoundingMode.HALF_UP);

        Servicio.CalculoMonetarioPedido.CalculoPedido calc = Servicio.CalculoMonetarioPedido.calcularPedido(detalles, tasaValida);
        return new CalculoFiscalRecord(
                calc.subtotalUsd(),
                ivaValido,
                calc.ivaUsd(),
                calc.totalUsd(),
                tasaValida,
                calc.subtotalVes(),
                calc.ivaVes(),
                calc.totalVes(),
                calc.baseImponibleUsd(),
                calc.exentoUsd(),
                calc.baseImponibleVes(),
                calc.exentoVes());
    }

    /**
     * Calcula de forma atómica y precisa los montos fiscales discriminando entre base imponible
     * (platos gravados) y montos exentos (platos sin IVA).
     *
     * @param baseImponibleUsd Monto total de platos gravados con IVA en USD
     * @param exentoUsd Monto total de platos exentos (sin IVA) en USD
     * @param ivaPorcentaje Porcentaje de IVA aplicable (ej. 16.00)
     * @param tasaCambio Tasa de cambio oficial ($ a Bs.)
     * @return CalculoFiscalRecord con todos los valores redondeados a 2 decimales (HALF_UP)
     */
    public static CalculoFiscalRecord calcular(BigDecimal baseImponibleUsd, BigDecimal exentoUsd, BigDecimal ivaPorcentaje, BigDecimal tasaCambio) {
        if (baseImponibleUsd != null && baseImponibleUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("La base imponible en USD no puede ser negativa.");
        }
        if (exentoUsd != null && exentoUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El monto exento en USD no puede ser negativo.");
        }
        if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0 || ivaPorcentaje.compareTo(new BigDecimal("100.00")) > 0) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA es obligatorio y debe estar entre 0.00% y 100.00%.");
        }
        if (ivaPorcentaje.scale() > 2) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA debe tener hasta dos decimales.");
        }
        if (tasaCambio == null || tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio debe ser positiva.");
        }

        BigDecimal baseUsd = baseImponibleUsd != null ? baseImponibleUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal exUsd = exentoUsd != null ? exentoUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal subUsd = baseUsd.add(exUsd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaPct = ivaPorcentaje.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tasa = tasaCambio.setScale(4, RoundingMode.HALF_UP);

        BigDecimal cien = new BigDecimal("100");
        BigDecimal ivaUsd = baseUsd.multiply(ivaPct).divide(cien, 2, RoundingMode.HALF_UP);
        BigDecimal totalUsd = subUsd.add(ivaUsd).setScale(2, RoundingMode.HALF_UP);

        BigDecimal baseBs = baseUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal exBs = exUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subBs = subUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaBs = ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totBs = totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        return new CalculoFiscalRecord(subUsd, ivaPct, ivaUsd, totalUsd, tasa, subBs, ivaBs, totBs, baseUsd, exUsd, baseBs, exBs);
    }
}

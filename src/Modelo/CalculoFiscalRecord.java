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
        boolean tieneMoneda = detalles.stream().anyMatch(d -> d != null && d.getMonedaBase() != null && !d.getMonedaBase().isBlank());
        if (tieneMoneda) {
            BigDecimal tasaValida = tasaCambio != null && tasaCambio.compareTo(BigDecimal.ZERO) > 0 ? tasaCambio : new BigDecimal("36.5000");
            BigDecimal ivaValido = ivaPorcentaje != null ? ivaPorcentaje : new BigDecimal("16.00");
            for (DetallePedido d : detalles) {
                if (d.getMonedaBase() == null || d.getMonedaBase().isBlank()) {
                    d.setMonedaBase("USD");
                }
                if (d.getPrecioIncluyeIva() == null) {
                    d.setPrecioIncluyeIva(Boolean.FALSE);
                }
                if (d.getIvaPorcentaje() == null) {
                    d.setIvaPorcentaje(d.getAplicaIva() != null && d.getAplicaIva() ? ivaValido : BigDecimal.ZERO.setScale(2));
                }
            }
            Servicio.CalculoMonetarioPedido.CalculoPedido calc = Servicio.CalculoMonetarioPedido.calcularPedido(detalles, tasaValida);
            BigDecimal baseUsd = BigDecimal.ZERO.setScale(2);
            BigDecimal exUsd = BigDecimal.ZERO.setScale(2);
            for (Servicio.CalculoMonetarioPedido.CalculoLinea linea : calc.lineas()) {
                if ("VES".equalsIgnoreCase(linea.monedaBase())) {
                    baseUsd = baseUsd.add(linea.baseImponibleVes().divide(tasaValida, 8, RoundingMode.HALF_UP));
                    exUsd = exUsd.add(linea.exentoVes().divide(tasaValida, 8, RoundingMode.HALF_UP));
                } else {
                    baseUsd = baseUsd.add(linea.baseImponibleFuente());
                    exUsd = exUsd.add(linea.exentoFuente());
                }
            }
            baseUsd = baseUsd.setScale(2, RoundingMode.HALF_UP);
            exUsd = exUsd.setScale(2, RoundingMode.HALF_UP);
            return new CalculoFiscalRecord(
                    calc.subtotalUsd(),
                    ivaValido.setScale(2, RoundingMode.HALF_UP),
                    calc.ivaUsd(),
                    calc.totalUsd(),
                    tasaValida.setScale(4, RoundingMode.HALF_UP),
                    calc.subtotalVes(),
                    calc.ivaVes(),
                    calc.totalVes(),
                    baseUsd,
                    exUsd,
                    calc.baseImponibleVes(),
                    calc.exentoVes());
        }

        BigDecimal baseImponible = BigDecimal.ZERO.setScale(2);
        BigDecimal exento = BigDecimal.ZERO.setScale(2);
        for (DetallePedido detalle : detalles) {
            if (detalle == null || detalle.getAplicaIva() == null || detalle.getCantidad() <= 0
                    || detalle.getPrecioDecimal() == null || detalle.getPrecioDecimal().signum() < 0) {
                throw ErrorAplicacionException.validacion("Cada detalle debe incluir una condición fiscal válida.");
            }
            BigDecimal importe = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(detalle.getCantidad()));
            if (detalle.getAplicaIva()) {
                baseImponible = baseImponible.add(importe);
            } else {
                exento = exento.add(importe);
            }
        }
        return calcular(baseImponible, exento, ivaPorcentaje, tasaCambio);
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

        BigDecimal baseUsd = baseImponibleUsd != null ? baseImponibleUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal exUsd = exentoUsd != null ? exentoUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal subUsd = baseUsd.add(exUsd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaPct = ivaPorcentaje != null ? ivaPorcentaje.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal tasa = tasaCambio != null && tasaCambio.compareTo(BigDecimal.ZERO) > 0 ? tasaCambio : new BigDecimal("36.5000");

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

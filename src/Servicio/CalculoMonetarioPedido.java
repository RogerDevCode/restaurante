package Servicio;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cálculo puro para líneas USD/VES. La moneda primaria conserva su importe;
 * el valor en la otra moneda es únicamente una referencia.
 */
public final class CalculoMonetarioPedido {
    private static final BigDecimal CERO = new BigDecimal("0.00");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal IVA_MAXIMO = new BigDecimal("100.00");
    private static final int ESCALA_MONEDA = 2;
    private static final int ESCALA_INTERMEDIA = 8;
    private static final int ESCALA_TASA = 4;
    private static final int DIGITOS_ENTEROS_PRECIO = 12; // DECIMAL(14,2)
    private static final int DIGITOS_ENTEROS_TASA = 8; // DECIMAL(12,4)

    private CalculoMonetarioPedido() {
    }

    public static CalculoLinea calcularLinea(
            BigDecimal precioBase,
            int cantidad,
            String monedaBase,
            Boolean aplicaIva,
            Boolean precioIncluyeIva,
            BigDecimal ivaPorcentaje,
            BigDecimal tasa) {
        BigDecimal precio = validarPrecio(precioBase);
        validarCantidad(cantidad);
        String moneda = validarMoneda(monedaBase);
        BigDecimal tasaNormalizada = validarTasa(tasa);
        BigDecimal porcentaje = validarIva(ivaPorcentaje);

        if (aplicaIva == null) {
            throw ErrorAplicacionException.validacion(
                    "No se puede calcular: la condición de aplicación de IVA de la línea no está clasificada.");
        }
        if (precioIncluyeIva == null) {
            throw ErrorAplicacionException.validacion(
                    "No se puede calcular: la condición de IVA incluido de la línea no está clasificada.");
        }
        if (!aplicaIva && precioIncluyeIva) {
            throw ErrorAplicacionException.validacion(
                    "Un producto exento no puede tener la condición de precio con IVA incluido.");
        }

        BigDecimal totalFuente = precio.multiply(BigDecimal.valueOf(cantidad)).setScale(ESCALA_MONEDA);
        BigDecimal baseFuente;
        BigDecimal exentoFuente;
        BigDecimal ivaFuente;

        if (!aplicaIva) {
            baseFuente = CERO;
            exentoFuente = totalFuente;
            ivaFuente = CERO;
        } else {
            exentoFuente = CERO;
            if (precioIncluyeIva) {
                baseFuente = totalFuente.multiply(CIEN)
                        .divide(CIEN.add(porcentaje), ESCALA_MONEDA, RoundingMode.HALF_UP);
                ivaFuente = totalFuente.subtract(baseFuente).setScale(ESCALA_MONEDA);
            } else {
                baseFuente = totalFuente;
                ivaFuente = baseFuente.multiply(porcentaje)
                        .divide(CIEN, ESCALA_MONEDA, RoundingMode.HALF_UP);
                totalFuente = baseFuente.add(ivaFuente).setScale(ESCALA_MONEDA);
            }
        }

        BigDecimal baseVes;
        BigDecimal exentoVes;
        BigDecimal ivaVes;
        BigDecimal totalVes;
        BigDecimal totalUsdReferencia;

        if ("VES".equals(moneda)) {
            baseVes = baseFuente;
            exentoVes = exentoFuente;
            ivaVes = ivaFuente;
            totalVes = totalFuente;
            totalUsdReferencia = convertirADolaresReferencia(totalVes, tasaNormalizada);
        } else {
            baseVes = convertirABolivares(baseFuente, tasaNormalizada);
            exentoVes = convertirABolivares(exentoFuente, tasaNormalizada);
            totalVes = convertirABolivares(totalFuente, tasaNormalizada);
            // Derivar el IVA del total redondeado manteniendo la identidad base + exento + IVA = total en VES.
            ivaVes = totalVes.subtract(baseVes).subtract(exentoVes).setScale(ESCALA_MONEDA);
            totalUsdReferencia = totalFuente;
        }

        validarMontoCalculado(baseVes);
        validarMontoCalculado(exentoVes);
        validarMontoCalculado(ivaVes);
        validarMontoCalculado(totalVes);
        validarMontoCalculado(totalUsdReferencia);

        BigDecimal totalUsdAditivoLinea = ("VES".equalsIgnoreCase(moneda))
                ? convertirADolaresReferencia(baseVes.add(exentoVes), tasaNormalizada).add(convertirADolaresReferencia(ivaVes, tasaNormalizada))
                : totalUsdReferencia;

        BigDecimal ajusteCambioBs = totalVes.subtract(
                totalUsdAditivoLinea.multiply(tasaNormalizada).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP))
                .setScale(ESCALA_MONEDA);

        return new CalculoLinea(
                moneda,
                baseFuente,
                exentoFuente,
                ivaFuente,
                totalFuente,
                baseVes,
                exentoVes,
                ivaVes,
                totalVes,
                totalUsdReferencia,
                ajusteCambioBs);
    }

    public static CalculoPedido calcularPedido(List<DetallePedido> detalles, BigDecimal tasa) {
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir detalles para calcular sus importes.");
        }
        BigDecimal tasaNormalizada = validarTasa(tasa);
        List<CalculoLinea> lineas = new ArrayList<>(detalles.size());
        BigDecimal baseVes = CERO;
        BigDecimal exentoVes = CERO;
        BigDecimal ivaVes = CERO;
        BigDecimal totalVes = CERO;
        BigDecimal totalUsdReferencia = CERO;

        for (DetallePedido detalle : detalles) {
            if (detalle == null) {
                throw ErrorAplicacionException.validacion("El pedido contiene una línea nula.");
            }
            CalculoLinea linea = calcularLinea(
                    detalle.getPrecioDecimal(),
                    detalle.getCantidad(),
                    detalle.getMonedaBase(),
                    detalle.getAplicaIva(),
                    detalle.getPrecioIncluyeIva(),
                    detalle.getIvaPorcentaje(),
                    tasaNormalizada);
            lineas.add(linea);
            baseVes = baseVes.add(linea.baseImponibleVes());
            exentoVes = exentoVes.add(linea.exentoVes());
            ivaVes = ivaVes.add(linea.ivaVes());
            totalVes = totalVes.add(linea.totalVes());
            totalUsdReferencia = totalUsdReferencia.add(linea.totalUsdReferencia());
        }

        baseVes = baseVes.setScale(ESCALA_MONEDA);
        exentoVes = exentoVes.setScale(ESCALA_MONEDA);
        ivaVes = ivaVes.setScale(ESCALA_MONEDA);
        totalVes = totalVes.setScale(ESCALA_MONEDA);
        totalUsdReferencia = totalUsdReferencia.setScale(ESCALA_MONEDA);
        validarMontoCalculado(baseVes);
        validarMontoCalculado(exentoVes);
        validarMontoCalculado(ivaVes);
        validarMontoCalculado(totalVes);
        validarMontoCalculado(totalUsdReferencia);

        BigDecimal subtotalUsdAditivo = CERO;
        BigDecimal ivaUsdAditivo = CERO;
        for (CalculoLinea linea : lineas) {
            if ("VES".equals(linea.monedaBase())) {
                subtotalUsdAditivo = subtotalUsdAditivo.add(convertirADolaresReferencia(linea.baseImponibleVes().add(linea.exentoVes()), tasaNormalizada));
                ivaUsdAditivo = ivaUsdAditivo.add(convertirADolaresReferencia(linea.ivaVes(), tasaNormalizada));
            } else {
                subtotalUsdAditivo = subtotalUsdAditivo.add(linea.baseImponibleFuente()).add(linea.exentoFuente());
                ivaUsdAditivo = ivaUsdAditivo.add(linea.ivaFuente());
            }
        }
        BigDecimal totalUsdFinal = subtotalUsdAditivo.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP)
                .add(ivaUsdAditivo.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP)).setScale(ESCALA_MONEDA);

        BigDecimal ajusteCambioBs = totalVes.subtract(
                totalUsdFinal.multiply(tasaNormalizada).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP))
                .setScale(ESCALA_MONEDA);

        return new CalculoPedido(
                lineas, baseVes, exentoVes, ivaVes, totalVes, totalUsdReferencia, tasaNormalizada, ajusteCambioBs);
    }

    private static BigDecimal validarPrecio(BigDecimal precio) {
        if (precio == null) {
            throw ErrorAplicacionException.validacion("El precio fuente de la línea es obligatorio.");
        }
        BigDecimal normalizado = precio.stripTrailingZeros();
        if (precio.signum() <= 0) {
            throw ErrorAplicacionException.validacion("El precio fuente debe ser mayor que cero.");
        }
        if (normalizado.scale() > ESCALA_MONEDA || digitosEnteros(normalizado) > DIGITOS_ENTEROS_PRECIO) {
            throw ErrorAplicacionException.validacion("El precio fuente admite hasta dos decimales y 12 dígitos enteros.");
        }
        return precio.setScale(ESCALA_MONEDA, RoundingMode.UNNECESSARY);
    }

    private static void validarCantidad(int cantidad) {
        if (cantidad < 1 || cantidad > DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
            throw ErrorAplicacionException.validacion("La cantidad debe estar entre 1 y "
                    + DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + ".");
        }
    }

    private static String validarMoneda(String monedaBase) {
        if (monedaBase == null) {
            throw ErrorAplicacionException.validacion("La moneda base de la línea no está clasificada.");
        }
        String moneda = monedaBase.trim().toUpperCase(Locale.ROOT);
        if (!"USD".equals(moneda) && !"VES".equals(moneda)) {
            throw ErrorAplicacionException.validacion("La moneda base debe ser USD o VES.");
        }
        return moneda;
    }

    private static BigDecimal validarIva(BigDecimal ivaPorcentaje) {
        if (ivaPorcentaje == null) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA de la línea no está clasificado.");
        }
        BigDecimal normalizado = ivaPorcentaje.stripTrailingZeros();
        if (ivaPorcentaje.signum() < 0 || ivaPorcentaje.compareTo(IVA_MAXIMO) > 0
                || normalizado.scale() > ESCALA_MONEDA) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA debe estar entre 0 y 100, con hasta dos decimales.");
        }
        return ivaPorcentaje.setScale(ESCALA_MONEDA, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal validarTasa(BigDecimal tasa) {
        if (tasa == null || tasa.signum() <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio debe ser positiva.");
        }
        BigDecimal normalizado = tasa.stripTrailingZeros();
        if (normalizado.scale() > ESCALA_TASA || digitosEnteros(normalizado) > DIGITOS_ENTEROS_TASA) {
            throw ErrorAplicacionException.validacion("La tasa de cambio admite hasta cuatro decimales y ocho dígitos enteros.");
        }
        return tasa.setScale(ESCALA_TASA, RoundingMode.UNNECESSARY);
    }

    private static int digitosEnteros(BigDecimal valor) {
        return Math.max(0, valor.precision() - valor.scale());
    }

    private static BigDecimal convertirABolivares(BigDecimal montoFuente, BigDecimal tasa) {
        return montoFuente.multiply(tasa).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
    }

    private static BigDecimal convertirADolaresReferencia(BigDecimal montoVes, BigDecimal tasa) {
        return montoVes.divide(tasa, ESCALA_INTERMEDIA, RoundingMode.HALF_UP)
                .setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
    }

    private static void validarMontoCalculado(BigDecimal monto) {
        if (monto.signum() < 0 || monto.scale() > ESCALA_MONEDA || digitosEnteros(monto) > DIGITOS_ENTEROS_PRECIO) {
            throw ErrorAplicacionException.validacion("El importe calculado excede la precisión monetaria permitida.");
        }
    }

    public record CalculoLinea(
            String monedaBase,
            BigDecimal baseImponibleFuente,
            BigDecimal exentoFuente,
            BigDecimal ivaFuente,
            BigDecimal totalFuente,
            BigDecimal baseImponibleVes,
            BigDecimal exentoVes,
            BigDecimal ivaVes,
            BigDecimal totalVes,
            BigDecimal totalUsdReferencia,
            BigDecimal ajusteCambioBs) {

        public BigDecimal baseImponibleUsd(BigDecimal tasa) {
            if ("VES".equals(monedaBase)) {
                return convertirADolaresReferencia(baseImponibleVes, tasa);
            }
            return baseImponibleFuente;
        }

        public BigDecimal exentoUsd(BigDecimal tasa) {
            if ("VES".equals(monedaBase)) {
                return convertirADolaresReferencia(exentoVes, tasa);
            }
            return exentoFuente;
        }

        public BigDecimal ivaUsd(BigDecimal tasa) {
            if ("VES".equals(monedaBase)) {
                return convertirADolaresReferencia(ivaVes, tasa);
            }
            return ivaFuente;
        }

        public BigDecimal subtotalUsd(BigDecimal tasa) {
            return baseImponibleUsd(tasa).add(exentoUsd(tasa)).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }

        public BigDecimal subtotalVes() {
            return baseImponibleVes.add(exentoVes).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }

        public BigDecimal totalUsd(BigDecimal tasa) {
            return subtotalUsd(tasa).add(ivaUsd(tasa)).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }
    }

    public record CalculoPedido(
            List<CalculoLinea> lineas,
            BigDecimal baseImponibleVes,
            BigDecimal exentoVes,
            BigDecimal ivaVes,
            BigDecimal totalVes,
            BigDecimal totalUsdReferencia,
            BigDecimal tasa,
            BigDecimal ajusteCambioBs) {
        public CalculoPedido {
            lineas = List.copyOf(lineas);
        }

        public BigDecimal totalUsd() {
            // Bajo política D8: se prioriza la aditividad de líneas (subtotalUsd + ivaUsd = totalUsd) sobre la división directa.
            return subtotalUsd().add(ivaUsd()).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }

        public BigDecimal subtotalVes() {
            return baseImponibleVes.add(exentoVes).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }

        public BigDecimal baseImponibleUsd() {
            BigDecimal base = CERO;
            for (CalculoLinea linea : lineas) {
                if ("VES".equals(linea.monedaBase())) {
                    base = base.add(convertirADolaresReferencia(linea.baseImponibleVes(), tasa));
                } else {
                    base = base.add(linea.baseImponibleFuente());
                }
            }
            return base.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }

        public BigDecimal exentoUsd() {
            BigDecimal exento = CERO;
            for (CalculoLinea linea : lineas) {
                if ("VES".equals(linea.monedaBase())) {
                    exento = exento.add(convertirADolaresReferencia(linea.exentoVes(), tasa));
                } else {
                    exento = exento.add(linea.exentoFuente());
                }
            }
            return exento.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }

        public BigDecimal subtotalUsd() {
            BigDecimal subtotal = CERO;
            for (CalculoLinea linea : lineas) {
                if ("VES".equals(linea.monedaBase())) {
                    BigDecimal baseUsd = convertirADolaresReferencia(linea.baseImponibleVes().add(linea.exentoVes()), tasa);
                    subtotal = subtotal.add(baseUsd);
                } else {
                    subtotal = subtotal.add(linea.baseImponibleFuente()).add(linea.exentoFuente());
                }
            }
            return subtotal.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }

        public BigDecimal ivaUsd() {
            BigDecimal iva = CERO;
            for (CalculoLinea linea : lineas) {
                if ("VES".equals(linea.monedaBase())) {
                    BigDecimal ivaUsdLinea = convertirADolaresReferencia(linea.ivaVes(), tasa);
                    iva = iva.add(ivaUsdLinea);
                } else {
                    iva = iva.add(linea.ivaFuente());
                }
            }
            return iva.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        }
    }
}

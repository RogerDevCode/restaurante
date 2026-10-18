package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un tramo del cobro de un pedido, con su medio, moneda, monto ingresado, recibido y cambio devuelto (Task 7).
 */
public record PagoPedido(
        String metodo,
        String moneda,
        BigDecimal monto,
        BigDecimal montoRecibido,
        BigDecimal montoAplicado,
        BigDecimal cambioDevuelto
) {
    public PagoPedido(String metodo, String moneda, BigDecimal monto) {
        this(metodo, moneda, monto, monto, monto, BigDecimal.ZERO);
    }

    public PagoPedido {
        if (metodo == null || metodo.isBlank()) {
            throw ErrorAplicacionException.validacion("Seleccione un medio de pago.");
        }
        metodo = metodo.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("EFECTIVO_BS", "EFECTIVO_USD", "TRANSFERENCIA", "TARJETA", "PAGO_MOVIL", "CORTESIA")
                .contains(metodo)) {
            throw ErrorAplicacionException.validacion("El medio de pago no es válido para el desglose.");
        }
        if (moneda == null || moneda.isBlank()) {
            throw ErrorAplicacionException.validacion("Seleccione la moneda del pago.");
        }
        moneda = moneda.trim().toUpperCase(java.util.Locale.ROOT);
        if (!moneda.equals("VES") && !moneda.equals("USD")) {
            throw ErrorAplicacionException.validacion("La moneda debe ser Bs. (VES) o USD.");
        }
        if ("EFECTIVO_BS".equals(metodo) && !"VES".equals(moneda)
                || "EFECTIVO_USD".equals(metodo) && !"USD".equals(moneda)) {
            throw ErrorAplicacionException.validacion("La moneda no corresponde al medio de efectivo seleccionado.");
        }
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2) {
            throw ErrorAplicacionException.validacion("El monto debe ser mayor que cero y tener hasta dos decimales.");
        }
        monto = monto.setScale(2, RoundingMode.UNNECESSARY);

        if (montoAplicado == null) {
            montoAplicado = monto;
        } else {
            montoAplicado = montoAplicado.setScale(2, RoundingMode.HALF_UP);
        }
        if (montoRecibido == null) {
            montoRecibido = monto;
        } else {
            montoRecibido = montoRecibido.setScale(2, RoundingMode.HALF_UP);
        }
        if (cambioDevuelto == null) {
            cambioDevuelto = montoRecibido.subtract(montoAplicado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        } else {
            cambioDevuelto = cambioDevuelto.setScale(2, RoundingMode.HALF_UP);
        }

        if (montoRecibido.compareTo(montoAplicado.add(cambioDevuelto)) != 0) {
            throw ErrorAplicacionException.validacion("El monto recibido debe ser igual a la suma de monto aplicado más cambio devuelto.");
        }
        if (cambioDevuelto.signum() > 0 && !"EFECTIVO_BS".equals(metodo) && !"EFECTIVO_USD".equals(metodo)) {
            throw ErrorAplicacionException.validacion("El cambio devuelto solo se permite en pagos en efectivo.");
        }
    }

    public BigDecimal equivalenteBs(BigDecimal tasa) {
        validarTasa(tasa);
        BigDecimal base = montoAplicado != null ? montoAplicado : monto;
        return "USD".equals(moneda)
                ? base.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
                : base.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal equivalenteUsd(BigDecimal tasa) {
        validarTasa(tasa);
        BigDecimal base = montoAplicado != null ? montoAplicado : monto;
        return "USD".equals(moneda)
                ? base.setScale(2, RoundingMode.HALF_UP)
                : base.divide(tasa, 2, RoundingMode.HALF_UP);
    }

    private static void validarTasa(BigDecimal tasa) {
        if (tasa == null || tasa.signum() <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio guardada en el pedido no es válida.");
        }
    }
}

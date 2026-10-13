package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Un tramo del cobro de un pedido, con su medio, moneda y monto ingresado. */
public record PagoPedido(String metodo, String moneda, BigDecimal monto) {
    public PagoPedido {
        if (metodo == null || metodo.isBlank()) {
            throw ErrorAplicacionException.validacion("Seleccione un medio de pago.");
        }
        metodo = metodo.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("EFECTIVO_BS", "EFECTIVO_USD", "TRANSFERENCIA", "TARJETA", "PAGO_MOVIL")
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
    }

    public BigDecimal equivalenteBs(BigDecimal tasa) {
        validarTasa(tasa);
        return "USD".equals(moneda)
                ? monto.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
                : monto.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal equivalenteUsd(BigDecimal tasa) {
        validarTasa(tasa);
        return "USD".equals(moneda)
                ? monto.setScale(2, RoundingMode.HALF_UP)
                : monto.divide(tasa, 2, RoundingMode.HALF_UP);
    }

    private static void validarTasa(BigDecimal tasa) {
        if (tasa == null || tasa.signum() <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio guardada en el pedido no es válida.");
        }
    }
}

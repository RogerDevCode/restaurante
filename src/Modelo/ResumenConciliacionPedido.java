package Modelo;

import java.math.BigDecimal;

/**
 * Resumen de conciliación financiera de una comanda al cierre y cobro (Task 7).
 * Garantiza la regla (P2-D): pagado_bs_aplicado utiliza estrictamente el monto_bs
 * guardado en cada fila histórica de pedido_pagos sin revalorizar a tasa actual.
 */
public record ResumenConciliacionPedido(
        int idPedido,
        boolean cerrado,
        int versionPrecio,
        BigDecimal tasaCambioCongelada,
        BigDecimal totalUsdCongelado,
        BigDecimal totalBsCongelado,
        BigDecimal pagadoBsAplicado,
        BigDecimal pagadoUsdAplicado,
        BigDecimal saldoBsPendiente,
        BigDecimal saldoUsdPendiente,
        BigDecimal excedenteBs,
        BigDecimal excedenteUsd
) {
}

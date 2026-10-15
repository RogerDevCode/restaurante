package Modelo;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Resultado inmutable de propagar la actualización de un producto del catálogo
 * a los pedidos abiertos de la sala.
 */
public record ResultadoActualizacionPrecios(
        Platos productoActualizado,
        int pedidosRecalculados,
        BigDecimal totalAnteriorVes,
        BigDecimal totalNuevoVes,
        List<Integer> pedidosAfectados,
        int lineasNoEnlazadasOmitidas) {
    public ResultadoActualizacionPrecios {
        pedidosAfectados = pedidosAfectados != null ? List.copyOf(pedidosAfectados) : Collections.emptyList();
    }
}

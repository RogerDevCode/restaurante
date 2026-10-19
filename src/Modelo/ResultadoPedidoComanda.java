package Modelo;

/** Resultado de guardar un pedido y, opcionalmente, generar su salida incremental de cocina. */
public record ResultadoPedidoComanda(int idPedido, boolean guardado, ComandaMarcha marcha) {
    public ResultadoPedidoComanda {
        if (idPedido <= 0 || !guardado) {
            throw ErrorAplicacionException.validacion("El resultado debe representar un pedido guardado correctamente.");
        }
    }
}

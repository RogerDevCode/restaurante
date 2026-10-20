package Modelo;

import java.util.List;

/** Puerto de persistencia de pedidos, inyectable para probar el servicio sin MySQL. */
public interface PedidosRepositorio {
    int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles);

    /** Registra el pedido y su primera marcha de cocina de forma atómica. */
    default ResultadoPedidoComanda registrarPedidoCompletoConMarcha(Pedidos pedido, List<DetallePedido> detalles) {
        return registrarPedidoCompletoConMarcha(pedido, detalles, true);
    }

    default ResultadoPedidoComanda registrarPedidoCompletoConMarcha(
            Pedidos pedido, List<DetallePedido> detalles, boolean imprimirComanda) {
        int idPedido = registrarPedidoCompleto(pedido, detalles);
        if (!imprimirComanda) return new ResultadoPedidoComanda(idPedido, true, null);
        List<MovimientoComanda> movimientos = detalles.stream()
                .map(d -> MovimientoComanda.desde(MovimientoComanda.Accion.PREPARAR, d, d.getCantidad()))
                .toList();
        return new ResultadoPedidoComanda(idPedido, true,
                new ComandaMarcha(0, idPedido, 1, ComandaMarcha.Tipo.INICIAL, movimientos));
    }

    /** Actualiza un pedido pendiente sobrescribiendo sus totales y detalles. */
    boolean actualizarPedidoCompleto(int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos);

    /** Actualiza el pedido y devuelve solo la marcha incremental persistida; null si no hubo movimiento de cocina. */
    default ResultadoPedidoComanda actualizarPedidoCompletoConMarcha(
            int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos) {
        return actualizarPedidoCompletoConMarcha(idPedido, pedidoModificado, detallesNuevos, true);
    }

    default ResultadoPedidoComanda actualizarPedidoCompletoConMarcha(
            int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos, boolean imprimirComanda) {
        if (!actualizarPedidoCompleto(idPedido, pedidoModificado, detallesNuevos)) {
            throw ErrorAplicacionException.validacion("No se pudo actualizar el pedido pendiente.");
        }
        return new ResultadoPedidoComanda(idPedido, true, null);
    }

    /** Recupera una marcha exacta e inmutable para reimpresión. */
    default ComandaMarcha obtenerComandaMarcha(long idMarcha) {
        return null;
    }

    /** Devuelve las marchas imprimibles de un pedido ordenadas por secuencia. */
    default List<ComandaMarcha> listarComandasMarchas(int idPedido) {
        return List.of();
    }

    /** Devuelve el identificador pendiente de una mesa o cero cuando no tiene pedido abierto. */
    int verificarStado(int mesa, int idSala);

    /** Consulta un pedido existente; falla si el identificador no existe. */
    Pedidos verPedido(int idPedido);

    /** Devuelve los detalles guardados de un pedido. */
    List<DetallePedido> verPedidoDetalle(int idPedido);

    /** Consulta los detalles que se muestran al finalizar un pedido. */
    List<DetallePedido> finalizarPedido(int idPedido);

    /** Marca como finalizado un pedido existente. */
    boolean actualizarEstado(int idPedido);

    /** Marca como finalizado un pedido asociándolo a los datos del cliente. */
    default boolean actualizarEstadoConCliente(int idPedido, String clienteNombre, String clienteDocumento) {
        return actualizarEstadoConCliente(idPedido, clienteNombre, clienteDocumento, "EFECTIVO");
    }

    /** Marca como finalizado un pedido asociándolo a los datos del cliente y método de pago. */
    default boolean actualizarEstadoConCliente(int idPedido, String clienteNombre, String clienteDocumento, String metodoPago) {
        return actualizarEstado(idPedido);
    }

    /** Finaliza el pedido y registra la parte cobrada en efectivo en cada moneda (requerida para MIXTO). */
    default boolean actualizarEstadoConCliente(int idPedido, String clienteNombre, String clienteDocumento,
            String metodoPago, java.math.BigDecimal efectivoBs, java.math.BigDecimal efectivoUsd) {
        return actualizarEstadoConCliente(idPedido, clienteNombre, clienteDocumento, metodoPago);
    }

    /** Finaliza el pedido guardando cada medio de un pago MIXTO. */
    default boolean actualizarEstadoConCliente(int idPedido, String clienteNombre, String clienteDocumento,
            String metodoPago, java.util.List<PagoPedido> pagos) {
        return actualizarEstadoConCliente(idPedido, clienteNombre, clienteDocumento, metodoPago);
    }

    /** Devuelve el historial de pedidos ordenado por fecha descendente. */
    List<Pedidos> listarPedidos();

    /** Devuelve la cantidad de mesas ocupadas (con pedidos pendientes) agrupadas por id de sala. */
    default java.util.Map<Integer, Integer> contarMesasOcupadasPorSala() {
        return java.util.Collections.emptyMap();
    }

    /** Devuelve un mapa de numeroMesa -> mesoneroNombre (o ID) de los pedidos pendientes en una sala. */
    default java.util.Map<Integer, String> consultarMesonerosMesasPendientes(int idSala) {
        return java.util.Collections.emptyMap();
    }

    /** Purga pedidos finalizados anteriores a la cantidad de meses especificada. Retorna la cantidad eliminada. */
    default int purgarPedidosFinalizados(int mesesAnteriores) {
        return 0;
    }

    /** Marca como anulado un pedido existente. */
    default boolean anularPedido(int idPedido) {
        return false;
    }

    /** Marca como anulado un pedido existente y registra su auditoría de forma atómica. */
    default boolean anularPedidoConAuditoria(int idPedido, String motivo, String usuario) {
        return anularPedido(idPedido);
    }

    /**
     * Anula una factura cobrada registrando formalmente la reversión contable/contra-asientos
     * de los pagos existentes en la misma transacción y marcando el pedido como ANULADO.
     */
    default boolean anularFacturaCobradaConDevolucion(int idPedido, String motivo, String usuario) {
        return false;
    }

    /**
     * Cierra el servicio de la mesa congelando su cotización (Task 7).
     * @param idPedido identificador del pedido
     * @param versionEsperada versión de cotización esperada (o 0 si no se valida versión previa)
     * @return true si se cerró o ya estaba cerrada en esa versión (idempotente)
     */
    default boolean cerrarMesa(int idPedido, int versionEsperada) {
        return false;
    }

    default boolean cerrarMesa(int idPedido) {
        return cerrarMesa(idPedido, 0);
    }

    /**
     * Devuelve el resumen de conciliación financiera de una mesa cerrada o abierta (Task 7).
     */
    default ResumenConciliacionPedido resumenConciliacion(int idPedido) {
        return null;
    }

    /**
     * Acción de emergencia (P2-C): congela el precio de una línea de pedido abierto cuyo producto
     * ya no existe en el catálogo para excluirla del recálculo por tasa o catálogo.
     */
    default boolean congelarLinea(int idDetalle, String motivo, String actor, String idempotencyKey) {
        return false;
    }
}


package Servicio;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Reglas de aplicación para registrar un pedido completo. */
public final class PedidoServicio {
    private final PedidosRepositorio repositorio;

    public PedidoServicio(PedidosRepositorio repositorio) {
        if (repositorio == null) {
            throw ErrorAplicacionException.validacion("El repositorio de pedidos es obligatorio.");
        }
        this.repositorio = repositorio;
    }

    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        validar(pedido, detalles);
        return repositorio.registrarPedidoCompleto(pedido, detalles);
    }

    public boolean actualizarPedidoCompleto(int idPedido, Pedidos pedido, List<DetallePedido> detalles) {
        validar(pedido, detalles);
        return repositorio.actualizarPedidoCompleto(idPedido, pedido, detalles);
    }

    private void validar(Pedidos pedido, List<DetallePedido> detalles) {
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("El pedido es obligatorio.");
        }
        boolean rapido = pedido.esPedidoRapido();
        if ((!rapido && (pedido.getId_sala() <= 0 || pedido.getNum_mesa() <= 0))
                || (rapido && (pedido.getId_sala() != 0 || pedido.getNum_mesa() != 0))) {
            throw ErrorAplicacionException.validacion("La sala y el número de mesa deben ser válidos.");
        }
        if (pedido.getUsuario() == null || pedido.getUsuario().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El usuario que registra el pedido es obligatorio.");
        }
        validarImporte(pedido.getTotalDecimal(), "El total del pedido");
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
        BigDecimal totalDetalles = BigDecimal.ZERO.setScale(2);
        BigDecimal baseImponible = BigDecimal.ZERO.setScale(2);
        for (DetallePedido detalle : detalles) {
            if (detalle == null) {
                throw ErrorAplicacionException.validacion("El pedido contiene un detalle vacío.");
            }
            if (detalle.getNombre() == null || detalle.getNombre().trim().isEmpty()) {
                throw ErrorAplicacionException.validacion("Cada detalle debe incluir el nombre del plato.");
            }
            if (detalle.getCantidad() <= 0) {
                throw ErrorAplicacionException.validacion("La cantidad de cada plato debe ser mayor que cero.");
            }
            validarImporte(detalle.getPrecioDecimal(), "El precio de cada plato");
            BigDecimal precio = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal importe = precio.multiply(BigDecimal.valueOf(detalle.getCantidad()));
            totalDetalles = totalDetalles.add(importe);
            if (detalle.getAplicaIva() == null) {
                throw ErrorAplicacionException.validacion("Cada detalle debe indicar si aplica IVA.");
            }
            if (detalle.getAplicaIva()) {
                baseImponible = baseImponible.add(importe);
            }
        }

        BigDecimal ivaPorcentaje = pedido.getIvaPorcentaje();
        BigDecimal subtotalEsperado = totalDetalles;
        if (ivaPorcentaje == null || ivaPorcentaje.signum() < 0
                || ivaPorcentaje.compareTo(new BigDecimal("100.00")) > 0 || ivaPorcentaje.scale() > 2) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA debe estar entre 0.00% y 100.00%.");
        }
        BigDecimal ivaEsperado = ivaPorcentaje.signum() > 0
                ? baseImponible.multiply(ivaPorcentaje).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        if (pedido.getSubtotal() != null && pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP).compareTo(subtotalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
        }

        if (pedido.getIvaMonto() != null) {
            BigDecimal ivaMonto = pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP);
            if (ivaMonto.compareTo(ivaEsperado) != 0) {
                throw ErrorAplicacionException.validacion("El monto del IVA no coincide con la condición fiscal de los detalles.");
            }
        }

        BigDecimal totalPedido = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaAplicado = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : ivaEsperado;
        BigDecimal totalEsperadoExacto = subtotalEsperado.add(ivaAplicado).setScale(2, RoundingMode.HALF_UP);
        if (totalPedido.compareTo(totalEsperadoExacto) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles.");
        }
    }

    private void validarImporte(BigDecimal importe, String nombreCampo) {
        if (importe == null || importe.signum() <= 0 || importe.scale() > 2
                || importe.precision() - importe.scale() > 8) {
            throw ErrorAplicacionException.validacion(
                    nombreCampo + " debe ser positivo y caber en DECIMAL(10,2).");
        }
    }
}

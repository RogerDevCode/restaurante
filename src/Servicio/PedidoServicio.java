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
    private final PoliticaAcceso politica;

    public PedidoServicio(PedidosRepositorio repositorio) {
        this(repositorio, new PoliticaAcceso(new Modelo.Usuario(0, "Sistema", "admin", "admin", RolesSistema.ADMINISTRADOR)));
    }

    public PedidoServicio(PedidosRepositorio repositorio, PoliticaAcceso politica) {
        if (repositorio == null || politica == null) {
            throw ErrorAplicacionException.validacion("El repositorio y la política de pedidos son obligatorios.");
        }
        this.repositorio = repositorio;
        this.politica = politica;
    }

    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        exigir(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS);
        validar(pedido, detalles);
        return repositorio.registrarPedidoCompleto(pedido, detalles);
    }

    public Modelo.ResultadoPedidoComanda registrarPedidoCompletoConMarcha(Pedidos pedido, List<DetallePedido> detalles) {
        return registrarPedidoCompletoConMarcha(pedido, detalles, true);
    }

    public Modelo.ResultadoPedidoComanda registrarPedidoCompletoConMarcha(
            Pedidos pedido, List<DetallePedido> detalles, boolean imprimirComanda) {
        exigir(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS);
        validar(pedido, detalles);
        return repositorio.registrarPedidoCompletoConMarcha(pedido, detalles, imprimirComanda);
    }

    public boolean actualizarPedidoCompleto(int idPedido, Pedidos pedido, List<DetallePedido> detalles) {
        exigir(PoliticaAcceso.Accion.EDITAR_PEDIDOS_ABIERTOS);
        validar(pedido, detalles);
        return repositorio.actualizarPedidoCompleto(idPedido, pedido, detalles);
    }

    public Modelo.ResultadoPedidoComanda actualizarPedidoCompletoConMarcha(
            int idPedido, Pedidos pedido, List<DetallePedido> detalles) {
        return actualizarPedidoCompletoConMarcha(idPedido, pedido, detalles, true);
    }

    public Modelo.ResultadoPedidoComanda actualizarPedidoCompletoConMarcha(
            int idPedido, Pedidos pedido, List<DetallePedido> detalles, boolean imprimirComanda) {
        exigir(PoliticaAcceso.Accion.EDITAR_PEDIDOS_ABIERTOS);
        validar(pedido, detalles);
        return repositorio.actualizarPedidoCompletoConMarcha(idPedido, pedido, detalles, imprimirComanda);
    }

    public Modelo.ComandaMarcha obtenerComandaMarcha(long idMarcha) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_HISTORIAL);
        if (idMarcha <= 0) throw ErrorAplicacionException.validacion("El identificador de marcha debe ser válido.");
        return repositorio.obtenerComandaMarcha(idMarcha);
    }

    public List<Modelo.ComandaMarcha> listarComandasMarchas(int idPedido) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_HISTORIAL);
        if (idPedido <= 0) throw ErrorAplicacionException.validacion("El pedido debe ser válido para consultar sus marchas.");
        return repositorio.listarComandasMarchas(idPedido);
    }

    public boolean cerrarMesa(int idPedido, int versionEsperada) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        return repositorio.cerrarMesa(idPedido, versionEsperada);
    }

    public boolean cerrarMesa(int idPedido) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        return repositorio.cerrarMesa(idPedido, 0);
    }

    public Modelo.ResumenConciliacionPedido resumenConciliacion(int idPedido) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        return repositorio.resumenConciliacion(idPedido);
    }

    private void exigir(PoliticaAcceso.Accion accion) {
        if (!politica.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
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
        if (pedido.getIvaPorcentaje() == null || pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El pedido debe tener un porcentaje de IVA válido mayor o igual a cero.");
        }
        validarImporte(pedido.getTotalDecimal(), "El total del pedido");
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
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

            if (detalle.getMonedaBase() == null || detalle.getMonedaBase().trim().isEmpty()) {
                throw ErrorAplicacionException.validacion("El producto '" + detalle.getNombre() + "' debe tener asignada su moneda base (USD o VES).");
            }
            if (detalle.getAplicaIva() == null) {
                throw ErrorAplicacionException.validacion("El producto '" + detalle.getNombre() + "' debe indicar si aplica IVA o es exento (fiscalidad incompleta).");
            }
            if (detalle.getPrecioIncluyeIva() == null) {
                throw ErrorAplicacionException.validacion("El producto '" + detalle.getNombre() + "' debe indicar si el precio incluye IVA.");
            }
            if (detalle.getIvaPorcentaje() == null) {
                detalle.setIvaPorcentaje(Boolean.TRUE.equals(detalle.getAplicaIva())
                        ? pedido.getIvaPorcentaje()
                        : BigDecimal.ZERO.setScale(2));
            }
        }

        BigDecimal tasa = pedido.getTasaCambio();
        if (tasa == null || tasa.signum() <= 0) {
            throw ErrorAplicacionException.validacion("El pedido debe tener una tasa de cambio válida mayor a cero.");
        }

        CalculoMonetarioPedido.CalculoPedido resultado = CalculoMonetarioPedido.calcularPedido(detalles, tasa);

        BigDecimal subtotalEsperado = resultado.subtotalUsd();
        BigDecimal ivaEsperado = resultado.ivaUsd();
        BigDecimal totalEsperadoExacto = resultado.totalUsd();

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

    public boolean congelarLinea(int idDetalle, String motivo, String actor, String idempotencyKey) {
        return repositorio.congelarLinea(idDetalle, motivo, actor, idempotencyKey);
    }
}


package Servicio;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import java.util.List;
import java.util.Map;

/** Consultas y finalización de pedidos con permisos explícitos por caso de uso. */
public final class ConsultaPedidosServicio {
    private final PedidosRepositorio repositorio;
    private final PoliticaAcceso politica;
    private final Modelo.AuditoriaPedidosDao auditoriaDao;

    public ConsultaPedidosServicio(PedidosRepositorio repositorio, PoliticaAcceso politica) {
        this(repositorio, politica, new Modelo.AuditoriaPedidosDao());
    }

    public ConsultaPedidosServicio(PedidosRepositorio repositorio, PoliticaAcceso politica, Modelo.AuditoriaPedidosDao auditoriaDao) {
        if (repositorio == null || politica == null) {
            throw ErrorAplicacionException.validacion("El repositorio y la política de pedidos son obligatorios.");
        }
        this.repositorio = repositorio;
        this.politica = politica;
        this.auditoriaDao = auditoriaDao;
    }

    public int buscarPedidoPendiente(int mesa, int idSala) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_OCUPACION_MESAS);
        if (mesa <= 0 || idSala <= 0) {
            throw ErrorAplicacionException.validacion("La sala y mesa deben ser válidas para buscar pedidos.");
        }
        int idPedido = repositorio.verificarStado(mesa, idSala);
        if (idPedido < 0) {
            throw new ErrorAplicacionException(
                    "La consulta devolvió un identificador de pedido no válido.",
                    new IllegalStateException("verificarStado devolvió un ID negativo."));
        }
        return idPedido;
    }

    public List<Pedidos> listarHistorial() {
        exigir(PoliticaAcceso.Accion.CONSULTAR_HISTORIAL);
        return repositorio.listarPedidos();
    }

    public Pedidos obtenerPedido(int idPedido) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PEDIDOS_PENDIENTES);
        validarId(idPedido);
        Pedidos pedido = repositorio.verPedido(idPedido);
        if (pedido == null) {
            throw new ErrorAplicacionException(
                    "No se encontró el pedido solicitado.",
                    new IllegalStateException("El repositorio devolvió null para el ID " + idPedido + "."));
        }
        return pedido;
    }

    public List<DetallePedido> obtenerDetalles(int idPedido) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PEDIDOS_PENDIENTES);
        validarId(idPedido);
        List<DetallePedido> detalles = repositorio.verPedidoDetalle(idPedido);
        if (detalles == null) {
            throw new ErrorAplicacionException(
                    "No se pudieron cargar los detalles del pedido.",
                    new IllegalStateException("El repositorio devolvió null para los detalles del pedido "
                            + idPedido + "."));
        }
        return detalles;
    }

    public boolean finalizar(int idPedido) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        validarId(idPedido);
        return repositorio.actualizarEstado(idPedido);
    }

    public boolean finalizarConCliente(int idPedido, String clienteNombre, String clienteDocumento) {
        return finalizarConCliente(idPedido, clienteNombre, clienteDocumento, "EFECTIVO");
    }

    public boolean finalizarConCliente(int idPedido, String clienteNombre, String clienteDocumento, String metodoPago) {
        return finalizarConCliente(idPedido, clienteNombre, clienteDocumento, metodoPago, null, null);
    }

    public boolean finalizarConCliente(int idPedido, String clienteNombre, String clienteDocumento,
            String metodoPago, java.math.BigDecimal efectivoBs, java.math.BigDecimal efectivoUsd) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        validarId(idPedido);
        return repositorio.actualizarEstadoConCliente(idPedido, clienteNombre, clienteDocumento,
                metodoPago, efectivoBs, efectivoUsd);
    }

    public boolean finalizarConCliente(int idPedido, String clienteNombre, String clienteDocumento,
            String metodoPago, java.util.List<Modelo.PagoPedido> pagos) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        validarId(idPedido);
        return repositorio.actualizarEstadoConCliente(idPedido, clienteNombre, clienteDocumento, metodoPago, pagos);
    }

    public boolean cerrarMesa(int idPedido, int versionEsperada) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        validarId(idPedido);
        return repositorio.cerrarMesa(idPedido, versionEsperada);
    }

    public boolean cerrarMesa(int idPedido) {
        return cerrarMesa(idPedido, 0);
    }

    public Modelo.ResumenConciliacionPedido resumenConciliacion(int idPedido) {
        exigir(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        validarId(idPedido);
        return repositorio.resumenConciliacion(idPedido);
    }

    public boolean anular(int idPedido, String motivo, String usuario) {
        exigir(PoliticaAcceso.Accion.CANCELAR_CUENTA_PENDIENTE);
        validarId(idPedido);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar el motivo de la anulación del pedido.");
        }
        String usr = (usuario != null && !usuario.isBlank()) ? usuario.trim() : "Sistema";
        return repositorio.anularPedidoConAuditoria(idPedido, motivo.trim(), usr);
    }

    public boolean anularFacturaCobradaConDevolucion(int idPedido, String motivo, String usuario) {
        exigir(PoliticaAcceso.Accion.ANULAR_FACTURA_COBRADA);
        validarId(idPedido);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar el motivo de la anulación de la factura.");
        }
        String usr = (usuario != null && !usuario.isBlank()) ? usuario.trim() : "Sistema";
        return repositorio.anularFacturaCobradaConDevolucion(idPedido, motivo.trim(), usr);
    }

    public void registrarAuditoria(int idPedido, String accion, String motivo, String usuario) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_HISTORIAL);
        validarId(idPedido);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar el motivo de la acción a registrar.");
        }
        if (auditoriaDao != null) {
            boolean exito = auditoriaDao.registrar(new Modelo.AuditoriaPedido(idPedido, accion, motivo.trim(), usuario));
            if (!exito) {
                throw ErrorAplicacionException.validacion("No se pudo registrar el evento de auditoría. Acción cancelada por seguridad.");
            }
        }
    }

    public List<Modelo.AuditoriaPedido> obtenerAuditoriaPedido(int idPedido) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_HISTORIAL);
        validarId(idPedido);
        return auditoriaDao != null ? auditoriaDao.listarPorPedido(idPedido) : List.of();
    }

    public Map<Integer, Integer> contarMesasOcupadasPorSala() {
        exigir(PoliticaAcceso.Accion.CONSULTAR_SALAS);
        return repositorio.contarMesasOcupadasPorSala();
    }

    public Map<Integer, String> consultarMesonerosMesasPendientes(int idSala) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_SALAS);
        return repositorio.consultarMesonerosMesasPendientes(idSala);
    }

    public int purgarPedidosFinalizados(int mesesAnteriores) {
        exigir(PoliticaAcceso.Accion.PURGAR_HISTORIAL);
        if (!politica.esAdministrador()) {
            throw ErrorAplicacionException.validacion("Solo los administradores pueden purgar el historial.");
        }
        if (mesesAnteriores < 1) {
            throw ErrorAplicacionException.validacion("El período de retención debe ser de al menos 1 mes.");
        }
        return repositorio.purgarPedidosFinalizados(mesesAnteriores);
    }

    private void exigir(PoliticaAcceso.Accion accion) {
        if (!politica.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
    }

    private void validarId(int idPedido) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
        }
    }
}

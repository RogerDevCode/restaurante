package Servicio;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Coordina la consulta de datos, la composición y la apertura del PDF de un pedido. */
public final class PedidoPdfServicio {
    private final ConsultaPedido pedidos;
    private final ConsultaDetalles detalles;
    private final ConsultaConfiguracion configuracion;
    private final GeneradorPdfPedido generador;
    private final AbridorPdf abridor;
    private final AbridorPdf visor;

    public PedidoPdfServicio(
            ConsultaPedido pedidos,
            ConsultaDetalles detalles,
            ConsultaConfiguracion configuracion,
            GeneradorPdfPedido generador,
            AbridorPdf abridor) {
        this(pedidos, detalles, configuracion, generador, abridor, ServicioImpresionTicket::abrirVisor);
    }

    public PedidoPdfServicio(
            ConsultaPedido pedidos,
            ConsultaDetalles detalles,
            ConsultaConfiguracion configuracion,
            GeneradorPdfPedido generador,
            AbridorPdf abridor,
            AbridorPdf visor) {
        if (pedidos == null || detalles == null || configuracion == null || generador == null || abridor == null || visor == null) {
            throw ErrorAplicacionException.validacion("Las dependencias para generar el PDF del pedido son obligatorias.");
        }
        this.pedidos = pedidos;
        this.detalles = detalles;
        this.configuracion = configuracion;
        this.generador = generador;
        this.abridor = abridor;
        this.visor = visor;
    }

    public void generar(int idPedido) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para generar el PDF.");
        }
        Pedidos pedido = pedidos.obtener(idPedido);
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("No existe el pedido " + idPedido + ".");
        }
        Config datosConfiguracion = configuracion.obtener();
        if (datosConfiguracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa para generar el PDF.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        List<DetallePedido> lineas = detalles.obtener(idPedido);
        if (lineas == null || lineas.isEmpty()) {
            throw ErrorAplicacionException.validacion(
                    "El pedido " + idPedido + " no tiene detalles para generar el PDF.");
        }
        Path archivo = generador.generar(pedido, datosConfiguracion, lineas);
        try {
            abridor.abrir(archivo);
        } catch (IOException error) {
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir automáticamente.", error);
        } catch (RuntimeException error) {
            if (error instanceof ErrorAplicacionException) {
                throw error;
            }
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir automáticamente.", error);
        }
    }

    public void reimprimir(int idPedido) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para generar el PDF.");
        }
        Pedidos pedido = pedidos.obtener(idPedido);
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("No existe el pedido " + idPedido + ".");
        }
        Config datosConfiguracion = configuracion.obtener();
        if (datosConfiguracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa para generar el PDF.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        List<DetallePedido> lineas = detalles.obtener(idPedido);
        if (lineas == null || lineas.isEmpty()) {
            throw ErrorAplicacionException.validacion(
                    "El pedido " + idPedido + " no tiene detalles para generar el PDF.");
        }
        Path archivo = generador.generarConTimestamp(pedido, datosConfiguracion, lineas);
        try {
            abridor.abrir(archivo);
        } catch (IOException error) {
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir automáticamente.", error);
        } catch (RuntimeException error) {
            if (error instanceof ErrorAplicacionException) {
                throw error;
            }
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir automáticamente.", error);
        }
    }

    public void previsualizar(int idPedido) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para generar el PDF.");
        }
        Pedidos pedido = pedidos.obtener(idPedido);
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("No existe el pedido " + idPedido + ".");
        }
        Config datosConfiguracion = configuracion.obtener();
        if (datosConfiguracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa para generar el PDF.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        List<DetallePedido> lineas = detalles.obtener(idPedido);
        if (lineas == null || lineas.isEmpty()) {
            throw ErrorAplicacionException.validacion(
                    "El pedido " + idPedido + " no tiene detalles para generar el PDF.");
        }
        Path archivo = generador.generarConTimestamp(pedido, datosConfiguracion, lineas);
        try {
            visor.abrir(archivo);
        } catch (IOException error) {
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir en el visor.", error);
        } catch (RuntimeException error) {
            if (error instanceof ErrorAplicacionException) {
                throw error;
            }
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir en el visor.", error);
        }
    }

    @FunctionalInterface
    public interface ConsultaPedido {
        Pedidos obtener(int idPedido);
    }

    @FunctionalInterface
    public interface ConsultaDetalles {
        List<DetallePedido> obtener(int idPedido);
    }

    @FunctionalInterface
    public interface ConsultaConfiguracion {
        Config obtener();
    }

    @FunctionalInterface
    public interface AbridorPdf {
        void abrir(Path archivo) throws IOException;

        static AbridorPdf porDefecto() {
            return ServicioImpresionTicket::procesarSalida;
        }
    }
}

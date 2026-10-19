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
    private final ProcesadorSalidaTicket procesadorSalida;

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
        this(pedidos, detalles, configuracion, generador, abridor, visor,
                archivo -> adaptarResultadoCompatibilidad(abridor, archivo));
    }

    public PedidoPdfServicio(
            ConsultaPedido pedidos,
            ConsultaDetalles detalles,
            ConsultaConfiguracion configuracion,
            GeneradorPdfPedido generador,
            AbridorPdf abridor,
            AbridorPdf visor,
            ProcesadorSalidaTicket procesadorSalida) {
        if (pedidos == null || detalles == null || configuracion == null || generador == null
                || abridor == null || visor == null || procesadorSalida == null) {
            throw ErrorAplicacionException.validacion("Las dependencias para generar el PDF del pedido son obligatorias.");
        }
        this.pedidos = pedidos;
        this.detalles = detalles;
        this.configuracion = configuracion;
        this.generador = generador;
        this.abridor = abridor;
        this.visor = visor;
        this.procesadorSalida = procesadorSalida;
    }

    public boolean generar(int idPedido) {
        return abrirPdf(crearPdf(idPedido, false));
    }

    public ResultadoSalidaTicket generarConResultado(int idPedido) {
        return procesadorSalida.procesar(crearPdf(idPedido, false));
    }

    public ResultadoSalidaTicket reimprimirConResultado(int idPedido) {
        return procesadorSalida.procesar(crearPdf(idPedido, true));
    }

    private Path crearPdf(int idPedido, boolean timestamp) {
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
        return timestamp
                ? generador.generarConTimestamp(pedido, datosConfiguracion, lineas)
                : generador.generar(pedido, datosConfiguracion, lineas);
    }

    private boolean abrirPdf(Path archivo) {
        try {
            return abridor.abrir(archivo);
        } catch (IOException error) {
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir automáticamente.", error);
        } catch (RuntimeException error) {
            if (error instanceof ErrorAplicacionException) {
                throw error;
            }
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir automáticamente.", error);
        }
    }

    public boolean reimprimir(int idPedido) {
        return abrirPdf(crearPdf(idPedido, true));
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

    public boolean emitirPreFactura(int idPedido) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para emitir la pre-factura.");
        }
        Pedidos pedido = pedidos.obtener(idPedido);
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("No existe el pedido " + idPedido + ".");
        }
        Config datosConfiguracion = configuracion.obtener();
        if (datosConfiguracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa para emitir la pre-factura.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        List<DetallePedido> lineas = detalles.obtener(idPedido);
        if (lineas == null || lineas.isEmpty()) {
            throw ErrorAplicacionException.validacion(
                    "El pedido " + idPedido + " no tiene consumos registrados para emitir la pre-factura.");
        }
        Path archivo = generador.generarPreFactura(pedido, datosConfiguracion, lineas);
        try {
            return abridor.abrir(archivo);
        } catch (IOException error) {
            throw new ErrorAplicacionException("La pre-factura se generó, pero no se pudo imprimir o abrir automáticamente.", error);
        } catch (RuntimeException error) {
            if (error instanceof ErrorAplicacionException) {
                throw error;
            }
            throw new ErrorAplicacionException("La pre-factura se generó, pero no se pudo imprimir o abrir automáticamente.", error);
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
        boolean abrir(Path archivo) throws IOException;

        static AbridorPdf porDefecto() {
            return ServicioImpresionTicket::procesarSalida;
        }
    }

    @FunctionalInterface
    public interface ProcesadorSalidaTicket {
        ResultadoSalidaTicket procesar(Path archivo);

        static ProcesadorSalidaTicket porDefecto() {
            return ServicioImpresionTicket::procesarSalidaResultado;
        }
    }

    private static ResultadoSalidaTicket adaptarResultadoCompatibilidad(AbridorPdf abridor, Path archivo) {
        try {
            boolean aceptado = abridor.abrir(archivo);
            if (!aceptado) {
                return ResultadoSalidaTicket.fallo("Canal de salida legado", "El canal de salida devolvió false.");
            }
            if (ServicioImpresionTicket.getModoGlobal() == Modelo.ModoSalidaTicket.VISOR_PDF) {
                return ResultadoSalidaTicket.visor("Visor Sistema", "PDF abierto en el visor.");
            }
            return ResultadoSalidaTicket.spooler("Canal de salida legado");
        } catch (IOException | RuntimeException ex) {
            return ResultadoSalidaTicket.fallo("Canal de salida legado", ex.getMessage());
        }
    }
}

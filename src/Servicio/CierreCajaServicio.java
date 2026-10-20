package Servicio;

import Modelo.CierreCaja;
import Modelo.Config;
import Modelo.CierreCajaDao;
import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio que coordina la consulta de datos del cierre de caja, la composición del PDF en 80 mm
 * y su impresión directa a la tickera térmica o previsualización interactiva.
 */
public class CierreCajaServicio {
    private static final Logger LOGGER = Logger.getLogger(CierreCajaServicio.class.getName());
    private final CierreCajaDao cierreDao;
    private final PedidoPdfServicio.ConsultaConfiguracion consultaConfig;
    private final GeneradorPdfCierre generador;
    private final PedidoPdfServicio.AbridorPdf visor;
    private final PedidoPdfServicio.ProcesadorSalidaTicket procesadorSalida;

    public CierreCajaServicio(
            CierreCajaDao cierreDao,
            PedidoPdfServicio.ConsultaConfiguracion consultaConfig,
            GeneradorPdfCierre generador) {
        this(cierreDao, consultaConfig, generador,
                ServicioImpresionTicket::procesarSalida,
                ServicioImpresionTicket::abrirVisor,
                PedidoPdfServicio.ProcesadorSalidaTicket.porDefecto());
    }

    public CierreCajaServicio(
            CierreCajaDao cierreDao,
            PedidoPdfServicio.ConsultaConfiguracion consultaConfig,
            GeneradorPdfCierre generador,
            PedidoPdfServicio.AbridorPdf impresor,
            PedidoPdfServicio.AbridorPdf visor) {
        this(cierreDao, consultaConfig, generador, impresor, visor,
                archivo -> adaptarResultadoCompatibilidad(impresor, archivo));
    }

    public CierreCajaServicio(
            CierreCajaDao cierreDao,
            PedidoPdfServicio.ConsultaConfiguracion consultaConfig,
            GeneradorPdfCierre generador,
            PedidoPdfServicio.AbridorPdf impresor,
            PedidoPdfServicio.AbridorPdf visor,
            PedidoPdfServicio.ProcesadorSalidaTicket procesadorSalida) {
        if (cierreDao == null || consultaConfig == null || generador == null || impresor == null
                || visor == null || procesadorSalida == null) {
            throw ErrorAplicacionException.validacion("Las dependencias del servicio de cierre de caja son obligatorias.");
        }
        this.cierreDao = cierreDao;
        this.consultaConfig = consultaConfig;
        this.generador = generador;
        this.visor = visor;
        this.procesadorSalida = procesadorSalida;
    }

    public record ResultadoCierre(Path archivo, ResultadoSalidaTicket salidaTicket) {
        public boolean impresoDirecto() {
            return salidaTicket != null
                    && salidaTicket.estado() == ResultadoSalidaTicket.EstadoSalida.ENVIADO_A_SPOOLER;
        }
    }

    /**
     * Genera el Cierre de Caja e imprime directamente a la tickera de 80 mm.
     */
    public ResultadoCierre imprimirCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return imprimirCierre(fecha, tipo, usuarioEmisor, null, null);
    }

    public ResultadoCierre imprimirCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs) {
        return imprimirCierre(fecha, tipo, usuarioEmisor, efectivoDeclaradoBs, null);
    }

    public ResultadoCierre imprimirCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs, java.math.BigDecimal efectivoDeclaradoUsd) {
        Config config = consultaConfig.obtener();
        if (config == null) {
            throw ErrorAplicacionException.validacion("No existe la configuración del restaurante para emitir el cierre de caja.");
        }
        CierreCaja cierre = cierreDao.consultarCierre(fecha, tipo, usuarioEmisor, config);
        if (efectivoDeclaradoBs != null) {
            cierre.setEfectivoDeclaradoBs(efectivoDeclaradoBs);
        }
        if (efectivoDeclaradoUsd != null) {
            cierre.setEfectivoDeclaradoUsd(efectivoDeclaradoUsd);
        }
        Path archivo = generador.generar(cierre, config);
        try {
            boolean guardado = cierreDao.guardarCierre(cierre, archivo != null ? archivo.toString() : null);
            if (!guardado) {
                throw new DataAccessException("No se pudo registrar la auditoría del cierre de caja en la base de datos.");
            }
        } catch (RuntimeException error) {
            eliminarPdfSiExiste(archivo, error);
            throw error;
        }
        ResultadoSalidaTicket salida;
        try {
            salida = procesadorSalida.procesar(archivo);
        } catch (RuntimeException ex) {
            salida = ResultadoSalidaTicket.fallo("Cierre de caja", ex.getMessage());
        }
        return new ResultadoCierre(archivo, salida);
    }

    private void eliminarPdfSiExiste(Path archivo, RuntimeException errorOriginal) {
        if (archivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException errorLimpieza) {
            errorOriginal.addSuppressed(errorLimpieza);
            LOGGER.log(Level.SEVERE, "No se pudo limpiar el PDF de cierre tras fallar su registro SQL: " + archivo,
                    errorLimpieza);
        }
    }

    /**
     * Genera el Cierre de Caja y abre la previsualización en pantalla (visor PDF).
     */
    public ResultadoCierre previsualizarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return previsualizarCierre(fecha, tipo, usuarioEmisor, null, null);
    }

    public ResultadoCierre previsualizarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs) {
        return previsualizarCierre(fecha, tipo, usuarioEmisor, efectivoDeclaradoBs, null);
    }

    public ResultadoCierre previsualizarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs, java.math.BigDecimal efectivoDeclaradoUsd) {
        Config config = consultaConfig.obtener();
        if (config == null) {
            throw ErrorAplicacionException.validacion("No existe la configuración del restaurante para previsualizar el cierre de caja.");
        }
        CierreCaja cierre = cierreDao.consultarCierre(fecha, tipo, usuarioEmisor, config);
        if (efectivoDeclaradoBs != null) {
            cierre.setEfectivoDeclaradoBs(efectivoDeclaradoBs);
        }
        if (efectivoDeclaradoUsd != null) {
            cierre.setEfectivoDeclaradoUsd(efectivoDeclaradoUsd);
        }
        Path archivo = generador.generar(cierre, config);
        // Las previsualizaciones no se insertan en la tabla de auditoría cierres_caja
        ResultadoSalidaTicket salida;
        try {
            salida = visor.abrir(archivo)
                    ? ResultadoSalidaTicket.visor("Visor Sistema", "PDF abierto para revisión.")
                    : ResultadoSalidaTicket.fallo("Visor Sistema", "El visor no aceptó el PDF.");
        } catch (IOException | RuntimeException ex) {
            salida = ResultadoSalidaTicket.fallo("Visor Sistema", ex.getMessage());
        }
        return new ResultadoCierre(archivo, salida);
    }

    private static ResultadoSalidaTicket adaptarResultadoCompatibilidad(
            PedidoPdfServicio.AbridorPdf impresor, Path archivo) {
        try {
            boolean aceptado = impresor.abrir(archivo);
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

    public CierreCaja obtenerDatosCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        Config config = consultaConfig.obtener();
        return cierreDao.consultarCierre(fecha, tipo, usuarioEmisor, config);
    }

    /**
     * Datos del Resumen de Ventas de un rango de fechas (solo lectura, sin persistir en cierres_caja).
     */
    public CierreCaja obtenerResumenVentas(String desde, String hasta, String usuarioEmisor) {
        Config config = consultaConfig.obtener();
        return cierreDao.consultarResumenVentas(desde, hasta, usuarioEmisor, config);
    }

    /**
     * Genera el Resumen de Ventas (documento informativo NO fiscal) de un rango de fechas y lo
     * entrega por el canal de salida configurado. No persiste en cierres_caja ni avanza correlativos.
     */
    public ResultadoCierre generarResumenVentas(String desde, String hasta, String usuarioEmisor) {
        Config config = consultaConfig.obtener();
        if (config == null) {
            throw ErrorAplicacionException.validacion("No existe la configuración del restaurante para generar el resumen de ventas.");
        }
        CierreCaja resumen = cierreDao.consultarResumenVentas(desde, hasta, usuarioEmisor, config);
        Path archivo = generador.generar(resumen, config);
        ResultadoSalidaTicket salida;
        try {
            salida = procesadorSalida.procesar(archivo);
        } catch (RuntimeException ex) {
            salida = ResultadoSalidaTicket.fallo("Resumen de Ventas", ex.getMessage());
        }
        return new ResultadoCierre(archivo, salida);
    }
}

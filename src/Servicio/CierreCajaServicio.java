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
    private final PedidoPdfServicio.AbridorPdf impresor;
    private final PedidoPdfServicio.AbridorPdf visor;

    public CierreCajaServicio(
            CierreCajaDao cierreDao,
            PedidoPdfServicio.ConsultaConfiguracion consultaConfig,
            GeneradorPdfCierre generador) {
        this(cierreDao, consultaConfig, generador,
                ServicioImpresionTicket::procesarSalida,
                ServicioImpresionTicket::abrirVisor);
    }

    public CierreCajaServicio(
            CierreCajaDao cierreDao,
            PedidoPdfServicio.ConsultaConfiguracion consultaConfig,
            GeneradorPdfCierre generador,
            PedidoPdfServicio.AbridorPdf impresor,
            PedidoPdfServicio.AbridorPdf visor) {
        if (cierreDao == null || consultaConfig == null || generador == null || impresor == null || visor == null) {
            throw ErrorAplicacionException.validacion("Las dependencias del servicio de cierre de caja son obligatorias.");
        }
        this.cierreDao = cierreDao;
        this.consultaConfig = consultaConfig;
        this.generador = generador;
        this.impresor = impresor;
        this.visor = visor;
    }

    /**
     * Genera el Cierre de Caja e imprime directamente a la tickera de 80 mm.
     */
    public Path imprimirCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return imprimirCierre(fecha, tipo, usuarioEmisor, null, null);
    }

    public Path imprimirCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs) {
        return imprimirCierre(fecha, tipo, usuarioEmisor, efectivoDeclaradoBs, null);
    }

    public Path imprimirCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs, java.math.BigDecimal efectivoDeclaradoUsd) {
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
        try {
            impresor.abrir(archivo);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("El ticket de cierre se generó, pero falló el envío a la impresora.", ex);
        }
        return archivo;
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
    public Path previsualizarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return previsualizarCierre(fecha, tipo, usuarioEmisor, null, null);
    }

    public Path previsualizarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs) {
        return previsualizarCierre(fecha, tipo, usuarioEmisor, efectivoDeclaradoBs, null);
    }

    public Path previsualizarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efectivoDeclaradoBs, java.math.BigDecimal efectivoDeclaradoUsd) {
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
        try {
            visor.abrir(archivo);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("El ticket de cierre se generó, pero no se pudo abrir en el visor.", ex);
        }
        return archivo;
    }

    public CierreCaja obtenerDatosCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        Config config = consultaConfig.obtener();
        return cierreDao.consultarCierre(fecha, tipo, usuarioEmisor, config);
    }
}

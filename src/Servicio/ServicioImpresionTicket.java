package Servicio;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

/**
 * Servicio encargado de gestionar la impresión directa de tickets de 80 mm en Windows 11.
 * Soporta:
 * 1. Detección automática y coincidencia difusa de la impresora (ej. Xprinter XP-80T / POS-80).
 * 2. Impresión directa y silenciosa a la tickera (modo IMPRIMIR por defecto).
 * 3. Fallback en cascada a la impresora predeterminada o visor en caso de desconexión física.
 */
public final class ServicioImpresionTicket {
    private static final Logger LOGGER = Logger.getLogger(ServicioImpresionTicket.class.getName());

    private ServicioImpresionTicket() {
    }

    /** Lista los nombres de todas las impresoras instaladas en el sistema. */
    public static List<String> listarImpresorasDisponibles() {
        List<String> nombres = new ArrayList<>();
        try {
            PrintService[] servicios = PrintServiceLookup.lookupPrintServices(null, null);
            for (PrintService ps : servicios) {
                if (ps != null && ps.getName() != null) {
                    nombres.add(ps.getName());
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudieron consultar las impresoras del sistema: " + ex.getMessage(), ex);
        }
        return nombres;
    }

    /** Retorna el nombre de la impresora predeterminada de Windows, o null si no hay. */
    public static String obtenerImpresoraPredeterminada() {
        try {
            PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
            return defaultService != null ? defaultService.getName() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Carga propiedades desde el archivo local .env si existe. */
    private static Properties cargarConfiguracionEnv() {
        Properties props = new Properties();
        try {
            String configuredEnvFile = System.getProperty("restaurante.env");
            Path envFile = Paths.get(configuredEnvFile == null ? ".env" : configuredEnvFile);
            if (configuredEnvFile == null && !Files.isRegularFile(envFile)) {
                envFile = Paths.get("..", ".env");
            }
            if (Files.isRegularFile(envFile)) {
                try (java.io.Reader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
                    Properties rawProps = new Properties();
                    rawProps.load(reader);
                    for (String key : rawProps.stringPropertyNames()) {
                        String cleanKey = key.replace("\uFEFF", "").trim();
                        props.setProperty(cleanKey, rawProps.getProperty(key));
                    }
                }
            }
        } catch (Exception ignored) { LOGGER.log(Level.FINE, "Error silenciado: " + ignored.getMessage(), ignored); 
        }
        return props;
    }

    /**
     * Retorna la impresora configurada por el usuario en .env o properties del sistema.
     * Si no está definida o es "DEFAULT", retorna "DEFAULT".
     */
    public static String obtenerImpresoraConfigurada() {
        String impresora = System.getProperty("IMPRESORA_TICKETS");
        if (impresora == null || impresora.isBlank()) {
            impresora = System.getenv("IMPRESORA_TICKETS");
        }
        if (impresora == null || impresora.isBlank()) {
            Properties envProps = cargarConfiguracionEnv();
            impresora = envProps.getProperty("IMPRESORA_TICKETS");
        }
        if (impresora != null && !impresora.isBlank()) {
            return impresora.trim();
        }
        return "DEFAULT";
    }

    /**
     * Retorna la acción configurada: "IMPRIMIR" (silencioso directo) o "ABRIR" (visor interactivo).
     * El valor por defecto es "IMPRIMIR" para impresión directa a la tickera.
     */
    public static String obtenerAccionConfigurada() {
        String accion = System.getProperty("ACCION_IMPRESION");
        if (accion == null || accion.isBlank()) {
            accion = System.getenv("ACCION_IMPRESION");
        }
        if (accion == null || accion.isBlank()) {
            Properties envProps = cargarConfiguracionEnv();
            accion = envProps.getProperty("ACCION_IMPRESION");
        }
        if (accion != null && !accion.isBlank()) {
            return accion.trim().toUpperCase();
        }
        return "IMPRIMIR";
    }

    /**
     * Resuelve el nombre real de la impresora instalada en el sistema mediante
     * coincidencia exacta o parcial (insensible a mayúsculas).
     */
    public static String resolverNombreRealImpresora(String nombreConfigurado) {
        if (nombreConfigurado == null || nombreConfigurado.isBlank() || "DEFAULT".equalsIgnoreCase(nombreConfigurado)) {
            return "DEFAULT";
        }
        List<String> disponibles = listarImpresorasDisponibles();
        String busqueda = nombreConfigurado.trim().toLowerCase();

        // 1. Coincidencia exacta
        for (String imp : disponibles) {
            if (imp.equalsIgnoreCase(nombreConfigurado.trim())) {
                return imp;
            }
        }
        // 2. Coincidencia parcial (ej. "XP-80T", "Xprinter", "POS-80")
        for (String imp : disponibles) {
            if (imp.toLowerCase().contains(busqueda) || busqueda.contains(imp.toLowerCase())) {
                return imp;
            }
        }
        return null;
    }

    private static volatile Modelo.ModoSalidaTicket modoGlobal = Modelo.ModoSalidaTicket.TERMICA_DIRECTA;
    private static volatile String impresoraGlobal = "DEFAULT";
    private static final List<java.util.function.Consumer<Modelo.ModoSalidaTicket>> listenersModo = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<java.util.function.Consumer<String>> listenersImpresora = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static Modelo.ModoSalidaTicket getModoGlobal() {
        return modoGlobal;
    }

    public static void setModoGlobal(Modelo.ModoSalidaTicket modo) {
        if (modo == null) {
            modo = Modelo.ModoSalidaTicket.TERMICA_DIRECTA;
        }
        modoGlobal = modo;
        for (java.util.function.Consumer<Modelo.ModoSalidaTicket> listener : listenersModo) {
            try {
                listener.accept(modo);
            } catch (Exception ignored) { LOGGER.log(Level.FINE, "Error silenciado: " + ignored.getMessage(), ignored); 
            }
        }
    }

    public static void addModoGlobalListener(java.util.function.Consumer<Modelo.ModoSalidaTicket> listener) {
        if (listener != null) {
            listenersModo.add(listener);
        }
    }

    public static void removeModoGlobalListener(java.util.function.Consumer<Modelo.ModoSalidaTicket> listener) {
        listenersModo.remove(listener);
    }

    public static String getImpresoraGlobal() {
        return impresoraGlobal;
    }

    public static void setImpresoraGlobal(String impresora) {
        if (impresora == null || impresora.isBlank()) {
            impresora = "DEFAULT";
        }
        impresoraGlobal = impresora.trim();
        for (java.util.function.Consumer<String> listener : listenersImpresora) {
            try {
                listener.accept(impresoraGlobal);
            } catch (Exception ignored) { LOGGER.log(Level.FINE, "Error silenciado: " + ignored.getMessage(), ignored); 
            }
        }
    }

    public static void addImpresoraGlobalListener(java.util.function.Consumer<String> listener) {
        if (listener != null) {
            listenersImpresora.add(listener);
        }
    }

    public static void removeImpresoraGlobalListener(java.util.function.Consumer<String> listener) {
        listenersImpresora.remove(listener);
    }

    /**
     * Procesa la salida del archivo PDF generado según el modo global y la impresora configurada.
     * @return true si el ticket se imprimió o despachó directamente según el modo elegido; false si se tuvo que degradar al visor de emergencia.
     */
    public static boolean procesarSalida(Path archivoPdf) throws IOException {
        String imp = !"DEFAULT".equalsIgnoreCase(impresoraGlobal) ? impresoraGlobal : obtenerImpresoraConfigurada();
        return procesarSalida(archivoPdf, modoGlobal, imp);
    }

    /**
     * Procesa la salida del archivo PDF generado según el modo especificado.
     * @return true si el ticket se imprimió o despachó directamente según el modo elegido; false si se tuvo que degradar al visor de emergencia.
     */
    public static boolean procesarSalida(Path archivoPdf, Modelo.ModoSalidaTicket modo, String impresoraConfigurada) throws IOException {
        if (archivoPdf == null || !Files.isRegularFile(archivoPdf)) {
            LOGGER.warning("Archivo PDF de ticket nulo o inexistente.");
            return false;
        }
        if (modo == null) {
            modo = modoGlobal;
        }
        String impConfig = (impresoraConfigurada != null && !impresoraConfigurada.isBlank())
                ? impresoraConfigurada
                : (!"DEFAULT".equalsIgnoreCase(impresoraGlobal) ? impresoraGlobal : obtenerImpresoraConfigurada());
        String imp = resolverNombreRealImpresora(impConfig);

        switch (modo) {
            case TERMICA_DIRECTA -> {
                boolean exito = imprimirEnWindows(archivoPdf, imp);
                if (exito) {
                    LOGGER.info(() -> "Ticket impreso directamente con éxito en: " + imp);
                    return true;
                } else {
                    LOGGER.warning(() -> "No se pudo imprimir directamente a " + imp + ". Abriendo visor como alternativa.");
                    abrirVisor(archivoPdf);
                    return false;
                }
            }
            case PDF24_CREATOR -> {
                boolean exito = despacharPdf24(archivoPdf);
                if (!exito) {
                    LOGGER.warning("No se pudo despachar a PDF24 Creator. Abriendo visor estándar como alternativa.");
                    abrirVisor(archivoPdf);
                    return false;
                }
                return true;
            }
            case VISOR_PDF -> {
                    return abrirVisor(archivoPdf);
                }
        }
        return false;
    }

    /**
     * Despacha el archivo PDF a PDF24 Creator:
     * 1. Intenta imprimir a la impresora virtual "PDF24" de Windows.
     * 2. Si no existe, intenta invocar el ejecutable pdf24-DocTool.exe o pdf24.exe.
     * 3. Si no está instalado, degrada en silencio y transparentemente a abrir el visor estándar.
     */
    public static boolean despacharPdf24(Path archivoPdf) {
        if (archivoPdf == null || !Files.isRegularFile(archivoPdf)) {
            return false;
        }
        boolean esWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String ruta = archivoPdf.toAbsolutePath().toString();

        if (esWindows) {
            // 1. Buscar impresora virtual de PDF24
            List<String> impresoras = listarImpresorasDisponibles();
            String impresoraPdf24 = null;
            for (String imp : impresoras) {
                if (imp != null && imp.toLowerCase().contains("pdf24")) {
                    impresoraPdf24 = imp;
                    break;
                }
            }
            if (impresoraPdf24 != null) {
                final String nombreImpFinal = impresoraPdf24;
                boolean ok = imprimirEnWindows(archivoPdf, nombreImpFinal);
                if (ok) {
                    LOGGER.info(() -> "Ticket enviado a impresora virtual PDF24: " + nombreImpFinal);
                    return true;
                }
            }

            // 2. Buscar ejecutable de PDF24 Creator en Program Files
            String[] posiblesRutas = {
                "C:\\Program Files\\PDF24\\pdf24-DocTool.exe",
                "C:\\Program Files (x86)\\PDF24\\pdf24-DocTool.exe",
                "C:\\Program Files\\PDF24\\pdf24.exe",
                "C:\\Program Files (x86)\\PDF24\\pdf24.exe"
            };
            for (String exe : posiblesRutas) {
                java.io.File f = new java.io.File(exe);
                if (f.isFile() && f.canExecute()) {
                    try {
                        new ProcessBuilder(exe, "-open", ruta).start();
                        LOGGER.info(() -> "Invocado ejecutable de PDF24: " + exe);
                        return true;
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Error al invocar ejecutable PDF24: " + ex.getMessage(), ex);
                    }
                }
            }
        }

        // 3. Fallback transparente a visor estándar
        try {
            abrirVisor(archivoPdf);
            return true;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error abriendo visor como fallback de PDF24: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Localiza el PrintService de Java que coincide con el nombre especificado.
     */
    public static PrintService buscarPrintService(String nombreImpresora) {
        if (nombreImpresora == null || nombreImpresora.isBlank() || "DEFAULT".equalsIgnoreCase(nombreImpresora)) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        PrintService[] servicios = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService ps : servicios) {
            if (ps != null && ps.getName() != null && ps.getName().equalsIgnoreCase(nombreImpresora.trim())) {
                return ps;
            }
        }
        String busqueda = nombreImpresora.trim().toLowerCase();
        for (PrintService ps : servicios) {
            if (ps != null && ps.getName() != null) {
                String n = ps.getName().toLowerCase();
                if (n.contains(busqueda) || busqueda.contains(n)) {
                    return ps;
                }
            }
        }
        return null;
    }

    /**
     * Intenta imprimir el PDF directamente a la cola de impresión de Windows mediante Apache PDFBox.
     * Es 100% silencioso y no depende de visores externos ni asociaciones de shell de Windows.
     */
    public static boolean imprimirDirectoJavaSpooler(Path archivoPdf, String nombreImpresora) {
        if (archivoPdf == null || !Files.isRegularFile(archivoPdf)) {
            return false;
        }
        try {
            PrintService target = buscarPrintService(nombreImpresora);
            if (target == null) {
                LOGGER.log(Level.FINE, () -> "No se encontró PrintService para: " + nombreImpresora);
                return false;
            }
            
            // N5: Evitar que impresoras virtuales bloqueen el hilo con un diálogo "Guardar como..."
            String lowerName = target.getName().toLowerCase();
            if (lowerName.contains("pdf") || lowerName.contains("xps") || lowerName.contains("onenote")) {
                LOGGER.warning(() -> "Impresora virtual detectada (" + target.getName() + "). Se aborta Java Spooler para evitar bloqueos del sistema.");
                return false;
            }
            
            try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(archivoPdf.toFile())) {
                java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
                job.setPrintService(target);
                job.setJobName("Ticket Restaurante 2026 - " + archivoPdf.getFileName());
                job.setPageable(new org.apache.pdfbox.printing.PDFPageable(doc));
                job.print();
                LOGGER.info(() -> "Ticket enviado exitosamente al spooler vía PDFBox/PrinterJob: " + target.getName());
                return true;
            }
        } catch (Exception | LinkageError ex) {
            LOGGER.log(Level.WARNING, "Fallo al imprimir directamente vía PDFBox/PrinterJob: " + ex.getMessage(), ex);
            return false;
        }
    }

    /** Envía el archivo PDF a imprimir directamente en Windows 11. */
    public static boolean imprimirEnWindows(Path archivoPdf, String nombreImpresora) {
        if (archivoPdf == null || !Files.isRegularFile(archivoPdf)) {
            return false;
        }

        boolean esWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String rutaAbsoluta = archivoPdf.toAbsolutePath().toString();

        if (esWindows) {
            // 1. Método preferido: Impresión nativa directa al Spooler de Windows vía PDFBox (silenciosa y sin ventanas)
            if (imprimirDirectoJavaSpooler(archivoPdf, nombreImpresora)) {
                return true;
            }

            // 2. Si no fue posible vía Java Spooler, intentar comando PowerShell con captura estricta de error
            if (nombreImpresora != null && !nombreImpresora.isBlank() && !"DEFAULT".equalsIgnoreCase(nombreImpresora)) {
                Process proceso = null;
                try {
                    String cmd = String.format(
                            "$ErrorActionPreference = 'Stop'; try { Start-Process -FilePath '%s' -Verb PrintTo -ArgumentList '%s' -WindowStyle Hidden -Wait; exit 0 } catch { exit 1 }",
                            rutaAbsoluta.replace("'", "''"),
                            nombreImpresora.replace("'", "''"));
                    proceso = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd).start();
                    boolean finalizo = proceso.waitFor(7, TimeUnit.SECONDS);
                    if (finalizo && proceso.exitValue() == 0) {
                        return true;
                    }
                    if (!finalizo) {
                        LOGGER.warning("Timeout (7s) agotado en PrintTo hacia " + nombreImpresora + ". Terminando proceso.");
                        proceso.destroyForcibly();
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Aviso en PrintTo hacia " + nombreImpresora + ": " + ex.getMessage(), ex);
                    if (proceso != null && proceso.isAlive()) {
                        proceso.destroyForcibly();
                    }
                }
            }

            // Desktop.print eliminado (Punto 2): retornaba true ciegamente sin garantizar que se imprimiera.
            // Se confía en el fallback de PowerShell (Paso 3) que sí permite tracking.

            // 4. Fallback PowerShell: imprimir a la predeterminada de Windows con trampa de error
            Process procesoFallback = null;
            try {
                String cmd = String.format(
                        "$ErrorActionPreference = 'Stop'; try { Start-Process -FilePath '%s' -Verb Print -WindowStyle Hidden -Wait; exit 0 } catch { exit 1 }",
                        rutaAbsoluta.replace("'", "''"));
                procesoFallback = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd).start();
                boolean finalizo = procesoFallback.waitFor(7, TimeUnit.SECONDS);
                if (finalizo && procesoFallback.exitValue() == 0) {
                    return true;
                }
                if (!finalizo) {
                    LOGGER.warning("Timeout (7s) agotado en Print de PowerShell predeterminado. Terminando proceso.");
                    procesoFallback.destroyForcibly();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Aviso en Print por PowerShell: " + ex.getMessage(), ex);
                if (procesoFallback != null && procesoFallback.isAlive()) {
                    procesoFallback.destroyForcibly();
                }
            }
        } else {
            // Entorno no Windows (Linux/Unix): intentar spooler nativo Java primero o comando lp
            if (imprimirDirectoJavaSpooler(archivoPdf, nombreImpresora)) {
                return true;
            }
            Process procesoLp = null;
            try {
                ProcessBuilder pb;
                if (nombreImpresora != null && !nombreImpresora.isBlank() && !"DEFAULT".equalsIgnoreCase(nombreImpresora)) {
                    pb = new ProcessBuilder("lp", "-d", nombreImpresora, rutaAbsoluta);
                } else {
                    pb = new ProcessBuilder("lp", rutaAbsoluta);
                }
                procesoLp = pb.start();
                boolean finalizo = procesoLp.waitFor(3, TimeUnit.SECONDS);
                if (finalizo && procesoLp.exitValue() == 0) {
                    return true;
                }
                if (!finalizo) {
                    procesoLp.destroyForcibly();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Aviso ejecutando lp en Linux: " + ex.getMessage(), ex);
                if (procesoLp != null && procesoLp.isAlive()) {
                    procesoLp.destroyForcibly();
                }
            }
        }

        return false;
    }

    /** Abre el PDF en el visor predeterminado del sistema operativo. */
    public static boolean abrirVisor(Path archivoPdf) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(archivoPdf.toFile());
            return true;
        } else {
            LOGGER.info(() -> "Entorno sin Desktop.open soportado. Archivo generado en: " + archivoPdf.toAbsolutePath());
            return false;
        }
    }

    /**
     * Genera un ticket de prueba de 80 mm y lo despacha al flujo de salida seleccionado.
     */
    public static boolean imprimirTicketPrueba(String impresora, Modelo.ModoSalidaTicket modo) {
        try {
            Path tempTicket = Files.createTempFile("ticket_prueba_", ".pdf");
            tempTicket.toFile().deleteOnExit();

            com.itextpdf.text.Rectangle tamano = new com.itextpdf.text.Rectangle(226.77f, 320f);
            com.itextpdf.text.Document doc = new com.itextpdf.text.Document(tamano, 10f, 10f, 10f, 10f);
            try (java.io.OutputStream os = Files.newOutputStream(tempTicket)) {
                com.itextpdf.text.pdf.PdfWriter.getInstance(doc, os);
                doc.open();

                com.itextpdf.text.Font fTit = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 11, com.itextpdf.text.Font.BOLD);
                com.itextpdf.text.Font fSub = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9, com.itextpdf.text.Font.BOLD);
                com.itextpdf.text.Font fNormal = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 8, com.itextpdf.text.Font.NORMAL);

                com.itextpdf.text.Paragraph p1 = new com.itextpdf.text.Paragraph("=== RESTAURANTE 2026 ===", fTit);
                p1.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                doc.add(p1);

                com.itextpdf.text.Paragraph p2 = new com.itextpdf.text.Paragraph("TICKET DE PRUEBA", fSub);
                p2.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                doc.add(p2);

                doc.add(new com.itextpdf.text.Paragraph("----------------------------------------", fNormal));
                doc.add(new com.itextpdf.text.Paragraph("Fecha/Hora: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")), fNormal));
                doc.add(new com.itextpdf.text.Paragraph("Impresora: " + (impresora != null ? impresora : "DEFAULT"), fNormal));
                doc.add(new com.itextpdf.text.Paragraph("Modo de Salida: " + (modo != null ? modo.getEtiqueta() : "Térmica Directa"), fNormal));
                doc.add(new com.itextpdf.text.Paragraph("----------------------------------------", fNormal));

                com.itextpdf.text.Paragraph pFin = new com.itextpdf.text.Paragraph("¡Impresión de prueba completada con éxito!", fSub);
                pFin.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                doc.add(pFin);

                doc.close();
            }

            if (modo == Modelo.ModoSalidaTicket.TERMICA_DIRECTA) {
                String impReal = resolverNombreRealImpresora(impresora != null ? impresora : (!"DEFAULT".equalsIgnoreCase(impresoraGlobal) ? impresoraGlobal : obtenerImpresoraConfigurada()));
                boolean ok = imprimirEnWindows(tempTicket, impReal);
                if (!ok) {
                    abrirVisor(tempTicket);
                    return false;
                }
                return true;
            } else if (modo == Modelo.ModoSalidaTicket.PDF24_CREATOR) {
                boolean ok = despacharPdf24(tempTicket);
                return ok;
            } else {
                return abrirVisor(tempTicket);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al generar ticket de prueba: " + ex.getMessage(), ex);
            return false;
        }
    }
}


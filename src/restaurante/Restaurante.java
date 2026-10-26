package restaurante;

import Vista.FrmLogin;
import Vista.ManejadorErroresSwing;
import Controlador.LoginControlador;
import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.LoginDao;
import Modelo.PedidosDao;
import Modelo.PlatosDao;
import Modelo.SalasDao;
import Modelo.Usuario;
import Servicio.AutenticacionServicio;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PlatosServicio;
import Servicio.PoliticaAcceso;
import Servicio.SalasServicio;
import Servicio.ServicioImpresionTicket;
import Vista.Sistema;
import java.util.function.Function;
import java.awt.Desktop;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;

public class Restaurante {

    private static java.net.ServerSocket socketInstanciaUnica;
    /**
     * Comandos de mantenimiento sin interfaz: respaldo y restauración desde la
     * línea de comandos. Devuelve {@code true} si la invocación era un comando
     * y ya fue atendida (el proceso debe terminar).
     */
    private static boolean ejecutarComandoMantenimiento(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        boolean respaldar = "--respaldar".equalsIgnoreCase(args[0]);
        boolean restaurar = "--restaurar-respaldo".equalsIgnoreCase(args[0]);
        boolean verificar = "--verificar-respaldo".equalsIgnoreCase(args[0]);
        boolean api = "--api".equalsIgnoreCase(args[0]) || "--servidor-api".equalsIgnoreCase(args[0]);
        if (!respaldar && !restaurar && !verificar && !api) {
            return false;
        }
        try {
            infraestructura.ConfiguracionLogs.configurar();
        } catch (Exception ignored) {
        }
        try {
            if (api) {
                int puerto = args.length >= 2 ? Integer.parseInt(args[1]) : 8080;
                infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new infraestructura.ProveedorConexionJdbc());
                infraestructura.api.ServidorRestTablets.iniciar(puerto);
                System.out.println("SERVIDOR_API_INICIADO en puerto " + puerto);
                try {
                    Thread.currentThread().join();
                } catch (InterruptedException ignored) {
                }
                return true;
            }
            Servicio.ServicioRespaldoBaseDatos servicio = new Servicio.ServicioRespaldoBaseDatos();
            servicio.recuperarOperacionInterrumpida();
            if (respaldar) {
                java.nio.file.Path directorio = args.length >= 2 && !args[1].isBlank()
                        ? java.nio.file.Paths.get(args[1])
                        : Servicio.ServicioRespaldoBaseDatos.DIRECTORIO_RESPALDOS_DEFAULT;
                java.nio.file.Path archivo = servicio.crearRespaldo(directorio);
                System.out.println("RESPALDO_OK " + archivo.toAbsolutePath());
            } else {
                if (args.length < 2 || args[1].isBlank()) {
                    System.err.println("Uso: java -jar Restaurante.jar "
                            + (verificar ? "--verificar-respaldo" : "--restaurar-respaldo") + " <archivo.sql>");
                    System.exit(2);
                }
                java.nio.file.Path archivo = java.nio.file.Paths.get(args[1]).toAbsolutePath().normalize();
                if (verificar) {
                    servicio.validarRestaurableEnEsquemaTemporal(archivo);
                    System.out.println("VERIFICACION_OK " + archivo);
                } else {
                    java.nio.file.Path previo = servicio.restaurarRespaldo(archivo);
                    System.out.println("RESTAURACION_OK respaldo_previo=" + previo.toAbsolutePath());
                }
            }
        } catch (Exception ex) {
            String prefijo = respaldar ? "RESPALDO_FALLIDO: "
                    : verificar ? "VERIFICACION_FALLIDA: " : "RESTAURACION_FALLIDA: ";
            System.err.println(prefijo + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
        return true;
    }

    private static boolean verificarInstanciaUnica() {
        try {
            // El puerto puede sobrescribirse en pruebas aisladas; el arranque normal conserva 49200.
            int puerto = Integer.getInteger("restaurante.instancia.puerto", 49200);
            socketInstanciaUnica = new java.net.ServerSocket(puerto, 1, java.net.InetAddress.getByName("127.0.0.1"));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (socketInstanciaUnica != null && !socketInstanciaUnica.isClosed()) {
                        socketInstanciaUnica.close();
                    }
                } catch (Exception ignored) {
                }
            }, "shutdown-socket-instancia"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        if (ejecutarComandoMantenimiento(args)) {
            return;
        }
        if (!verificarInstanciaUnica()) {
            javax.swing.JOptionPane.showMessageDialog(null, "El sistema ya se encuentra en ejecución.\nPor favor revisa la barra de tareas o las aplicaciones abiertas.", "Instancia en Ejecución", javax.swing.JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }
        try {
            // Nimbus (100 % Java): idéntico en Linux y Windows; si no está, sistema.
            boolean nimbus = false;
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    nimbus = true;
                    break;
                }
            }
            if (!nimbus) {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            }
            
            // UX/UI Customizations for Dialogs and Inputs
            java.awt.Color colorFondoDialogo = new java.awt.Color(242, 242, 242);
            java.awt.Color colorBorde = new java.awt.Color(0, 102, 102);
            
            javax.swing.UIManager.put("OptionPane.background", colorFondoDialogo);
            javax.swing.UIManager.put("Panel.background", colorFondoDialogo);
            javax.swing.UIManager.put("OptionPane.border", javax.swing.BorderFactory.createLineBorder(colorBorde, 3));
            
            // Fix input fields contrast
            javax.swing.UIManager.put("TextField.background", java.awt.Color.WHITE);
            javax.swing.UIManager.put("TextField.border", javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153), 1),
                    javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            
            javax.swing.UIManager.put("PasswordField.background", java.awt.Color.WHITE);
            javax.swing.UIManager.put("PasswordField.border", javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153), 1),
                    javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Restaurante.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        ManejadorErroresSwing.instalar();
        infraestructura.ConfiguracionLogs.configurar();
        if (args != null && args.length == 1 && "--migrate-db".equalsIgnoreCase(args[0])) {
            try {
                new Servicio.ServicioRespaldoBaseDatos().recuperarOperacionInterrumpida();
                infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new infraestructura.ProveedorConexionJdbc());
                new Servicio.ConfiguracionAccesosServicio().asegurarMatrizPredeterminada();
            } catch (Exception ex) {
                Throwable raiz = ex;
                while (raiz.getCause() != null && raiz.getCause() != raiz) {
                    raiz = raiz.getCause();
                }
                String detalle = raiz.getMessage() != null && !raiz.getMessage().isBlank()
                        ? raiz.getMessage() : ex.getMessage();
                System.err.println("MIGRACION_FALLIDA: " + detalle);
                System.exit(1);
                return;
            }
            System.out.println("Migración de base de datos validada correctamente.");
            return;
        }
        new Servicio.ServicioRespaldoBaseDatos().recuperarOperacionInterrumpida();
        infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new infraestructura.ProveedorConexionJdbc());
        new Servicio.ConfiguracionAccesosServicio().asegurarMatrizPredeterminada();
        Thread.ofVirtual().name("backup-diario-inicio").start(() -> {
            try {
                new Servicio.ServicioRespaldoBaseDatos().crearRespaldoAutomaticoSiEsNecesario();
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Restaurante.class.getName())
                        .log(java.util.logging.Level.FINE, "Aviso en respaldo automático inicial: " + ex.getMessage());
            }
        });
        Thread.ofVirtual().name("servidor-rest-tablets").start(() -> {
            try {
                infraestructura.api.ServidorRestTablets.iniciarSiEstaHabilitado();
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Restaurante.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso iniciando servidor REST para tablets: " + ex.getMessage());
            }
        });
        Thread.ofVirtual().name("tunel-inverso-ssh").start(() -> {
            try {
                infraestructura.red.ServicioTunelSsh.iniciarSiEstaHabilitado();
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Restaurante.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso iniciando túnel SSH para monitoreo: " + ex.getMessage());
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(infraestructura.red.ServicioTunelSsh::detener, "shutdown-tunel-ssh"));

        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            Servicio.ServicioBootstrapInicial servicioBootstrap = new Servicio.ServicioBootstrapInicial(new infraestructura.ProveedorConexionJdbc());
            if (!servicioBootstrap.esBootstrapCompletado()) {
                boolean completado = Vista.FrmBootstrapInicial.mostrarDialogo(null, servicioBootstrap);
                if (!completado) {
                    System.exit(0);
                    return;
                }
            }
        }

        verificarMostrarBromaLicencia();
        lanzarLogin();
    }

    private static void verificarMostrarBromaLicencia() {
        if (java.awt.GraphicsEnvironment.isHeadless() || "true".equals(System.getProperty("modo.automatizado"))) {
            return;
        }
        try {
            Modelo.ConfiguracionSistemaDao configDao = new Modelo.ConfiguracionSistemaDao();
            int conteo = 0;
            try {
                String strConteo = configDao.obtener(Modelo.ConfigClaves.BROMA_INICIO_CONTEO, "0");
                conteo = Integer.parseInt(strConteo.trim());
            } catch (Exception ignored) {}

            if (conteo < 3) {
                configDao.guardar(Modelo.ConfigClaves.BROMA_INICIO_CONTEO, String.valueOf(conteo + 1));
                Vista.DialogoAlertaLicenciaBroma.mostrar();
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Restaurante.class.getName())
                    .log(java.util.logging.Level.FINE, "Aviso verificando broma de inicio: " + ex.getMessage());
        }
    }

    public static void lanzarLogin() {
        LoginControlador controlador = new LoginControlador(
                new AutenticacionServicio(new LoginDao()));
        Function<Usuario, Sistema> crearSistema = usuario -> {
            LoginDao loginDao = new LoginDao();
            java.util.Set<PoliticaAcceso.Accion> permisos = new Servicio.ConfiguracionAccesosServicio()
                    .cargarPermisos(usuario != null ? usuario.getRol() : null);
            PoliticaAcceso politica = new PoliticaAcceso(usuario, () -> {
                if (usuario == null || usuario.getId() <= 0) return false;
                try {
                    return loginDao.esUsuarioActivoConRol(usuario.getId(), usuario.getRol());
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger(Restaurante.class.getName())
                            .warning("No se pudo verificar el estado activo del usuario #" + usuario.getId() + " en la BD; acceso denegado por seguridad: " + ex.getMessage());
                    return false;
                }
            }, permisos);
            PedidosDao pedidosDao = new PedidosDao();
            LoginDao empresaDao = new LoginDao();
            PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                    pedidosDao::verPedido,
                    id -> pedidosDao.verPedidoDetalle(id),
                    empresaDao::datosEmpresa,
                    GeneradorPdfPedido.conEstructuraMensual(GeneradorPdfPedido.resolverDirectorioFacturasPorDefecto()),
                    PedidoPdfServicio.AbridorPdf.porDefecto(),
                    ServicioImpresionTicket::abrirVisor,
                    PedidoPdfServicio.ProcesadorSalidaTicket.porDefecto());
            Servicio.CierreCajaServicio cierreServicio = new Servicio.CierreCajaServicio(
                    new Modelo.CierreCajaDao(),
                    empresaDao::datosEmpresa,
                    new Servicio.GeneradorPdfCierre());
            PedidosControlador pedidosControlador = new PedidosControlador(
                    new PedidoServicio(pedidosDao, politica), pdfServicio, politica,
                    new ConsultaPedidosServicio(pedidosDao, politica),
                    cierreServicio);
            return new Sistema(usuario,
                    new SalasControlador(new SalasServicio(new SalasDao(), politica)),
                    new PlatosControlador(new PlatosServicio(new PlatosDao(), politica)),
                    pedidosControlador,
                    new Controlador.CategoriaControlador(new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politica)),
                    new Controlador.FavoritoControlador(new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politica)),
                    empresaDao);
        };
        SwingUtilities.invokeLater(() -> {
            FrmLogin iniciar = new FrmLogin(controlador, crearSistema);
            iniciar.setVisible(true);
        });
    }
}

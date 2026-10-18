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
import Vista.Sistema;
import java.util.function.Function;
import java.awt.Desktop;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;

public class Restaurante {

    private static java.net.ServerSocket socketInstanciaUnica;
    private static boolean verificarInstanciaUnica() {
        try {
            socketInstanciaUnica = new java.net.ServerSocket(49200, 1, java.net.InetAddress.getByName("127.0.0.1"));
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
            new Servicio.ServicioRespaldoBaseDatos().recuperarOperacionInterrumpida();
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new infraestructura.ProveedorConexionJdbc());
            System.out.println("Migración de base de datos validada correctamente.");
            return;
        }
        new Servicio.ServicioRespaldoBaseDatos().recuperarOperacionInterrumpida();
        infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new infraestructura.ProveedorConexionJdbc());
        Thread.ofVirtual().name("backup-diario-inicio").start(() -> {
            try {
                new Servicio.ServicioRespaldoBaseDatos().crearRespaldoAutomaticoSiEsNecesario();
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Restaurante.class.getName())
                        .log(java.util.logging.Level.FINE, "Aviso en respaldo automático inicial: " + ex.getMessage());
            }
        });
        lanzarLogin();
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
                    PedidoPdfServicio.AbridorPdf.porDefecto());
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

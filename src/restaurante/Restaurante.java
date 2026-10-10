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

    public static void main(String[] args) {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            
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
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new infraestructura.ProveedorConexionJdbc());
            System.out.println("Migración de base de datos validada correctamente.");
            return;
        }
        infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new infraestructura.ProveedorConexionJdbc());
        Thread.ofVirtual().name("backup-diario-inicio").start(() -> {
            try {
                new Servicio.ServicioRespaldoBaseDatos().crearRespaldoAutomaticoSiEsNecesario();
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Restaurante.class.getName())
                        .log(java.util.logging.Level.FINE, "Aviso en respaldo automático inicial: " + ex.getMessage());
            }
        });
        LoginControlador controlador = new LoginControlador(
                new AutenticacionServicio(new LoginDao()));
        Function<Usuario, Sistema> crearSistema = usuario -> {
            PoliticaAcceso politica = new PoliticaAcceso(usuario);
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
                    new PedidoServicio(pedidosDao), pdfServicio, politica,
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

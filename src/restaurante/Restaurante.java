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
                    pedidosControlador);
        };
        SwingUtilities.invokeLater(() -> {
            FrmLogin iniciar = new FrmLogin(controlador, crearSistema);
            iniciar.setVisible(true);
        });
    }
    
}

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
                    new GeneradorPdfPedido(Paths.get(System.getProperty("user.home", "."))),
                    archivo -> Desktop.getDesktop().open(archivo.toFile()));
            PedidosControlador pedidosControlador = new PedidosControlador(
                    new PedidoServicio(pedidosDao), pdfServicio, politica,
                    new ConsultaPedidosServicio(pedidosDao, politica));
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

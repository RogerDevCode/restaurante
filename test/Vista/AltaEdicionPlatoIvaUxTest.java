package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.ErrorAplicacionException;
import Modelo.LoginDao;
import Modelo.PedidosRepositorioFalso;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Prueba Swing dirigida para la captura de ErrorAplicacionException de IVA
 * en el alta y modificación de platos en Sistema.java.
 */
public class AltaEdicionPlatoIvaUxTest {

    private static class PlatosRepoFalloIva implements PlatosRepositorio {
        @Override
        public boolean registrar(Platos plato) {
            throw ErrorAplicacionException.validacion("Debe indicar si el precio del plato incluye IVA.");
        }

        @Override
        public List<Platos> listarPorFecha(String nombre, String fecha) {
            return Collections.emptyList();
        }

        @Override
        public boolean modificar(Platos plato) {
            throw ErrorAplicacionException.validacion("Debe indicar si el precio del plato incluye IVA.");
        }
    }

    private Sistema crearSistema(PlatosRepositorio platosRepo) throws Exception {
        final Sistema[] contenedor = new Sistema[1];
        Usuario usuario = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        PoliticaAcceso rbac = new PoliticaAcceso(usuario);

        SalasRepositorio salasRepo = new SalasRepositorio() {
            @Override public boolean registrar(Modelo.Salas sl) { return true; }
            @Override public List<Modelo.Salas> listar() { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Modelo.Salas sl) { return true; }
        };

        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso();
        SalasControlador salasCtrl = new SalasControlador(new Servicio.SalasServicio(salasRepo, rbac));
        PlatosControlador platosCtrl = new PlatosControlador(new Servicio.PlatosServicio(platosRepo, rbac));
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(pedidosRepo, rbac);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                new PedidoPdfServicio(id -> null, id -> null, () -> null, new GeneradorPdfPedido(Path.of(".")), p -> true),
                rbac,
                consultas
        );

        LoginDao loginDao = new LoginDao() {
            @Override
            public boolean esUsuarioActivo(int id) {
                return true;
            }
        };

        SwingUtilities.invokeAndWait(() -> {
            contenedor[0] = new Sistema(usuario, salasCtrl, platosCtrl, pedidosCtrl, loginDao);
        });
        return contenedor[0];
    }

    @Test
    public void altaPlatoConFalloIvaDirigeAlertaACasillaIvaSinRomperFlujo() throws Exception {
        Sistema sistema = crearSistema(new PlatosRepoFalloIva());
        try {
            SwingUtilities.invokeAndWait(() -> {
                sistema.getTxtNombrePlato().setText("Plato Prueba");
                sistema.getTxtPrecioPlato().setText("10.00");
                sistema.getBtnGuardarPlato().doClick();
            });

            // Verificar que la alerta roja y tooltip se dirigen a la casilla de IVA
            assertNotNull(sistema.getChkPrecioIncluyeIvaPlato());
            assertEquals("Debe indicar si el precio del plato incluye IVA.",
                    sistema.getChkPrecioIncluyeIvaPlato().getToolTipText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void edicionPlatoConFalloIvaDirigeAlertaACasillaIvaSinRomperFlujo() throws Exception {
        Sistema sistema = crearSistema(new PlatosRepoFalloIva());
        try {
            SwingUtilities.invokeAndWait(() -> {
                sistema.getTxtIdPlato().setText("42");
                sistema.getTxtNombrePlato().setText("Plato Editado");
                sistema.getTxtPrecioPlato().setText("15.00");
                sistema.getBtnEditarPlato().doClick();
            });

            assertNotNull(sistema.getChkPrecioIncluyeIvaPlato());
            assertEquals("Debe indicar si el precio del plato incluye IVA.",
                    sistema.getChkPrecioIncluyeIvaPlato().getToolTipText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }
}

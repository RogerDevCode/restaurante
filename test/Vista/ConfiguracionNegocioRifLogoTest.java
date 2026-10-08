package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.LoginDao;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.PlatosRepositorio;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas unitarias de las características de configuración 100% personalizable:
 * RIF venezolano, datos de negocio y cambio de logo/ícono en UI y tickets PDF.
 */
public class ConfiguracionNegocioRifLogoTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    private static java.util.Timer autoDismissTimer;

    @org.junit.BeforeClass
    public static void setupAutoDismiss() {
        autoDismissTimer = new java.util.Timer("modal-dismisser-config", true);
        autoDismissTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    for (java.awt.Window window : java.awt.Window.getWindows()) {
                        if (window instanceof javax.swing.JDialog dialog && dialog.isModal()) {
                            dialog.dispose();
                        }
                    }
                });
            }
        }, 50, 50);
    }

    @org.junit.AfterClass
    public static void teardownAutoDismiss() {
        if (autoDismissTimer != null) {
            autoDismissTimer.cancel();
        }
    }

    private final Usuario usuarioAdmin = new Usuario(1, "Admin Negocio", "admin@empresa.ve", "pass", "Administrador");

    // =========================================================================
    // 1. Etiqueta RIF en la interfaz gráfica en lugar de RUC
    // =========================================================================
    @Test
    public void testEtiquetaConfiguracionEsRifEnVezDeRuc() throws Exception {
        Sistema sistema = crearSistema(usuarioAdmin, new Config(1, "J-30987654-1", "Arepera Caracas", "0412-1234567",
                "Sabana Grande", "¡Buen provecho!"));
        try {
            JLabel lblRif = campo(sistema, "jLabel27", JLabel.class);
            assertNotNull(lblRif);
            assertEquals("La etiqueta en pantalla debe ser RIF para Venezuela", "RIF", lblRif.getText());

            JTextField txtRif = campo(sistema, "txtRucConfig", JTextField.class);
            assertEquals("J-30987654-1", txtRif.getText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 2. Modelo Config con alias getRif/setRif y logoPath
    // =========================================================================
    @Test
    public void testModeloConfigSoportaRifYLogo() {
        Config conf = new Config();
        conf.setRif("G-20001234-5");
        conf.setLogoPath("/ruta/a/mi/logo_restaurante.png");

        assertEquals("G-20001234-5", conf.getRif());
        assertEquals("G-20001234-5", conf.getRuc()); // Retrocompatibilidad
        assertEquals("/ruta/a/mi/logo_restaurante.png", conf.getLogoPath());

        conf.setRuc("J-11223344-0");
        assertEquals("J-11223344-0", conf.getRif());
    }

    // =========================================================================
    // 3. Modificación de datos del negocio con RIF y teléfono venezolano
    // =========================================================================
    @Test
    public void testActualizarDatosNegocioConRifYTelefonoVenezolano() throws Exception {
        Config configInicial = new Config(1, "J-12345678-0", "Restaurante Ávila", "0212-9876543",
                "Chacao, Caracas", "Gracias por su visita", new BigDecimal("38.5000"), new BigDecimal("16.00"), null);

        LoginDaoFalsoMock daoMock = new LoginDaoFalsoMock(configInicial);
        Sistema sistema = crearSistemaConDao(usuarioAdmin, daoMock);
        try {
            JTextField txtRif = campo(sistema, "txtRucConfig", JTextField.class);
            JTextField txtNombre = campo(sistema, "txtNombreConfig", JTextField.class);
            JTextField txtTelefono = campo(sistema, "txtTelefonoConfig", JTextField.class);
            JTextField txtDireccion = campo(sistema, "txtDireccionConfig", JTextField.class);
            JTextField txtMensaje = campo(sistema, "txtMensaje", JTextField.class);
            JButton btnActualizar = campo(sistema, "btnActualizarConfig", JButton.class);

            // Modificar a datos de empresa venezolana
            txtRif.setText("J-50123456-7");
            txtNombre.setText("Arepera Gran Café");
            txtTelefono.setText("+58 414 1234567");
            txtDireccion.setText("Bulevar de Sabana Grande, Caracas");
            txtMensaje.setText("¡La mejor sazón venezolana!");

            SwingUtilities.invokeAndWait(btnActualizar::doClick);

            Config guardado = daoMock.configuracionActual;
            assertEquals("J-50123456-7", guardado.getRif());
            assertEquals("Arepera Gran Café", guardado.getNombre());
            assertEquals("+58 414 1234567", guardado.getTelefono());
            assertEquals("Bulevar de Sabana Grande, Caracas", guardado.getDireccion());
            assertEquals("¡La mejor sazón venezolana!", guardado.getMensaje());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 4. Cambio de Logo e Ícono en Sistema y Vista Previa
    // =========================================================================
    @Test
    public void testConfigurarLogoEIconoPersonalizado() throws Exception {
        // Crear una imagen PNG temporal de prueba para simular el logo
        File archivoLogo = temporal.newFile("logo_custom.png");
        BufferedImage img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", archivoLogo);

        Config configInicial = new Config(1, "J-12345678-0", "Restaurante Ávila", "0212-9876543",
                "Caracas", "Gracias", new BigDecimal("38.5000"), new BigDecimal("16.00"), null);

        LoginDaoFalsoMock daoMock = new LoginDaoFalsoMock(configInicial);
        Sistema sistema = crearSistemaConDao(usuarioAdmin, daoMock);
        try {
            JLabel lblPreview = campo(sistema, "lblLogoPreview", JLabel.class);
            JTextField txtRuta = campo(sistema, "txtRutaLogo", JTextField.class);
            JButton btnActualizar = campo(sistema, "btnActualizarConfig", JButton.class);

            assertNotNull("La vista previa del logo debe estar inicializada", lblPreview);
            assertNotNull("El campo de ruta del logo debe estar inicializado", txtRuta);

            // Establecer la nueva ruta del logo y actualizar
            SwingUtilities.invokeAndWait(() -> {
                txtRuta.setText(archivoLogo.getAbsolutePath());
                sistema.actualizarLogoEIcono(archivoLogo.getAbsolutePath());
            });

            assertNotNull("lblLogoPreview debe tener icono asignado tras configurar logo",
                    lblPreview.getIcon());

            // Guardar configuración
            SwingUtilities.invokeAndWait(btnActualizar::doClick);

            assertEquals("El DAO debe haber recibido la nueva ruta del logo",
                    archivoLogo.getAbsolutePath(), daoMock.configuracionActual.getLogoPath());

            // Restablecer logo
            JButton btnRestablecer = campo(sistema, "btnRestablecerLogo", JButton.class);
            assertNotNull(btnRestablecer);
            SwingUtilities.invokeAndWait(btnRestablecer::doClick);
            assertEquals("", txtRuta.getText());

            SwingUtilities.invokeAndWait(btnActualizar::doClick);
            assertEquals(null, daoMock.configuracionActual.getLogoPath());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 5. Generación de PDF con RIF y con Logo Personalizado
    // =========================================================================
    @Test
    public void testGeneradorPdfUsaRifYLogoPersonalizado() throws Exception {
        Path dirSalida = temporal.newFolder("pdf_rif_logo").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(dirSalida);

        // Crear logo personalizado en disco
        File archivoLogo = temporal.newFile("logo_empresa_ticket.png");
        BufferedImage bi = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(bi, "png", archivoLogo);

        Config config = new Config(1, "J-99887766-3", "Restaurante Los Llanos", "0241-8889900",
                "Valencia - Venezuela", "Feliz Día", new BigDecimal("36.5000"), new BigDecimal("16.00"),
                archivoLogo.getAbsolutePath());

        Pedidos pedido = new Pedidos();
        pedido.setId(77);
        pedido.setFecha("2026-10-06");
        pedido.setSala("Terraza");
        pedido.setNum_mesa(1);
        pedido.setTotalDecimal(new BigDecimal("15.00"));
        pedido.setUsuario("Admin Negocio");

        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Pabellón Criollo");
        detalle.setCantidad(1);
        detalle.setPrecioDecimal(new BigDecimal("15.00"));

        Path pdfGenerado = generador.generar(pedido, config, Collections.singletonList(detalle));
        assertTrue(Files.exists(pdfGenerado));
        assertTrue(Files.size(pdfGenerado) > 500); // PDF generado con éxito con imagen incrustada
    }

    // =========================================================================
    // Clases auxiliares para tests
    // =========================================================================
    private static class LoginDaoFalsoMock extends LoginDao {
        Config configuracionActual;

        LoginDaoFalsoMock(Config configInicial) {
            this.configuracionActual = configInicial;
        }

        @Override
        public Config datosEmpresa() {
            return configuracionActual;
        }

        @Override
        public boolean ModificarDatos(Config conf) {
            this.configuracionActual = conf;
            return true;
        }
    }

    private Sistema crearSistema(Usuario usuario, Config config) throws Exception {
        return crearSistemaConDao(usuario, new LoginDaoFalsoMock(config));
    }

    private Sistema crearSistemaConDao(Usuario usuario, LoginDao loginDao) throws Exception {
        final Sistema[] contenedor = new Sistema[1];
        PoliticaAcceso rbac = new PoliticaAcceso(usuario);

        SalasRepositorio salasRepo = new SalasRepositorio() {
            @Override public boolean registrar(Modelo.Salas sl) { return true; }
            @Override public List<Modelo.Salas> listar() { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Modelo.Salas sl) { return true; }
        };

        PlatosRepositorio platosRepo = new PlatosRepositorio() {
            @Override public boolean registrar(Modelo.Platos pla) { return true; }
            @Override public List<Modelo.Platos> listarPorFecha(String f, String fe) { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Modelo.Platos pla) { return true; }
        };

        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso();

        SalasControlador salasCtrl = new SalasControlador(new Servicio.SalasServicio(salasRepo, rbac));
        PlatosControlador platosCtrl = new PlatosControlador(new Servicio.PlatosServicio(platosRepo, rbac));
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(pedidosRepo, rbac);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                new PedidoPdfServicio(id -> null, id -> null, () -> null, new GeneradorPdfPedido(Path.of(".")), p -> {}),
                rbac,
                consultas
        );

        SwingUtilities.invokeAndWait(() -> {
            contenedor[0] = new Sistema(usuario, salasCtrl, platosCtrl, pedidosCtrl, loginDao);
        });
        return contenedor[0];
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object obj, String nombreCampo, Class<T> tipo) throws Exception {
        Field f = obj.getClass().getDeclaredField(nombreCampo);
        f.setAccessible(true);
        return (T) f.get(obj);
    }
}

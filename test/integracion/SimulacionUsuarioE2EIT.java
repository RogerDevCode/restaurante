package integracion;

import Controlador.LoginControlador;
import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.LoginDao;
import Modelo.PedidoPendienteExistenteException;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Modelo.Platos;
import Modelo.PlatosDao;
import Modelo.Salas;
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
import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Prueba de integración E2E que simula el recorrido completo de un usuario. */
public class SimulacionUsuarioE2EIT {

    @Test
    public void simulaRecorridoCompletoDeUsuarioE2E() throws Exception {
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
        LoginDao loginDao = new LoginDao(proveedor, new PasswordHasher());
        SalasDao salasDao = new SalasDao(proveedor);
        PlatosDao platosDao = new PlatosDao(proveedor);
        PedidosDao pedidosDao = new PedidosDao(proveedor);

        LoginControlador loginCtrl = new LoginControlador(new AutenticacionServicio(loginDao));

        // 1. Inicio de sesión como Administrador
        Optional<Usuario> adminOpt = loginCtrl.autenticar("info@angelsifuentes.com", "admin");
        assertTrue("El Administrador debe poder autenticarse", adminOpt.isPresent());
        Usuario admin = adminOpt.get();
        assertEquals("Administrador", admin.getRol());

        PoliticaAcceso politicaAdmin = new PoliticaAcceso(admin);
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasDao, politicaAdmin));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosDao, politicaAdmin));

        Path dirPdfTemp = Files.createTempDirectory("e2e-pdf-test-");
        try {
            GeneradorPdfPedido generadorPdf = new GeneradorPdfPedido(dirPdfTemp);
            PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                    pedidosDao::verPedido,
                    pedidosDao::verPedidoDetalle,
                    loginDao::datosEmpresa,
                    generadorPdf,
                    archivo -> { /* Apertura simulada */ }
            );

            ConsultaPedidosServicio consultaPedidos = new ConsultaPedidosServicio(pedidosDao, politicaAdmin);
            PedidosControlador pedidosCtrl = new PedidosControlador(
                    new PedidoServicio(pedidosDao),
                    pdfServicio,
                    politicaAdmin,
                    consultaPedidos
            );

            // 2. Consulta de configuración de empresa
            Config config = loginDao.datosEmpresa();
            assertNotNull(config.getNombre());
            assertNotNull(config.getRuc());

            // 3. Crear Sala y Menú del Día
            String sufijo = UUID.randomUUID().toString().substring(0, 6);
            String nombreSala = "SALA E2E " + sufijo;
            assertTrue(salasCtrl.registrar(new Salas(0, nombreSala, 10)));

            List<Salas> salas = salasCtrl.listar();
            Salas salaCreada = salas.stream()
                    .filter(s -> nombreSala.equals(s.getNombre()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Sala no encontrada"));

            String hoy = LocalDate.now().toString();
            Platos plato1 = new Platos(0, "Plato E2E A " + sufijo, new BigDecimal("25.00"), hoy);
            Platos plato2 = new Platos(0, "Plato E2E B " + sufijo, new BigDecimal("10.00"), hoy);
            assertTrue(platosCtrl.registrar(plato1));
            assertTrue(platosCtrl.registrar(plato2));

            // 4. Registrar Pedido completo
            DetallePedido det1 = new DetallePedido(0, plato1.getNombre(), plato1.getPrecioDecimal(), 2, "Sin sal", 0);
            DetallePedido det2 = new DetallePedido(0, plato2.getNombre(), plato2.getPrecioDecimal(), 1, "Normal", 0);
            BigDecimal totalEsperado = new BigDecimal("60.00"); // 25*2 + 10*1 = 60

            Pedidos pedido = new Pedidos(0, salaCreada.getId(), 4, hoy, totalEsperado, salaCreada.getNombre(), admin.getNombre(), "PENDIENTE");
            int idPedido = pedidosCtrl.registrarPedidoCompleto(pedido, Arrays.asList(det1, det2));
            assertTrue("ID de pedido debe ser positivo", idPedido > 0);

            // 5. Conflicto de concurrencia: no se puede duplicar pedido pendiente en la misma mesa
            Pedidos pedidoConflicto = new Pedidos(0, salaCreada.getId(), 4, hoy, new BigDecimal("10.00"), salaCreada.getNombre(), "Mozo2", "PENDIENTE");
            assertThrows(PedidoPendienteExistenteException.class,
                    () -> pedidosCtrl.registrarPedidoCompleto(pedidoConflicto, Arrays.asList(det2)));

            // 6. Consulta de pedido activo
            Pedidos pedidoActivo = pedidosCtrl.verPedido(idPedido);
            assertEquals("PENDIENTE", pedidoActivo.getEstado());
            assertEquals(4, pedidoActivo.getNum_mesa());
            List<DetallePedido> detalles = pedidosCtrl.verPedidoDetalle(idPedido);
            assertEquals(2, detalles.size());

            // 7. Finalización del pedido
            assertTrue(pedidosCtrl.finalizarPedido(idPedido));
            assertEquals("FINALIZADO", pedidosCtrl.verPedido(idPedido).getEstado());

            // 8. Generación de PDF
            pedidosCtrl.generarPdfPedido(idPedido);
            Path pdfGenerado = dirPdfTemp.resolve("pedido-" + idPedido + ".pdf");
            assertTrue(Files.isRegularFile(pdfGenerado));
            assertTrue(Files.size(pdfGenerado) > 500);

            // 9. Permisos RBAC para rol Asistente
            String correoAsistente = "asistente-" + sufijo + "@restaurante.test";
            Usuario asistente = new Usuario(0, "Mozo E2E", correoAsistente, "clave-asistente", "Asistente");
            assertTrue(loginDao.Registrar(asistente));

            Optional<Usuario> authAsistente = loginCtrl.autenticar(correoAsistente, "clave-asistente");
            assertTrue(authAsistente.isPresent());
            PoliticaAcceso politicaAsistente = new PoliticaAcceso(authAsistente.get());

            assertTrue(politicaAsistente.permite(PoliticaAcceso.Accion.CONSULTAR_SALAS));
            assertTrue(politicaAsistente.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));
            assertFalse(politicaAsistente.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));
            assertFalse(politicaAsistente.permite(PoliticaAcceso.Accion.EDITAR_CONFIGURACION));
            assertFalse(politicaAsistente.permite(PoliticaAcceso.Accion.GESTIONAR_SALAS));

        } finally {
            File[] files = dirPdfTemp.toFile().listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            Files.deleteIfExists(dirPdfTemp);
        }
    }
}

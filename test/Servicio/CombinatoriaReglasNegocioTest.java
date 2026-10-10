package Servicio;

import Modelo.AutenticacionRepositorio;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Matriz combinatoria exhaustiva de reglas de negocio, límites y valores anómalos.
 */
public class CombinatoriaReglasNegocioTest {

    // ==========================================
    // 1. MATRIZ DE ROLES Y PERMISOS (RBAC)
    // ==========================================
    @Test
    public void matrizRolesYPermisosCompleta() {
        Usuario admin = new Usuario(1, "Admin", "admin@rest.com", "pass", "Administrador");
        Usuario asistente = new Usuario(2, "Asistente", "asist@rest.com", "pass", "Asistente");

        PoliticaAcceso rbacAdmin = new PoliticaAcceso(admin);
        PoliticaAcceso rbacAsistente = new PoliticaAcceso(asistente);

        for (PoliticaAcceso.Accion accion : PoliticaAcceso.Accion.values()) {
            assertTrue("Administrador debe tener permiso para " + accion, rbacAdmin.permite(accion));
        }

        assertTrue(rbacAsistente.permite(PoliticaAcceso.Accion.CONSULTAR_SALAS));
        assertTrue(rbacAsistente.permite(PoliticaAcceso.Accion.CONSULTAR_PLATOS));
        assertTrue(rbacAsistente.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));

        assertFalse(rbacAsistente.permite(PoliticaAcceso.Accion.GESTIONAR_SALAS));
        assertFalse(rbacAsistente.permite(PoliticaAcceso.Accion.GESTIONAR_PLATOS));
        assertFalse(rbacAsistente.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS));
        assertFalse(rbacAsistente.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));
        assertFalse(rbacAsistente.permite(PoliticaAcceso.Accion.EDITAR_CONFIGURACION));
    }

    @Test
    public void matrizRolesInvalidosRechazoInmediato() {
        String[] rolesInvalidos = {null, "", "  ", "Root", "Superuser", "Cajero", "Invitado", "ADMIN", "administrador"};
        for (String rol : rolesInvalidos) {
            assertThrows("El rol '" + rol + "' debe ser rechazado",
                    ErrorAplicacionException.class,
                    () -> new PoliticaAcceso(new Usuario(99, "Test", "t@t.com", "pass", rol)));
        }
    }

    // ==========================================
    // 2. MATRIZ DE PRECIOS Y PLATOS
    // ==========================================
    @Test
    public void matrizPreciosPlatoLimitesYNegativos() {
        PoliticaAcceso rbac = new PoliticaAcceso(new Usuario(1, "A", "a@a.com", "p", "Administrador"));
        PlatosServicio servicio = new PlatosServicio(new RepositorioPlatosMemoria(), rbac);

        BigDecimal[] preciosValidos = {
                new BigDecimal("0.01"),
                new BigDecimal("1.00"),
                new BigDecimal("15.50"),
                new BigDecimal("99999999.99")
        };
        for (BigDecimal precio : preciosValidos) {
            Platos plato = new Platos(0, "Plato Valido " + precio, precio, "2026-10-05");
            assertTrue("Precio válido " + precio + " debe aceptarse", servicio.registrar(plato));
        }

        assertThrows("Precio null debe ser rechazado en constructor",
                ErrorAplicacionException.class,
                () -> new Platos(0, "Plato Invalido", null, "2026-10-05"));

        BigDecimal[] preciosInvalidos = {
                BigDecimal.ZERO,
                new BigDecimal("-0.01"),
                new BigDecimal("-100.00"),
                new BigDecimal("100000000.00"),
                new BigDecimal("12.345") // scale > 2
        };
        for (BigDecimal precio : preciosInvalidos) {
            Platos plato = new Platos(0, "Plato Invalido", precio, "2026-10-05");
            assertThrows("Precio inválido " + precio + " debe lanzar excepción",
                    ErrorAplicacionException.class,
                    () -> servicio.registrar(plato));
        }
    }

    @Test
    public void matrizFechasPlatoValidasEInvalidas() {
        PoliticaAcceso rbac = new PoliticaAcceso(new Usuario(1, "A", "a@a.com", "p", "Administrador"));
        PlatosServicio servicio = new PlatosServicio(new RepositorioPlatosMemoria(), rbac);

        String[] fechasValidas = {"2026-01-01", "2026-12-31", "2024-02-29"}; // 2024 bisiesto
        for (String fecha : fechasValidas) {
            Platos plato = new Platos(0, "Plato", new BigDecimal("10.00"), fecha);
            assertTrue("Fecha válida " + fecha + " debe ser aceptada", servicio.registrar(plato));
        }

        String[] fechasInvalidas = {null, "", "   ", "2023-02-29", "2026-13-01", "2026-04-31", "05-10-2026", "2026/10/05", "ayer"};
        for (String fecha : fechasInvalidas) {
            Platos plato = new Platos(0, "Plato", new BigDecimal("10.00"), fecha);
            assertThrows("Fecha inválida " + fecha + " debe ser rechazada",
                    ErrorAplicacionException.class,
                    () -> servicio.registrar(plato));
        }
    }

    // ==========================================
    // 3. MATRIZ DE SALAS Y MESAS
    // ==========================================
    @Test
    public void matrizMesasSalasLimites() {
        PoliticaAcceso rbac = new PoliticaAcceso(new Usuario(1, "A", "a@a.com", "p", "Administrador"));
        SalasServicio servicio = new SalasServicio(new RepositorioSalasMemoria(), rbac);

        int[] mesasValidas = {1, 5, 20, 100, 999};
        for (int mesas : mesasValidas) {
            Salas sala = new Salas(0, "Sala Mesas " + mesas, mesas);
            assertTrue("Mesa válida " + mesas + " debe ser aceptada", servicio.registrar(sala));
        }

        int[] mesasInvalidas = {0, -1, -5, -100};
        for (int mesas : mesasInvalidas) {
            Salas sala = new Salas(0, "Sala Mesas Invalida", mesas);
            assertThrows("Mesa inválida " + mesas + " debe ser rechazada",
                    ErrorAplicacionException.class,
                    () -> servicio.registrar(sala));
        }
    }

    @Test
    public void matrizNombresSalaInvalidos() {
        PoliticaAcceso rbac = new PoliticaAcceso(new Usuario(1, "A", "a@a.com", "p", "Administrador"));
        SalasServicio servicio = new SalasServicio(new RepositorioSalasMemoria(), rbac);

        String[] nombresInvalidos = {null, "", "   ", "\t\n"};
        for (String nombre : nombresInvalidos) {
            Salas sala = new Salas(0, nombre, 10);
            assertThrows("Nombre de sala inválido debe ser rechazado",
                    ErrorAplicacionException.class,
                    () -> servicio.registrar(sala));
        }
    }

    // ==========================================
    // 4. MATRIZ DE PEDIDOS Y CONVERSIONES
    // ==========================================
    @Test
    public void matrizConversionBimonetariaYRedondeo() {
        BigDecimal tasa = new BigDecimal("36.5000");
        BigDecimal totalUSD = new BigDecimal("17.50");
        BigDecimal totalBs = totalUSD.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("638.75"), totalBs);

        // Caso con fracción de céntimo que requiere redondeo HALF_UP
        BigDecimal tasaImpar = new BigDecimal("36.5432");
        BigDecimal usdCentavo = new BigDecimal("10.05");
        BigDecimal bsCalculado = usdCentavo.multiply(tasaImpar).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("367.26"), bsCalculado);
    }

    @Test
    public void matrizDetallesPedidoCantidadesYSumas() {
        PedidoServicio servicio = new PedidoServicio(new RepositorioPedidosMemoria());

        // Pedido con múltiples líneas y cantidades combinadas
        List<DetallePedido> detalles = Arrays.asList(
                new DetallePedido(1, "Pabellón", new BigDecimal("12.00"), 2, "Sin cebolla", 0),
                new DetallePedido(2, "Jugo Natural", new BigDecimal("3.50"), 3, "De parchita", 0),
                new DetallePedido(3, "Postre Tres Leches", new BigDecimal("4.00"), 1, "", 0)
        );
        // Total esperado: 12*2 + 3.50*3 + 4*1 = 24.00 + 10.50 + 4.00 = 38.50
        BigDecimal totalEsperado = new BigDecimal("38.50");

        Pedidos pedidoValido = new Pedidos(0, 1, 4, "2026-10-05", totalEsperado, "Sala Principal", "Admin", "PENDIENTE");
        int idPedido = servicio.registrarPedidoCompleto(pedidoValido, detalles);
        assertTrue("Pedido con suma coincidente debe registrarse", idPedido > 0);

        // Suma intencionalmente alterada por 0.01 centavo
        Pedidos pedidoAlterado = new Pedidos(0, 1, 4, "2026-10-05", new BigDecimal("38.51"), "Sala Principal", "Admin", "PENDIENTE");
        assertThrows("Suma con discrepancia debe rechazarse",
                ErrorAplicacionException.class,
                () -> servicio.registrarPedidoCompleto(pedidoAlterado, detalles));
    }

    // ==========================================
    // REPOSITORIOS EN MEMORIA PARA TESTS
    // ==========================================
    private static class RepositorioPlatosMemoria implements PlatosRepositorio {
        private final List<Platos> base = new ArrayList<>();
        @Override public boolean registrar(Platos plato) { return base.add(plato); }
        @Override public List<Platos> listarPorFecha(String nombre, String fecha) { return base; }
        @Override public boolean eliminar(int id) { return true; }
        @Override public boolean modificar(Platos plato) { return true; }
    }

    private static class RepositorioSalasMemoria implements SalasRepositorio {
        private final List<Salas> base = new ArrayList<>();
        @Override public boolean registrar(Salas sala) { return base.add(sala); }
        @Override public List<Salas> listar() { return base; }
        @Override public boolean eliminar(int id) { return true; }
        @Override public boolean modificar(Salas sala) { return true; }
    }

    private static class RepositorioPedidosMemoria implements PedidosRepositorio {
    public boolean actualizarPedidoCompleto(int id, Pedidos p, java.util.List<DetallePedido> d) { return true; }
        private int contador = 1;
        @Override public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) { return contador++; }
        @Override public Pedidos verPedido(int idPedido) { return null; }
        @Override public List<DetallePedido> verPedidoDetalle(int idPedido) { return null; }
        @Override public int verificarStado(int mesa, int idSala) { return 0; }
        @Override public boolean actualizarEstado(int idPedido) { return true; }
        @Override public List<DetallePedido> finalizarPedido(int idPedido) { return null; }
        @Override public List<Pedidos> listarPedidos() { return null; }
    }
}

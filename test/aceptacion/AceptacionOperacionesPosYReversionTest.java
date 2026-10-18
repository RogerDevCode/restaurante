package aceptacion;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import Vista.Sistema;
import infraestructura.ProveedorConexionJdbc;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Suite de Aceptación del Red Team para Operaciones POS, Comandas, Reversión Financiera
 * y Ciclo de Vida de Pedidos Rápidos (Fallos F-53 a F-58 / RT-09 a RT-14).
 */
public class AceptacionOperacionesPosYReversionTest {

    private ProveedorConexionJdbc proveedor;
    private PedidosDao pedidosDao;
    private ClienteDao clienteDao;
    private Sistema sistema;

    @Before
    public void setUp() throws Exception {
        System.setProperty("test.headless", "true");
        proveedor = new ProveedorConexionJdbc();
        pedidosDao = new PedidosDao(proveedor);
        clienteDao = new ClienteDao(proveedor);

        Usuario admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(admin);
        LoginDao empresaDao = new LoginDao();

        SalasDao salasDao = new SalasDao(proveedor);
        PlatosDao platosDao = new PlatosDao(proveedor);

        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                pedidosDao::verPedido,
                id -> pedidosDao.verPedidoDetalle(id),
                empresaDao::datosEmpresa,
                GeneradorPdfPedido.conEstructuraMensual(GeneradorPdfPedido.resolverDirectorioFacturasPorDefecto()),
                PedidoPdfServicio.AbridorPdf.porDefecto());
        Servicio.CierreCajaServicio cierreServicio = new Servicio.CierreCajaServicio(
                new Modelo.CierreCajaDao(), empresaDao::datosEmpresa, new Servicio.GeneradorPdfCierre());
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosDao), pdfServicio, politica,
                new ConsultaPedidosServicio(pedidosDao, politica), cierreServicio);

        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosDao, politica));
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasDao, politica));
        CategoriaControlador catCtrl = new CategoriaControlador(new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politica));
        FavoritoControlador favCtrl = new FavoritoControlador(new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politica));

        SwingUtilities.invokeAndWait(() -> {
            sistema = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, catCtrl, favCtrl, empresaDao);
            sistema.setVisible(true);
        });
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(() -> sistema.dispose());
        }
    }

    @Test
    public void testF53_LimpiezaCamposClienteEnFinalizar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField txtNombre = sistema.getTxtClienteNombreFac();
            JTextField txtDoc = sistema.getTxtClienteDocFac();
            assertNotNull("txtClienteNombreFac no debe ser nulo", txtNombre);
            assertNotNull("txtClienteDocFac no debe ser nulo", txtDoc);

            // Simular que el cajero escribió datos de Juan Pérez en la Mesa 1
            txtNombre.setText("Juan Pérez");
            txtDoc.setText("V-12345678");

            // Simular volver al inicio de selección de sala
            sistema.volverAInicioSeleccionSalaPublica();
            assertTrue("txtClienteNombreFac debe quedar limpio tras volver", txtNombre.getText().isEmpty());
            assertTrue("txtClienteDocFac debe quedar limpio tras volver", txtDoc.getText().isEmpty());

            // Simular cargar un pedido de consumidor final anónimo
            Pedidos pedidoAnonimo = new Pedidos();
            pedidoAnonimo.setId(9999);
            pedidoAnonimo.setClienteNombre("Consumidor Final");
            pedidoAnonimo.setClienteDocumento("V-00000000");
            pedidoAnonimo.setTotalDecimal(new BigDecimal("10.00"));
            pedidoAnonimo.setTotalBs(new BigDecimal("448.00"));
            pedidoAnonimo.setTasaCambio(new BigDecimal("44.8000"));
            pedidoAnonimo.setFecha(new java.sql.Timestamp(System.currentTimeMillis()).toString());

            sistema.mostrarPedidoEnPantallaPublica(pedidoAnonimo, List.of());
            assertTrue("Al cargar cliente anónimo, el campo de nombre debe estar vacío", txtNombre.getText().isEmpty());
            assertTrue("Al cargar cliente anónimo, el campo de doc debe estar vacío", txtDoc.getText().isEmpty());

            // Simular cargar un pedido que sí tiene cliente registrado
            Pedidos pedidoConCliente = new Pedidos();
            pedidoConCliente.setId(9998);
            pedidoConCliente.setClienteNombre("Empresa ACME C.A.");
            pedidoConCliente.setClienteDocumento("J-99999999-0");
            pedidoConCliente.setTotalDecimal(new BigDecimal("50.00"));
            pedidoConCliente.setTotalBs(new BigDecimal("2240.00"));
            pedidoConCliente.setTasaCambio(new BigDecimal("44.8000"));
            pedidoConCliente.setFecha(new java.sql.Timestamp(System.currentTimeMillis()).toString());

            sistema.mostrarPedidoEnPantallaPublica(pedidoConCliente, List.of());
            assertEquals("Debe cargar el nombre del cliente del pedido", "Empresa ACME C.A.", txtNombre.getText());
            assertEquals("Debe cargar el doc del cliente del pedido", "J-99999999-0", txtDoc.getText());
        });
    }

    @Test
    public void testF54_ConfirmacionComandaCocinaNoBloqueaHeadless() {
        // En modo test/headless no debe lanzar excepciones ni bloquear
        try {
            sistema.imprimirComandaCocinaSiHabilitada(999999, false);
        } catch (Exception ex) {
            fail("imprimirComandaCocinaSiHabilitada no debe arrojar excepción: " + ex.getMessage());
        }
    }

    @Test
    public void testF55_DashboardDesglosePagosYFacturasDelDia() throws Exception {
        EstadisticasDashboard stats = clienteDao.obtenerEstadisticasDashboard();
        assertNotNull("EstadisticasDashboard no debe ser nulo", stats);
        assertNotNull("Lista de pagos por método de hoy no debe ser nula", stats.getPagosPorMetodoHoy());

        List<Pedidos> facturasRecientes = clienteDao.listarFacturasRecientesDelDia();
        assertNotNull("listarFacturasRecientesDelDia no debe ser nulo", facturasRecientes);
    }

    @Test
    public void testF56_ResumenPagosAlFinalizarMetodos() {
        // Verificar que mostrarResumenCobroExitoso no arroje excepciones en modo test
        List<PagoPedido> pagos = List.of(
                new PagoPedido("EFECTIVO_USD", "USD", new BigDecimal("10.00")),
                new PagoPedido("PAGO_MOVIL", "VES", new BigDecimal("224.00"))
        );
        try {
            sistema.mostrarResumenCobroExitosoPublico(1234, "Mesa 1", "USD $ 15.00 | Bs. 672.00", pagos);
        } catch (Exception ex) {
            fail("mostrarResumenCobroExitoso no debe arrojar excepción: " + ex.getMessage());
        }
    }

    @Test
    public void testF57_AnulacionFacturaCobradaConContraAsientosAtómicos() throws Exception {
        // 1. Crear un pedido cobrado de prueba en MySQL
        int idPedido = -1;
        long idPago1 = -1;
        long idPago2 = -1;
        try (Connection con = proveedor.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement psPed = con.prepareStatement(
                    "INSERT INTO pedidos (id_sala, num_mesa, tipo_pedido, subtotal, iva_porcentaje, iva_monto, total, " +
                    "subtotal_bs, iva_bs, total_bs, tasa_cambio, usuario, estado, cliente_nombre, cliente_documento, metodo_pago) " +
                    "VALUES (1, 99, 'MESA', 20.00, 16.00, 3.20, 23.20, 896.00, 143.36, 1039.36, 44.8000, 'admin', 'FINALIZADO', 'Cliente Prueba Anulacion', 'V-11111111', 'MIXTO')",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                psPed.executeUpdate();
                try (ResultSet rs = psPed.getGeneratedKeys()) {
                    if (rs.next()) idPedido = rs.getInt(1);
                }
            }

            assertTrue("idPedido debe ser generado", idPedido > 0);

            // 2. Insertar 2 pagos en pedido_pagos
            try (PreparedStatement psPago = con.prepareStatement(
                    "INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd, monto_recibido, monto_aplicado, cambio_devuelto, es_legado) " +
                    "VALUES (?, ?, ?, ?, 44.8000, ?, ?, ?, ?, 0.00, 0)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                // Pago 1: $ 10.00 Efectivo USD
                psPago.setInt(1, idPedido);
                psPago.setString(2, "EFECTIVO_USD");
                psPago.setString(3, "USD");
                psPago.setBigDecimal(4, new BigDecimal("10.00"));
                psPago.setBigDecimal(5, new BigDecimal("448.00"));
                psPago.setBigDecimal(6, new BigDecimal("10.00"));
                psPago.setBigDecimal(7, new BigDecimal("10.00"));
                psPago.setBigDecimal(8, new BigDecimal("10.00"));
                psPago.executeUpdate();
                try (ResultSet rs = psPago.getGeneratedKeys()) {
                    if (rs.next()) idPago1 = rs.getLong(1);
                }

                // Pago 2: Bs. 591.36 Pago Móvil (equivale a $ 13.20)
                psPago.setInt(1, idPedido);
                psPago.setString(2, "PAGO_MOVIL");
                psPago.setString(3, "VES");
                psPago.setBigDecimal(4, new BigDecimal("591.36"));
                psPago.setBigDecimal(5, new BigDecimal("591.36"));
                psPago.setBigDecimal(6, new BigDecimal("13.20"));
                psPago.setBigDecimal(7, new BigDecimal("591.36"));
                psPago.setBigDecimal(8, new BigDecimal("591.36"));
                psPago.executeUpdate();
                try (ResultSet rs = psPago.getGeneratedKeys()) {
                    if (rs.next()) idPago2 = rs.getLong(1);
                }
            }
            con.commit();
        }

        // 3. Ejecutar anulación con auditoría. ANTES lanzaba ErrorAplicacionException bloqueando al usuario.
        // AHORA debe anular con éxito y generar automáticamente los contra-asientos.
        boolean anulado = pedidosDao.anularPedidoConAuditoria(idPedido, "Error en factura de prueba", "admin_qa");
        assertTrue("anularPedidoConAuditoria debe retornar true", anulado);

        // 4. Verificar en MySQL que el pedido esté ANULADO
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement psVerif = con.prepareStatement("SELECT estado FROM pedidos WHERE id = ?")) {
                psVerif.setInt(1, idPedido);
                try (ResultSet rs = psVerif.executeQuery()) {
                    assertTrue("Debe existir el pedido", rs.next());
                    assertEquals("Estado del pedido debe ser ANULADO", "ANULADO", rs.getString("estado"));
                }
            }

            // 5. Verificar que se hayan insertado contra-asientos en pedido_saldos_movimientos
            try (PreparedStatement psMov = con.prepareStatement(
                    "SELECT tipo, moneda, monto_origen FROM pedido_saldos_movimientos WHERE id_pedido = ? AND tipo = 'ANULACION'")) {
                psMov.setInt(1, idPedido);
                try (ResultSet rs = psMov.executeQuery()) {
                    int cont = 0;
                    while (rs.next()) {
                        cont++;
                        assertEquals("Tipo debe ser ANULACION", "ANULACION", rs.getString("tipo"));
                    }
                    assertEquals("Deben haberse creado exactamente 2 contra-asientos (1 por cada pago)", 2, cont);
                }
            }

            // 6. Verificar que se haya registrado en auditoria_pedidos
            try (PreparedStatement psAud = con.prepareStatement(
                    "SELECT accion, motivo, usuario FROM auditoria_pedidos WHERE id_pedido = ? AND accion = 'ANULACION'")) {
                psAud.setInt(1, idPedido);
                try (ResultSet rs = psAud.executeQuery()) {
                    assertTrue("Debe existir registro en auditoria_pedidos", rs.next());
                    assertTrue("Motivo debe coincidir", rs.getString("motivo").contains("Error en factura de prueba"));
                    assertEquals("Usuario debe ser admin_qa", "admin_qa", rs.getString("usuario"));
                }
            }

            // Limpieza de datos de prueba
            try (PreparedStatement psDel1 = con.prepareStatement("DELETE FROM pedido_saldos_movimientos WHERE id_pedido = ?");
                 PreparedStatement psDel2 = con.prepareStatement("DELETE FROM auditoria_pedidos WHERE id_pedido = ?");
                 PreparedStatement psDel3 = con.prepareStatement("DELETE FROM pedido_pagos WHERE id_pedido = ?");
                 PreparedStatement psDel4 = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                psDel1.setInt(1, idPedido);
                psDel1.executeUpdate();
                psDel2.setInt(1, idPedido);
                psDel2.executeUpdate();
                psDel3.setInt(1, idPedido);
                psDel3.executeUpdate();
                psDel4.setInt(1, idPedido);
                psDel4.executeUpdate();
            }
        }
    }

    @Test
    public void testF58_RecuperacionYCobroPedidoRapidoPendiente() throws Exception {
        // Crear un pedido rápido pendiente
        int idPedidoRapido = -1;
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pedidos (id_sala, num_mesa, tipo_pedido, subtotal, iva_porcentaje, iva_monto, total, " +
                    "subtotal_bs, iva_bs, total_bs, tasa_cambio, usuario, estado, cliente_nombre, cliente_documento) " +
                    "VALUES (NULL, NULL, 'RAPIDO', 10.00, 16.00, 1.60, 11.60, 448.00, 71.68, 519.68, 44.8000, 'admin', 'PENDIENTE', 'Cliente Mostrador', 'V-00000000')",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) idPedidoRapido = rs.getInt(1);
                }
            }
        }

        assertTrue("idPedidoRapido debe ser generado", idPedidoRapido > 0);

        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement psDet = con.prepareStatement(
                    "INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido, aplica_iva, id_plato, moneda_base, precio_incluye_iva, iva_porcentaje) " +
                    "VALUES ('Plato Test Rapido', 10.00, 1, '', ?, 1, NULL, 'USD', 0, 16.00)")) {
                psDet.setInt(1, idPedidoRapido);
                psDet.executeUpdate();
            }
        }

        final int idTest = idPedidoRapido;
        SwingUtilities.invokeAndWait(() -> {
            // Simular carga de pedido rápido con permitirFinalizar = true (como ahora se pasa al detectar PENDIENTE)
            sistema.cargarPedidoEnPantallaPublica(idTest, true);
        });

        // Esperar a que PedidoEnPantallaSwingWorker complete en el EDT
        JButton btnFinalizar = sistema.getBtnFinalizar();
        assertNotNull("btnFinalizar no debe ser nulo", btnFinalizar);
        long limite = System.currentTimeMillis() + 5000;
        while (!btnFinalizar.isEnabled() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertTrue("btnFinalizar debe estar habilitado para pedidos pendientes", btnFinalizar.isEnabled());

        // Limpieza
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement psDelDet = con.prepareStatement("DELETE FROM detalle_pedidos WHERE id_pedido = ?");
                 PreparedStatement psDelPed = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                psDelDet.setInt(1, idTest);
                psDelDet.executeUpdate();
                psDelPed.setInt(1, idTest);
                psDelPed.executeUpdate();
            }
        }
    }
}

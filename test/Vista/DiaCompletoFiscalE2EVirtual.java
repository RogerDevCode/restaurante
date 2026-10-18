package Vista;

import java.awt.Component;
import java.awt.Window;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Simula un día de servicio en la UI real dentro de Xvfb, con teclado y mouse de Robot. */
public final class DiaCompletoFiscalE2EVirtual {
    private static final BigDecimal TASA_INICIAL = DiaE2EFixture.TASA_INICIAL;
    private static final BigDecimal TASA_NUEVA = DiaE2EFixture.TASA_NUEVA;
    private static final BigDecimal IVA_INICIAL = DiaE2EFixture.IVA_INICIAL;
    private static final BigDecimal IVA_NUEVO = DiaE2EFixture.IVA_NUEVO;
    private static int fase;

    private record PagoUi(String metodo, String moneda, String monto) {}
    private record PedidoUi(String id, String sala, String mesa, String total, String estado, String metodo) {}
    private record TotalesUi(BigDecimal usd, BigDecimal ves) {}

    private DiaCompletoFiscalE2EVirtual() {}

    public static void main(String[] args) throws Exception {
        DiaE2EFixture.exigirBdAislada();
        DiaE2EFixture.Fixture fixture = DiaE2EFixture.nuevo();
        Path directorioE2E = Path.of(System.getenv().getOrDefault("E2E_WORKDIR", System.getProperty("java.io.tmpdir")));
        System.setProperty("user.home", directorioE2E.toString());
        System.setProperty("modo.automatizado", "true");
        System.setProperty("PDF_FACTURAS_DIR", directorioE2E.resolve("facturas").toString());
        Throwable fallo = null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            DiaE2ERobot robot = new DiaE2ERobot();
            restaurante.Restaurante.lanzarLogin();
            Vista.FrmLogin login = robot.esperarVentana(Vista.FrmLogin.class);
            maximizar(login);
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            Sistema sistema = robot.esperarVentana(Sistema.class);
            maximizar(sistema);
            robot.esperar(() -> sistema.isShowing() && sistema.getBtnAtencionDirecta().isShowing(),
                    "Sistema autenticado disponible en Xvfb");
            jornada(sistema, robot, fixture);
            fase = 99;
        } catch (Throwable error) {
            fallo = error;
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (Window ventana : Window.getWindows()) ventana.dispose();
                });
            } catch (Throwable errorLimpieza) {
                if (fallo == null) fallo = errorLimpieza;
                else fallo.addSuppressed(errorLimpieza);
            }
        }
        if (fallo != null) {
            System.err.println("E2E_FALLO(" + fase + ") " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            throw new AssertionError("Falló la fase E2E " + fase, fallo);
        }
        System.out.println("E2E_OK");
    }

    private static void jornada(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture) throws Exception {
        fase = 1;
        actualizarTasaEIvaPorInterfaz(sistema, robot, TASA_INICIAL, IVA_INICIAL);
        crearSalaPorInterfaz(sistema, robot, fixture);
        crearProductosPorInterfaz(sistema, robot, fixture, IVA_INICIAL);

        fase = 2;
        int combinado = crearPedidoCombinadoInicial(sistema, robot, fixture);
        verificarPedidoEnHistorial(sistema, robot, fixture, combinado,
                "Bs. 2096.00", "$ 52.40", "PENDIENTE");
        verificarDetallesEnPantalla(sistema, robot, combinado, fixture, 1, IVA_INICIAL, TASA_INICIAL,
                Map.of("EX_USD", 1, "EX_VES", 1, "MAS_USD", 1, "MAS_VES", 1, "INC_USD", 1, "INC_VES", 1));

        fase = 3;
        segundaRonda(sistema, robot, fixture, combinado);
        Map<String, PedidoUi> individuales = crearPedidosIndividuales(sistema, robot, fixture);

        fase = 4;
        cobrarSoloVesYSoloUsd(sistema, robot, individuales);
        PedidoUi rechazo = individuales.get("MAS_USD");
        rechazarMontosIncorrectos(sistema, robot, rechazo);
        finalizarCobro(sistema, robot, rechazo, List.of(
                new PagoUi("EFECTIVO_USD", "USD", "11.60")));
        List<PedidoUi> facturasCongeladas = List.of(
                pedidoActual(sistema, robot, Integer.parseInt(individuales.get("EX_VES").id())),
                pedidoActual(sistema, robot, Integer.parseInt(individuales.get("EX_USD").id())),
                pedidoActual(sistema, robot, Integer.parseInt(individuales.get("MAS_USD").id())));

        fase = 5;
        actualizarTasaEIvaPorInterfaz(sistema, robot, TASA_NUEVA, IVA_NUEVO);
        verificarFacturasPagadasCongeladas(sistema, robot, facturasCongeladas);
        PedidoUi combinadoRecalculado = pedidoActual(sistema, robot, combinado);
        exigir(contieneMonto(combinadoRecalculado.total(), "Bs. 2738.40")
                        && contieneMonto(combinadoRecalculado.total(), "$ 65.20"),
                "el historial muestra la cuenta abierta recalculada a tasa 42.0000");
        verificarDetallesEnPantalla(sistema, robot, combinado, fixture, 1, IVA_INICIAL, TASA_NUEVA,
                Map.of("EX_USD", 2, "EX_VES", 1, "MAS_USD", 1, "MAS_VES", 2, "INC_USD", 1, "INC_VES", 1));

        finalizarCobro(sistema, robot, individuales.get("MAS_VES"), List.of(
                new PagoUi("EFECTIVO_BS", "VES", "464.00")));
        finalizarCobro(sistema, robot, individuales.get("INC_USD"), List.of(
                new PagoUi("EFECTIVO_USD", "USD", "11.60")));
        finalizarCobro(sistema, robot, individuales.get("INC_VES"), List.of(
                new PagoUi("EFECTIVO_BS", "VES", "464.00")));

        fase = 6;
        finalizarCobro(sistema, robot, pedidoActual(sistema, robot, combinado), List.of(
                new PagoUi("EFECTIVO_BS", "VES", "1369.20"),
                new PagoUi("EFECTIVO_USD", "USD", "32.60")));

        fase = 7;
        PedidoUi posterior = crearPedidoPosteriorCambio(sistema, robot, fixture);
        verificarDetallesEnPantalla(sistema, robot, Integer.parseInt(posterior.id()), fixture, 2, IVA_NUEVO, TASA_NUEVA,
                Map.of("MAS_USD", 1, "INC_VES", 1));
        finalizarCobro(sistema, robot, posterior, List.of(
                new PagoUi("EFECTIVO_BS", "VES", "926.00")));

        fase = 8;
        PedidoUi fuga = crearPedidoDeFuga(sistema, robot, fixture);
        anularCuentaAbierta(sistema, robot, fuga);

        fase = 9;
        verificarJornadaEnHistorial(sistema, robot, fuga);
        generarCierreZ(sistema, robot);
        verificarJornadaEnHistorial(sistema, robot, fuga);
    }

    private static void actualizarTasaEIvaPorInterfaz(Sistema sistema, DiaE2ERobot robot,
            BigDecimal tasa, BigDecimal iva) throws Exception {
        robot.clic((Component) robot.campo(sistema, "btnConfig"));
        int indiceConfig = sistema.getJTabbedPane1().indexOfTab("Configuración");
        if (indiceConfig < 0) indiceConfig = 6;
        int tab = indiceConfig;
        robot.esperar(() -> sistema.getJTabbedPane1().getSelectedIndex() == tab,
                "pestaña de configuración visible");

        JTextField campoTasa = (JTextField) robot.campo(sistema, "txtTasaConfig");
        JTextField campoIva = (JTextField) robot.campo(sistema, "txtIvaConfig");
        JTextField campoNombre = (JTextField) robot.campo(sistema, "txtNombreConfig");
        robot.esperar(() -> !campoNombre.getText().trim().isEmpty()
                        && campoTasa.getText().matches("[0-9]+(?:\\.[0-9]+)?")
                        && campoIva.getText().matches("[0-9]+(?:\\.[0-9]+)?"),
                "ficha de configuración cargada desde la interfaz");
        robot.reemplazarTexto(campoTasa, tasa.toPlainString());
        robot.reemplazarTexto(campoIva, iva.toPlainString());
        exigir(decimalIgual(robot.texto(campoTasa), tasa) && decimalIgual(robot.texto(campoIva), iva),
                "campos de tasa e IVA reciben los valores por teclado");
        JComboBox<?> salida = (JComboBox<?>) robot.campo(sistema, "cbModoSalidaConfig");
        robot.seleccionarCombo(salida, "Tickera Térmica (Directa 80mm)");
        JCheckBox imprimirComanda = (JCheckBox) robot.campo(sistema, "chkImprimirComandaCocina");
        if (robot.edt(imprimirComanda::isSelected)) robot.clic(imprimirComanda);
        robot.clic((Component) robot.campo(sistema, "btnActualizarConfig"));
        robot.confirmarDialogo("Confirmar cambio de tasa global");
        robot.esperar(() -> decimalIgual(campoTasa.getText(), tasa)
                        && decimalIgual(campoIva.getText(), iva),
                "configuración visible con tasa " + tasa + " e IVA " + iva);
        JComboBox<?> modoGlobal = sistema.getCbModoAtencionGlobal();
        if (modoGlobal != null && !"Opción B (Mapa + Pestañas)".equals(robot.edt(modoGlobal::getSelectedItem))) {
            robot.seleccionarCombo(modoGlobal, "Opción B (Mapa + Pestañas)");
            robot.esperar(() -> "OPCION_B".equals(sistema.getModoAtencionActual()),
                    "modo de atención Opción B activo");
        }
        System.out.println("OK   la UI muestra tasa " + tasa + " e IVA " + iva);
    }

    private static void crearSalaPorInterfaz(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture) throws Exception {
        robot.clic((Component) robot.campo(sistema, "btnSala"));
        robot.clic((Component) robot.campo(sistema, "btnNuevoSala"));
        JTextField nombre = (JTextField) robot.campo(sistema, "txtNombreSala");
        JTextField mesas = (JTextField) robot.campo(sistema, "txtMesas");
        robot.reemplazarTexto(nombre, fixture.nombreSala());
        robot.reemplazarTexto(mesas, "8");
        robot.clic((Component) robot.campo(sistema, "btnRegistrarSala"));
        JTable tabla = (JTable) robot.campo(sistema, "tableSala");
        robot.esperar(() -> filaPorTexto(tabla, 1, fixture.nombreSala()) >= 0,
                "sala de ocho mesas registrada desde el formulario");
        int fila = robot.filaTablaModelo(tabla, 1, fixture.nombreSala());
        String cantidad = String.valueOf(robot.edt(() -> tabla.getModel().getValueAt(
                tabla.convertRowIndexToModel(fila), 2)));
        exigir("8".equals(cantidad), "la sala de la jornada muestra ocho mesas en la tabla de salas");
    }

    private static void crearProductosPorInterfaz(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture, BigDecimal iva) throws Exception {
        robot.clic((Component) robot.campo(sistema, "btnPlatos"));
        JTable tabla = (JTable) robot.campo(sistema, "TablePlatos");
        for (String clave : DiaE2EFixture.CLAVES) {
            DiaE2EFixture.ProductoFiscal producto = fixture.productos().get(clave);
            robot.clic((Component) robot.campo(sistema, "btnNuevoPlato"));
            JTextField buscar = (JTextField) robot.campo(sistema, "txtBuscarAdminPlatos");
            robot.reemplazarTexto(buscar, "");
            JTextField nombre = (JTextField) robot.campo(sistema, "txtNombrePlato");
            JTextField precio = (JTextField) robot.campo(sistema, "txtPrecioPlato");
            robot.reemplazarTexto(nombre, producto.nombre());
            JComboBox<?> moneda = (JComboBox<?>) robot.campo(sistema, "cbMonedaBasePlato");
            robot.seleccionarCombo(moneda, producto.moneda());
            robot.reemplazarTexto(precio, producto.precio().toPlainString());
            JCheckBox aplica = (JCheckBox) robot.campo(sistema, "chkAplicaIvaPlato");
            JCheckBox incluye = (JCheckBox) robot.campo(sistema, "chkPrecioIncluyeIvaPlato");
            if (robot.edt(aplica::isSelected) != producto.aplicaIva()) robot.clic(aplica);
            if (robot.edt(incluye::isSelected) != producto.incluyeIva()) robot.clic(incluye);
            comprobarDesgloseFicha(sistema, robot, producto, iva);
            robot.clic((Component) robot.campo(sistema, "btnGuardarPlato"));
            robot.esperar(() -> filaPorTexto(tabla, 1, producto.nombre()) >= 0,
                    "producto " + clave + " guardado desde ficha");

            robot.reemplazarTexto(buscar, producto.nombre());
            robot.esperar(() -> filaPorTexto(tabla, 1, producto.nombre()) >= 0,
                    "producto " + clave + " filtrado en el catálogo");
            int fila = robot.filaTablaModelo(tabla, 1, producto.nombre());
            int columnaVisible = robot.edt(() -> tabla.getColumnCount() > 0 ? 0 : -1);
            if (columnaVisible < 0) throw new AssertionError("La tabla de platos no tiene columnas visibles.");
            robot.clicCelda(tabla, fila, columnaVisible);
            exigir(producto.moneda().equalsIgnoreCase(String.valueOf(robot.edt(moneda::getSelectedItem))),
                    clave + " conserva su moneda principal en la ficha visible");
            exigir(decimalIgual(robot.texto(precio), producto.precio()),
                    clave + " conserva el precio principal en la ficha visible");
            exigir(robot.edt(aplica::isSelected) == producto.aplicaIva(),
                    clave + " conserva la bandera Aplica IVA");
            exigir(robot.edt(incluye::isSelected) == producto.incluyeIva(),
                    clave + " conserva la bandera Incluye IVA");
        }
    }

    private static void comprobarDesgloseFicha(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.ProductoFiscal producto, BigDecimal iva) throws Exception {
        JLabel precioFinal = (JLabel) robot.campo(sistema, "lblFichaPrecioFinal");
        JLabel base = (JLabel) robot.campo(sistema, "lblFichaBaseImponible");
        JLabel montoIva = (JLabel) robot.campo(sistema, "lblFichaIvaMonto");
        String finalTexto = robot.texto(precioFinal);
        String baseTexto = robot.texto(base);
        String ivaTexto = robot.texto(montoIva);
        String precio = producto.precio().toPlainString();
        if (!producto.aplicaIva()) {
            exigir(baseTexto.contains("EXENTO DE IVA") && ivaTexto.contains("0.00"),
                    producto.clave() + " se presenta como exento en la ficha");
            exigir(finalTexto.contains(precio), producto.clave() + " muestra su precio exento sin recargo");
            return;
        }
        if (producto.incluyeIva()) {
            BigDecimal baseEsperada = producto.precio().multiply(new BigDecimal("100"))
                    .divide(new BigDecimal("100").add(iva), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal ivaEsperado = producto.precio().subtract(baseEsperada);
            exigir(baseTexto.contains(baseEsperada.toPlainString()),
                    producto.clave() + " muestra la base neta del precio con IVA incluido");
            exigir(ivaTexto.contains(ivaEsperado.toPlainString()),
                    producto.clave() + " muestra el IVA extraído del precio incluido");
            exigir(finalTexto.contains(precio), producto.clave() + " no suma IVA dos veces al precio incluido");
        } else {
            BigDecimal ivaEsperado = producto.precio().multiply(iva)
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal totalEsperado = producto.precio().add(ivaEsperado);
            exigir(baseTexto.contains(producto.precio().toPlainString()),
                    producto.clave() + " muestra la base antes de IVA");
            exigir(ivaTexto.contains(ivaEsperado.toPlainString()),
                    producto.clave() + " muestra el IVA añadido a la base");
            exigir(finalTexto.contains(totalEsperado.toPlainString()),
                    producto.clave() + " muestra precio final con IVA añadido una sola vez");
        }
    }

    private static int crearPedidoCombinadoInicial(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture) throws Exception {
        robot.seleccionarMesa(sistema, fixture.nombreSala(), 1);
        for (String clave : DiaE2EFixture.CLAVES) robot.seleccionarPlato(sistema, fixture.productos().get(clave).nombre());
        JTable carrito = (JTable) robot.campo(sistema, "tableMenu");
        verificarCarrito(robot, carrito, fixture,
                Map.of("EX_USD", 1, "EX_VES", 1, "MAS_USD", 1, "MAS_VES", 1, "INC_USD", 1, "INC_VES", 1), IVA_INICIAL);
        robot.clic((Component) robot.campo(sistema, "btnGenerarPedido"));
        PedidoUi pedido = esperarPedido(sistema, robot, fixture, 1, "PENDIENTE");
        return Integer.parseInt(pedido.id());
    }

    private static void segundaRonda(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture, int idPedido) throws Exception {
        robot.seleccionarMesa(sistema, fixture.nombreSala(), 1);
        JTextField idVisible = (JTextField) robot.campo(sistema, "txtIdPedido");
        robot.esperar(() -> String.valueOf(idPedido).equals(idVisible.getText().trim()),
                "mesa ocupada recupera la cuenta de la primera ronda");
        robot.clic((Component) robot.campo(sistema, "btnModificarPedido"));
        JTable carrito = (JTable) robot.campo(sistema, "tableMenu");
        robot.esperar(() -> carrito.getRowCount() == 6, "edición recupera las seis líneas anteriores");
        robot.seleccionarPlato(sistema, fixture.productos().get("EX_USD").nombre());
        robot.seleccionarPlato(sistema, fixture.productos().get("MAS_VES").nombre());
        verificarCarrito(robot, carrito, fixture,
                Map.of("EX_USD", 2, "EX_VES", 1, "MAS_USD", 1, "MAS_VES", 2, "INC_USD", 1, "INC_VES", 1), IVA_INICIAL);
        robot.clic((Component) robot.campo(sistema, "btnGenerarPedido"));
        PedidoUi pedido = esperarPedido(sistema, robot, fixture, 1, "PENDIENTE");
        exigir(pedido.id().equals(String.valueOf(idPedido)), "la segunda ronda conserva el mismo folio de cuenta");
        exigir(contieneMonto(pedido.total(), "Bs. 2680.00") && contieneMonto(pedido.total(), "$ 67.00"),
                "segunda ronda incrementa la cuenta en vez de abrir una nueva");
        verificarDetallesEnPantalla(sistema, robot, idPedido, fixture, 1, IVA_INICIAL, TASA_INICIAL,
                Map.of("EX_USD", 2, "EX_VES", 1, "MAS_USD", 1, "MAS_VES", 2, "INC_USD", 1, "INC_VES", 1));
    }

    private static Map<String, PedidoUi> crearPedidosIndividuales(Sistema sistema,
            DiaE2ERobot robot, DiaE2EFixture.Fixture fixture) throws Exception {
        Map<String, PedidoUi> pedidos = new LinkedHashMap<>();
        for (int i = 0; i < DiaE2EFixture.CLAVES.length; i++) {
            String clave = DiaE2EFixture.CLAVES[i];
            DiaE2EFixture.ProductoFiscal producto = fixture.productos().get(clave);
            int mesa = i + 2;
            robot.seleccionarMesa(sistema, fixture.nombreSala(), mesa);
            robot.seleccionarPlato(sistema, producto.nombre());
            verificarCarrito(robot, (JTable) robot.campo(sistema, "tableMenu"), fixture,
                    Map.of(clave, 1), IVA_INICIAL);
            robot.clic((Component) robot.campo(sistema, "btnGenerarPedido"));
            PedidoUi pedido = esperarPedido(sistema, robot, fixture, mesa, "PENDIENTE");
            exigir(montoPerfil(clave, pedido.total()), clave + " da el total esperado en su moneda equivalente");
            verificarDetallesEnPantalla(sistema, robot, Integer.parseInt(pedido.id()), fixture, mesa, IVA_INICIAL, TASA_INICIAL,
                    Map.of(clave, 1));
            pedidos.put(clave, pedido);
        }
        return Map.copyOf(pedidos);
    }

    private static void cobrarSoloVesYSoloUsd(Sistema sistema, DiaE2ERobot robot,
            Map<String, PedidoUi> pedidos) throws Exception {
        finalizarCobro(sistema, robot, pedidos.get("EX_VES"), List.of(
                new PagoUi("EFECTIVO_BS", "VES", "120.00")));
        finalizarCobro(sistema, robot, pedidos.get("EX_USD"), List.of(
                new PagoUi("EFECTIVO_USD", "USD", "3.00")));
        verificarPagosEnPantalla(sistema, robot, pedidos.get("EX_VES").id(), 3,
                Set.of("EFECTIVO_BS|VES|120.00"));
        verificarPagosEnPantalla(sistema, robot, pedidos.get("EX_USD").id(), 2,
                Set.of("EFECTIVO_USD|USD|3.00"));
    }

    private static void rechazarMontosIncorrectos(Sistema sistema, DiaE2ERobot robot,
            PedidoUi pedido) throws Exception {
        robot.abrirPedidoEnHistorial(sistema, Integer.parseInt(pedido.id()), Integer.parseInt(pedido.mesa()));
        JTable pagos = sistema.getTablaPagosFac();
        robot.seleccionarMedioPago(pagos, 0, "EFECTIVO_USD");
        robot.reemplazarCelda(pagos, 0, 2, "11.59");
        robot.clic((Component) sistema.getBtnFinalizar());
        JLabel alerta = (JLabel) robot.campo(sistema, "lblAlertaPagosFac");
        robot.esperar(() -> alerta.getText() != null && alerta.getText().startsWith("Falta"),
                "la UI señala el pago por debajo del saldo");
        exigir(robot.edt(() -> sistema.getBtnFinalizar().isEnabled()),
                "el pago corto deja la cuenta abierta para corregirla");

        robot.reemplazarCelda(pagos, 0, 2, "11.61");
        robot.clic((Component) sistema.getBtnFinalizar());
        robot.esperar(() -> alerta.getText() != null && alerta.getText().startsWith("Excede"),
                "la UI señala el pago por encima del saldo");
        exigir(robot.edt(() -> sistema.getBtnFinalizar().isEnabled()),
                "el pago excedido deja la cuenta abierta para corregirla");

        robot.reemplazarCelda(pagos, 0, 2, "11.60");
        System.out.println("OK   validación visible de pago corto y pago excedido antes del monto exacto");
    }

    private static void finalizarCobro(Sistema sistema, DiaE2ERobot robot,
            PedidoUi pedido, List<PagoUi> pagos) throws Exception {
        robot.abrirPedidoEnHistorial(sistema, Integer.parseInt(pedido.id()), Integer.parseInt(pedido.mesa()));
        prepararPagos(sistema, robot, pagos);
        robot.clic((Component) sistema.getBtnFinalizar());
        int tabMesas = sistema.getJTabbedPane1().indexOfTab("Mesas");
        robot.esperar(() -> sistema.getJTabbedPane1().getSelectedIndex() == tabMesas,
                "la UI vuelve al mapa después de finalizar el cobro");
        PedidoUi actualizado = esperarPedidoPorId(sistema, robot,
                Integer.parseInt(pedido.id()), "FINALIZADO");
        exigir(actualizado.id().equals(pedido.id()), "pedido " + pedido.id() + " figura FINALIZADO en el historial");
        verificarPagosEnPantalla(sistema, robot, pedido.id(), Integer.parseInt(pedido.mesa()),
                firmasPago(pagos));
    }

    private static void prepararPagos(Sistema sistema, DiaE2ERobot robot,
            List<PagoUi> pagos) throws Exception {
        if (pagos.isEmpty()) throw new IllegalArgumentException("El cobro requiere al menos un medio.");
        JTable tabla = sistema.getTablaPagosFac();
        for (int i = 1; i < pagos.size(); i++) {
            robot.clic((Component) robot.campo(sistema, "btnAgregarPagoFac"));
        }
        robot.esperar(() -> tabla.getRowCount() == pagos.size(), "filas de pago disponibles");
        for (int i = 0; i < pagos.size(); i++) {
            PagoUi pago = pagos.get(i);
            if (!"EFECTIVO_BS".equals(pago.metodo()) || i > 0) robot.seleccionarMedioPago(tabla, i, pago.metodo());
            int fila = i;
            robot.esperar(() -> pago.moneda().equals(String.valueOf(tabla.getValueAt(fila, 1))),
                    "moneda de pago " + pago.moneda() + " en fila " + (fila + 1));
            robot.reemplazarCelda(tabla, i, 2, pago.monto());
        }
    }

    private static PedidoUi crearPedidoPosteriorCambio(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture) throws Exception {
        robot.seleccionarMesa(sistema, fixture.nombreSala(), 2);
        robot.seleccionarPlato(sistema, fixture.productos().get("MAS_USD").nombre());
        robot.seleccionarPlato(sistema, fixture.productos().get("INC_VES").nombre());
        verificarCarrito(robot, (JTable) robot.campo(sistema, "tableMenu"), fixture,
                Map.of("MAS_USD", 1, "INC_VES", 1), IVA_NUEVO);
        robot.clic((Component) robot.campo(sistema, "btnGenerarPedido"));
        PedidoUi pedido = esperarPedido(sistema, robot, fixture, 2, "PENDIENTE");
        exigir(contieneMonto(pedido.total(), "Bs. 926.00") && contieneMonto(pedido.total(), "$ 22.04"),
                "el historial muestra el pedido nuevo calculado con IVA 10% y tasa 42");
        return pedido;
    }

    private static PedidoUi crearPedidoDeFuga(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture) throws Exception {
        robot.seleccionarMesa(sistema, fixture.nombreSala(), 8);
        robot.seleccionarPlato(sistema, fixture.productos().get("EX_VES").nombre());
        robot.clic((Component) robot.campo(sistema, "btnGenerarPedido"));
        PedidoUi fuga = esperarPedido(sistema, robot, fixture, 8, "PENDIENTE");
        exigir(contieneMonto(fuga.total(), "Bs. 120.00"), "la cuenta para cancelar aparece pendiente en el historial");
        return fuga;
    }

    private static void anularCuentaAbierta(Sistema sistema, DiaE2ERobot robot,
            PedidoUi fuga) throws Exception {
        robot.abrirPedidoEnHistorial(sistema, Integer.parseInt(fuga.id()), Integer.parseInt(fuga.mesa()));
        robot.clic((Component) robot.campo(sistema, "btnAnularFuga"));
        robot.llenarDialogoInput("Anular por Fuga / No Pagó", "Cliente se retiro sin pagar");
        robot.confirmarDialogo("Confirmar No-Pago (Fuga)");
        PedidoUi anulada = esperarPedidoPorId(sistema, robot, Integer.parseInt(fuga.id()), "ANULADO");
        exigir(anulada.estado().equals("ANULADO"), "la UI registra la anulación por fuga");
        exigir(anulada.metodo().isBlank() || "—".equals(anulada.metodo()),
                "el historial de fuga no muestra un método de cobro");
        robot.mostrarSalaEnMapa(sistema, fuga.sala());
        JPanelMesaLibre.comprobar(robot, sistema, Integer.parseInt(fuga.mesa()));
    }

    private static void verificarJornadaEnHistorial(Sistema sistema, DiaE2ERobot robot,
            PedidoUi fuga) throws Exception {
        robot.irAHistorialPedidos(sistema);
        JButton btnHoy = (JButton) robot.campo(sistema, "btnFiltroHoy");
        robot.esperar(() -> btnHoy.isShowing() && btnHoy.getWidth() > 0 && btnHoy.getHeight() > 0,
                "botón de filtro Hoy visible en historial");
        JTextField desde = (JTextField) robot.campo(sistema, "txtFiltroFechaDesde");
        String fechaHoy = LocalDate.now().toString();
        if (!fechaHoy.equals(robot.texto(desde))) {
            robot.clic(btnHoy);
        }
        JTable tabla = sistema.getTablePedidos();
        robot.esperar(() -> tabla.getRowCount() == 9, "el filtro de hoy muestra las nueve cuentas de la jornada");
        List<PedidoUi> filas = robot.edt(() -> {
            List<PedidoUi> resultado = new java.util.ArrayList<>();
            int colSala = columna(tabla, "Sala", 1);
            int colMesa = columna(tabla, "N° Mesa", 3);
            int colTotal = columna(tabla, "Total", 5);
            int colEstado = columna(tabla, "Estado", 6);
            int colMetodo = columna(tabla, "Método de Pago", 7);
            for (int vista = 0; vista < tabla.getRowCount(); vista++) {
                int modelo = tabla.convertRowIndexToModel(vista);
                resultado.add(new PedidoUi(
                        String.valueOf(tabla.getModel().getValueAt(modelo, 0)),
                        String.valueOf(tabla.getModel().getValueAt(modelo, colSala)),
                        String.valueOf(tabla.getModel().getValueAt(modelo, colMesa)).trim(),
                        String.valueOf(tabla.getModel().getValueAt(modelo, colTotal)),
                        String.valueOf(tabla.getModel().getValueAt(modelo, colEstado)),
                        String.valueOf(tabla.getModel().getValueAt(modelo, colMetodo)).trim()));
            }
            return resultado;
        });
        int finalizados = 0;
        int anulados = 0;
        int pendientes = 0;
        BigDecimal totalFinalizadoBs = BigDecimal.ZERO.setScale(2);
        for (PedidoUi pedido : filas) {
            String estado = pedido.estado();
            if ("FINALIZADO".equals(estado)) {
                finalizados++;
                totalFinalizadoBs = totalFinalizadoBs.add(extraerBs(pedido.total()));
            } else if ("ANULADO".equals(estado)) {
                anulados++;
                exigir(fuga.sala().equalsIgnoreCase(pedido.sala()) && fuga.mesa().equals(pedido.mesa()),
                        "la única cuenta anulada corresponde a la mesa de fuga");
            } else if ("PENDIENTE".equals(estado)) {
                pendientes++;
            }
        }
        exigir(finalizados == 8, "el historial de hoy muestra ocho facturas finalizadas");
        exigir(anulados == 1, "el historial de hoy muestra una cuenta anulada");
        exigir(pendientes == 0, "el historial de hoy no deja cuentas pendientes");
        monto("suma visible de facturas finalizadas", "5783.60", totalFinalizadoBs);
        System.out.println("OK   la UI concilia los importes de la jornada en bolívares");
    }

    private static void generarCierreZ(Sistema sistema, DiaE2ERobot robot) throws Exception {
        JButton btnCierre = (JButton) robot.campo(sistema, "btnCierreTotal");
        robot.esperar(() -> btnCierre.isShowing() && btnCierre.getWidth() > 0 && btnCierre.getHeight() > 0,
                "botón de Cierre Total visible en pantalla de pedidos");
        robot.clic(btnCierre);
        robot.clicarBotonEnDialogo("CORTE Z - CIERRE TOTAL", "Imprimir Directo");
        robot.esperar(() -> sistema.getTitle() != null
                        && sistema.getTitle().contains("Cierre CORTE Z - CIERRE TOTAL procesado"),
                "la banda de la aplicación confirma el cierre Z procesado");
        exigir(robot.edt(() -> sistema.getTitle().contains("impresora térmica")),
                "la salida del cierre usa la impresora simulada del entorno virtual");
        System.out.println("OK   cierre Z solicitado desde la interfaz de caja");
    }

    private static void verificarDetallesEnPantalla(Sistema sistema, DiaE2ERobot robot,
            int idPedido, DiaE2EFixture.Fixture fixture, int mesa, BigDecimal iva, BigDecimal tasa,
            Map<String, Integer> cantidades) throws Exception {
        robot.abrirPedidoEnHistorial(sistema, idPedido, mesa);
        JLabel total = (JLabel) robot.campo(sistema, "totalFinalizar");
        TotalesUi esperados = calcularTotalesEsperados(fixture, cantidades, iva, tasa);
        String textoTotal = robot.texto(total);
        exigir(contieneMonto(textoTotal, "$ " + esperados.usd().toPlainString())
                        && contieneMonto(textoTotal, "Bs. " + esperados.ves().toPlainString()),
                "pantalla de cuenta muestra los totales esperados con la matriz fiscal y tasa activa");

        JLabel desglose = (JLabel) robot.campo(sistema, "lblDesgloseFiscalFinalizar");
        String textoDesglose = robot.texto(desglose);
        boolean tieneIva = cantidades.keySet().stream().anyMatch(k -> fixture.productos().get(k).aplicaIva());
        boolean tieneExento = cantidades.keySet().stream().anyMatch(k -> !fixture.productos().get(k).aplicaIva());
        if (tieneIva) {
            String porcentaje = String.format(java.util.Locale.US, "%.2f", iva);
            exigir(textoDesglose.contains("IVA (" + porcentaje + "%)"),
                    "desglose fiscal de la cuenta muestra IVA " + porcentaje + "%");
        }
        if (tieneExento) {
            exigir(textoDesglose.contains("Exento:"), "desglose fiscal de la cuenta conserva el exento");
        }

        JTable detalles = (JTable) robot.campo(sistema, "tableFinalizar");
        robot.esperar(() -> detalles.getRowCount() == cantidades.size(),
                "detalle visible con " + cantidades.size() + " perfiles fiscales");
        for (Map.Entry<String, Integer> entrada : cantidades.entrySet()) {
            String nombre = fixture.productos().get(entrada.getKey()).nombre();
            DiaE2EFixture.ProductoFiscal producto = fixture.productos().get(entrada.getKey());
            int fila = robot.filaTablaModelo(detalles, 1, nombre);
            exigir(fila >= 0, entrada.getKey() + " aparece en el detalle de la cuenta");
            int cantidad = Integer.parseInt(String.valueOf(robot.edt(() -> detalles.getModel().getValueAt(
                    detalles.convertRowIndexToModel(fila), 2))));
            exigir(cantidad == entrada.getValue(), entrada.getKey() + " muestra cantidad " + cantidad + " en pantalla");
            String subtotalVisible = String.valueOf(robot.edt(() -> detalles.getModel().getValueAt(
                    detalles.convertRowIndexToModel(fila), 4)));
            BigDecimal cantidadDecimal = BigDecimal.valueOf(entrada.getValue());
            BigDecimal subtotalFuente = producto.aplicaIva() && producto.incluyeIva()
                    ? producto.precio().multiply(cantidadDecimal).multiply(new BigDecimal("100"))
                            .divide(new BigDecimal("100").add(iva), 2, java.math.RoundingMode.HALF_UP)
                    : producto.precio().multiply(cantidadDecimal).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal subtotalUsd = "USD".equals(producto.moneda())
                    ? subtotalFuente
                    : subtotalFuente.divide(tasa, 2, java.math.RoundingMode.HALF_UP);
            BigDecimal subtotalVes = "VES".equals(producto.moneda())
                    ? subtotalFuente
                    : subtotalFuente.multiply(tasa).setScale(2, java.math.RoundingMode.HALF_UP);
            exigir(subtotalVisible.contains("$ " + subtotalUsd.toPlainString())
                            && subtotalVisible.contains("Bs. " + subtotalVes.toPlainString()),
                    entrada.getKey() + " muestra subtotal fiscal esperado en USD y bolívares");
            int colIva = columna(detalles, "IVA", -1);
            if (colIva >= 0) {
                String etiqueta = String.valueOf(robot.edt(() -> detalles.getModel().getValueAt(
                        detalles.convertRowIndexToModel(fila), colIva)));
                boolean exento = entrada.getKey().startsWith("EX_");
                exigir(exento ? etiqueta.contains("Exento") : etiqueta.contains(iva.stripTrailingZeros().toPlainString()),
                        entrada.getKey() + " muestra su condición de IVA en el detalle");
            }
        }
    }

    private static void verificarCarrito(DiaE2ERobot robot, JTable carrito,
            DiaE2EFixture.Fixture fixture, Map<String, Integer> cantidades,
            BigDecimal iva) throws Exception {
        robot.esperar(() -> carrito.getRowCount() == cantidades.size(), "líneas esperadas en el carrito");
        for (Map.Entry<String, Integer> entrada : cantidades.entrySet()) {
            DiaE2EFixture.ProductoFiscal producto = fixture.productos().get(entrada.getKey());
            int fila = robot.filaTablaModelo(carrito, 1, producto.nombre());
            exigir(fila >= 0, entrada.getKey() + " aparece en el carrito");
            int filaModelo = robot.edt(() -> carrito.convertRowIndexToModel(fila));
            int cantidad = Integer.parseInt(String.valueOf(robot.edt(() -> carrito.getModel().getValueAt(filaModelo, 2))));
            exigir(cantidad == entrada.getValue(), entrada.getKey() + " tiene cantidad " + cantidad + " en carrito");
            String precioVisible = String.valueOf(robot.edt(() -> carrito.getModel().getValueAt(filaModelo, 3)));
            exigir(precioVisible.contains(producto.moneda().equals("VES") ? "Bs." : "$ ")
                            && precioVisible.contains(producto.precio().toPlainString()),
                    entrada.getKey() + " conserva moneda y precio base visibles en el carrito");
            if (carrito.getModel().getColumnCount() > 6) {
                String ivaVisible = String.valueOf(robot.edt(() -> carrito.getModel().getValueAt(filaModelo, 6)));
                exigir(producto.aplicaIva()
                                ? ivaVisible.contains(iva.stripTrailingZeros().toPlainString())
                                : ivaVisible.contains("Exento"),
                        entrada.getKey() + " muestra la condición fiscal actual en el carrito");
            }
        }
    }

    private static void verificarPagosEnPantalla(Sistema sistema, DiaE2ERobot robot,
            String id, int mesa, Set<String> firmasEsperadas) throws Exception {
        robot.abrirPedidoEnHistorial(sistema, Integer.parseInt(id), mesa);
        JTable tabla = sistema.getTablaPagosFac();
        robot.esperar(() -> tabla.getRowCount() == firmasEsperadas.size(),
                "detalle de pagos visible al consultar factura #" + id);
        Set<String> firmadas = new LinkedHashSet<>();
        for (int i = 0; i < tabla.getRowCount(); i++) {
            int fila = i;
            firmadas.add(robot.edt(() -> String.valueOf(tabla.getValueAt(fila, 0)) + "|"
                    + String.valueOf(tabla.getValueAt(fila, 1)) + "|"
                    + normalizarMonto(String.valueOf(tabla.getValueAt(fila, 2)))));
        }
        exigir(firmadas.equals(firmasEsperadas),
                "la factura #" + id + " muestra los medios/importes " + firmadas);
        JLabel alerta = (JLabel) robot.campo(sistema, "lblAlertaPagosFac");
        robot.esperar(() -> alerta.getText() != null && alerta.getText().contains("finalizado"),
                "estado finalizado visible junto a los pagos de #" + id);
    }

    private static void verificarFacturasPagadasCongeladas(Sistema sistema,
            DiaE2ERobot robot, List<PedidoUi> antes) throws Exception {
        for (PedidoUi factura : antes) {
            PedidoUi despues = pedidoActual(sistema, robot, Integer.parseInt(factura.id()));
            exigir(factura.total().equals(despues.total())
                            && factura.estado().equals(despues.estado())
                            && factura.metodo().equals(despues.metodo()),
                    "factura pagada #" + factura.id() + " mantiene total, estado y método tras cambio de tasa");
        }
    }

    private static void verificarPedidoEnHistorial(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture, int id, String totalBs, String totalUsd,
            String estado) throws Exception {
        PedidoUi pedido = pedidoActual(sistema, robot, id);
        exigir(fixture.nombreSala().equalsIgnoreCase(pedido.sala()), "el historial mantiene la sala fixture");
        exigir(estado.equals(pedido.estado()), "la cuenta aparece " + estado + " en el historial");
        exigir(contieneMonto(pedido.total(), totalBs) && contieneMonto(pedido.total(), totalUsd),
                "el historial muestra " + totalUsd + " y " + totalBs);
    }

    private static PedidoUi pedidoActual(Sistema sistema, DiaE2ERobot robot, int id) throws Exception {
        robot.irAHistorialPedidos(sistema);
        JTable tabla = sistema.getTablePedidos();
        robot.esperar(() -> filaPorTexto(tabla, 0, String.valueOf(id)) >= 0,
                "pedido #" + id + " visible en historial");
        return leerPedido(tabla, robot.filaTablaModelo(tabla, 0, String.valueOf(id)), robot);
    }

    private static PedidoUi esperarPedido(Sistema sistema, DiaE2ERobot robot,
            DiaE2EFixture.Fixture fixture, int mesa, String estado) throws Exception {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            robot.irAHistorialPedidos(sistema);
            JTable tabla = sistema.getTablePedidos();
            int fila = robot.edt(() -> filaPedido(tabla, fixture.nombreSala(), mesa, estado));
            if (fila >= 0) return leerPedido(tabla, fila, robot);
            Thread.sleep(200);
        }
        throw new AssertionError("Timeout: pedido de mesa " + mesa + " con estado " + estado + " visible en el historial");
    }

    private static PedidoUi esperarPedidoPorId(Sistema sistema, DiaE2ERobot robot,
            int id, String estado) throws Exception {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            PedidoUi pedido = pedidoActual(sistema, robot, id);
            if (estado.equals(pedido.estado())) return pedido;
            Thread.sleep(200);
        }
        throw new AssertionError("Timeout: pedido #" + id + " no llegó al estado " + estado);
    }

    private static PedidoUi leerPedido(JTable tabla, int filaVista, DiaE2ERobot robot) throws Exception {
        int filaModelo = robot.edt(() -> tabla.convertRowIndexToModel(filaVista));
        int colSala = columna(tabla, "Sala", 1);
        int colMesa = columna(tabla, "N° Mesa", 3);
        int colTotal = columna(tabla, "Total", 5);
        int colEstado = columna(tabla, "Estado", 6);
        int colMetodo = columna(tabla, "Método de Pago", 7);
        return new PedidoUi(
                String.valueOf(robot.edt(() -> tabla.getModel().getValueAt(filaModelo, 0))),
                String.valueOf(robot.edt(() -> tabla.getModel().getValueAt(filaModelo, colSala))),
                String.valueOf(robot.edt(() -> tabla.getModel().getValueAt(filaModelo, colMesa))).trim(),
                String.valueOf(robot.edt(() -> tabla.getModel().getValueAt(filaModelo, colTotal))),
                String.valueOf(robot.edt(() -> tabla.getModel().getValueAt(filaModelo, colEstado))),
                String.valueOf(robot.edt(() -> tabla.getModel().getValueAt(filaModelo, colMetodo))).trim());
    }

    private static void maximizar(JFrame ventana) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ventana.setExtendedState(JFrame.MAXIMIZED_BOTH);
            ventana.setVisible(true);
        });
    }

    private static int filaPorTexto(JTable tabla, int columnaModelo, String valor) {
        for (int filaVista = 0; filaVista < tabla.getRowCount(); filaVista++) {
            int modelo = tabla.convertRowIndexToModel(filaVista);
            if (valor.equalsIgnoreCase(String.valueOf(tabla.getModel().getValueAt(modelo, columnaModelo)))) return filaVista;
        }
        return -1;
    }

    private static int filaPedido(JTable tabla, String sala, int mesa, String estado) {
        int cSala = columna(tabla, "Sala", 1);
        int cMesa = columna(tabla, "N° Mesa", 3);
        int cEstado = columna(tabla, "Estado", 6);
        for (int filaVista = 0; filaVista < tabla.getRowCount(); filaVista++) {
            int modelo = tabla.convertRowIndexToModel(filaVista);
            String actualSala = String.valueOf(tabla.getModel().getValueAt(modelo, cSala));
            String actualMesa = String.valueOf(tabla.getModel().getValueAt(modelo, cMesa)).trim();
            String actualEstado = String.valueOf(tabla.getModel().getValueAt(modelo, cEstado));
            if (sala.equalsIgnoreCase(actualSala) && String.valueOf(mesa).equals(actualMesa)
                    && estado.equalsIgnoreCase(actualEstado)) return filaVista;
        }
        return -1;
    }

    private static int columna(JTable tabla, String nombre, int defecto) {
        for (int indice = 0; indice < tabla.getModel().getColumnCount(); indice++) {
            if (nombre.equalsIgnoreCase(tabla.getModel().getColumnName(indice))) return indice;
        }
        return defecto;
    }

    private static TotalesUi calcularTotalesEsperados(DiaE2EFixture.Fixture fixture,
            Map<String, Integer> cantidades, BigDecimal iva, BigDecimal tasa) {
        BigDecimal subtotalUsd = BigDecimal.ZERO;
        BigDecimal ivaUsd = BigDecimal.ZERO;
        BigDecimal totalVes = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entrada : cantidades.entrySet()) {
            DiaE2EFixture.ProductoFiscal producto = fixture.productos().get(entrada.getKey());
            BigDecimal cantidad = BigDecimal.valueOf(entrada.getValue());
            BigDecimal precioLinea = producto.precio().multiply(cantidad).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal base = BigDecimal.ZERO.setScale(2);
            BigDecimal exento = BigDecimal.ZERO.setScale(2);
            BigDecimal impuesto = BigDecimal.ZERO.setScale(2);
            BigDecimal totalFuente = precioLinea;
            if (!producto.aplicaIva()) {
                exento = precioLinea;
            } else if (producto.incluyeIva()) {
                base = precioLinea.multiply(new BigDecimal("100"))
                        .divide(new BigDecimal("100").add(iva), 2, java.math.RoundingMode.HALF_UP);
                impuesto = precioLinea.subtract(base).setScale(2, java.math.RoundingMode.HALF_UP);
            } else {
                base = precioLinea;
                impuesto = base.multiply(iva).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                totalFuente = base.add(impuesto).setScale(2, java.math.RoundingMode.HALF_UP);
            }

            if ("USD".equals(producto.moneda())) {
                subtotalUsd = subtotalUsd.add(base).add(exento);
                ivaUsd = ivaUsd.add(impuesto);
                totalVes = totalVes.add(totalFuente.multiply(tasa).setScale(2, java.math.RoundingMode.HALF_UP));
            } else {
                subtotalUsd = subtotalUsd.add(base.divide(tasa, 2, java.math.RoundingMode.HALF_UP))
                        .add(exento.divide(tasa, 2, java.math.RoundingMode.HALF_UP));
                ivaUsd = ivaUsd.add(impuesto.divide(tasa, 2, java.math.RoundingMode.HALF_UP));
                totalVes = totalVes.add(totalFuente);
            }
        }
        BigDecimal totalUsd = subtotalUsd.setScale(2, java.math.RoundingMode.HALF_UP)
                .add(ivaUsd.setScale(2, java.math.RoundingMode.HALF_UP)).setScale(2, java.math.RoundingMode.HALF_UP);
        return new TotalesUi(totalUsd, totalVes.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private static boolean montoPerfil(String clave, String total) {
        boolean exento = clave.startsWith("EX_");
        if (exento) return contieneMonto(total, "Bs. 120.00") && contieneMonto(total, "$ 3.00");
        return contieneMonto(total, "Bs. 464.00") && contieneMonto(total, "$ 11.60");
    }

    private static Set<String> firmasPago(List<PagoUi> pagos) {
        Set<String> resultado = new LinkedHashSet<>();
        for (PagoUi pago : pagos) resultado.add(pago.metodo() + "|" + pago.moneda() + "|" + normalizarMonto(pago.monto()));
        return resultado;
    }

    private static boolean decimalIgual(String valor, BigDecimal esperado) {
        try { return new BigDecimal(valor.trim()).compareTo(esperado) == 0; }
        catch (RuntimeException error) { return false; }
    }

    private static boolean contieneMonto(String texto, String importe) {
        return texto != null && texto.replaceAll("\\s+", " ").contains(importe.replaceAll("\\s+", " "));
    }

    private static String normalizarMonto(String monto) {
        try { return new BigDecimal(monto.trim().replace(',', '.')).setScale(2).toPlainString(); }
        catch (RuntimeException error) { return monto.trim(); }
    }

    private static BigDecimal extraerBs(String texto) {
        int inicio = texto.indexOf("Bs.");
        if (inicio < 0) throw new AssertionError("El historial no muestra importe en VES: " + texto);
        int desde = inicio + 3;
        int hasta = texto.indexOf('(', desde);
        String valor = (hasta >= 0 ? texto.substring(desde, hasta) : texto.substring(desde)).trim();
        return new BigDecimal(valor.replace(",", ""));
    }

    private static void monto(String nombre, String esperado, BigDecimal real) {
        BigDecimal esperadoDecimal = new BigDecimal(esperado);
        exigir(real != null && esperadoDecimal.compareTo(real) == 0,
                nombre + " esperado=" + esperadoDecimal.toPlainString() + " real=" + real);
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
        System.out.println("OK   " + mensaje);
    }

    private static final class JPanelMesaLibre {
        static void comprobar(DiaE2ERobot robot, Sistema sistema, int mesa) throws Exception {
            javax.swing.JPanel panel = (javax.swing.JPanel) robot.campo(sistema, "PanelMesas");
            java.util.function.Predicate<JButton> criterio = boton -> {
                String tooltip = boton.getToolTipText() == null ? "" : boton.getToolTipText();
                String texto = boton.getText() == null ? "" : boton.getText().toUpperCase(java.util.Locale.ROOT);
                return tooltip.startsWith("Mesa " + mesa + " (") || texto.contains("MESA " + mesa);
            };
            robot.esperar(() -> buscarBoton(panel, criterio) != null,
                    "botón actualizado de la mesa " + mesa + " visible");
            JButton boton = robot.edt(() -> buscarBoton(panel, criterio));
            String tooltip = robot.edt(boton::getToolTipText);
            String texto = robot.edt(boton::getText);
            exigir((tooltip != null && tooltip.contains("Disponible"))
                            || (texto != null && texto.toUpperCase(java.util.Locale.ROOT).contains("LIBRE")),
                    "mesa " + mesa + " aparece libre en el mapa después de la fuga");
        }

        private static JButton buscarBoton(java.awt.Container raiz,
                java.util.function.Predicate<JButton> criterio) {
            for (Component componente : raiz.getComponents()) {
                if (componente instanceof JButton boton && criterio.test(boton)) return boton;
                if (componente instanceof java.awt.Container contenedor) {
                    JButton boton = buscarBoton(contenedor, criterio);
                    if (boton != null) return boton;
                }
            }
            return null;
        }
    }
}

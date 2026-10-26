package Vista;

import Controlador.PedidosControlador;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.PagoPedido;
import Modelo.Pedidos;
import Servicio.CalculoMonetarioPedido;
import Servicio.GestorBloqueoCobro;
import Servicio.PlanPagosCobro;
import Servicio.PlanPagosCobro.Estado;
import Servicio.PlanPagosCobro.Linea;
import Servicio.PlanPagosCobro.Metodo;
import Servicio.PlanPagosCobro.Resultado;
import Servicio.PlanPagosCobro.Saldo;
import Servicio.PoliticaAcceso;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo modal de cobro para restaurante, bi-moneda (USD base, Bs derivado).
 *
 * <p>Diseñado ergonómicamente para pantallas táctiles y resoluciones de 960×600 y 1024×600
 * (altura acotada a 525 px). Camino de 0 clics con Efectivo $ precargado (ENTER cobra).
 * Acceso directo a Pre-Factura y atajos F1-F6.</p>
 */
public class DialogoCobroPedido extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(DialogoCobroPedido.class.getName());

    private static final String CLIENTE_NOMBRE_DEFECTO = "Consumidor Final";
    private static final String CLIENTE_DOC_DEFECTO = "V-00000000";

    private static final Color COLOR_TEXTO = new Color(44, 62, 80);
    private static final Color COLOR_BORDE = new Color(218, 225, 231);
    private static final Border BORDE_ACTIVO = BorderFactory.createLineBorder(new Color(241, 196, 15), 3);

    private enum Nivel {
        INFO(new Color(44, 62, 80)),
        OK(new Color(30, 132, 73)),
        AVISO(new Color(183, 110, 0)),
        ERROR(new Color(192, 57, 43));

        final Color color;

        Nivel(Color color) {
            this.color = color;
        }
    }

    private record EstiloMetodo(String etiqueta, String nombreComponente, Color color, int tecla) { }

    private static final Map<Metodo, EstiloMetodo> ESTILOS = new EnumMap<>(Metodo.class);

    static {
        ESTILOS.put(Metodo.EFECTIVO_USD, new EstiloMetodo("💵 Efectivo $", "btnCobroEfectivoUsd", new Color(39, 174, 96), KeyEvent.VK_F2));
        ESTILOS.put(Metodo.EFECTIVO_BS, new EstiloMetodo("💵 Efectivo Bs", "btnCobroEfectivoBs", new Color(41, 128, 185), KeyEvent.VK_F3));
        ESTILOS.put(Metodo.TARJETA, new EstiloMetodo("💳 Punto / Tarjeta", "btnCobroTarjeta", new Color(52, 73, 94), KeyEvent.VK_F4));
        ESTILOS.put(Metodo.PAGO_MOVIL, new EstiloMetodo("📱 Pago Móvil", "btnCobroPagoMovil", new Color(142, 68, 173), KeyEvent.VK_F5));
        ESTILOS.put(Metodo.TRANSFERENCIA, new EstiloMetodo("🏦 Transferencia", "btnCobroTransferencia", new Color(22, 160, 133), KeyEvent.VK_F6));
        ESTILOS.put(Metodo.CORTESIA, new EstiloMetodo("🎁 Cortesía", "btnCobroCortesia", new Color(211, 84, 0), KeyEvent.VK_F7));
    }

    // ---------------------------------------------------------------- datos del pedido

    private final PedidosControlador controlador;
    private final int idPedido;
    private final Pedidos pedido;
    private final List<DetallePedido> detalles;
    private final GestorBloqueoCobro gestorBloqueo;
    private final int idUsuario;
    private final String nombreUsuario;
    private final Config configEmpresa;
    private final PoliticaAcceso politicaAcceso;
    private final Consumer<Boolean> callbackFinalizado;

    private final BigDecimal tasaCambio;
    private final boolean tasaValida;
    private final BigDecimal totalUsd;
    private final BigDecimal totalBs;
    private final CalculoMonetarioPedido.CalculoPedido calculo;

    // ---------------------------------------------------------------- estado del cobro

    private final PlanPagosCobro plan;
    private Metodo metodoActivo = Metodo.EFECTIVO_USD;
    private boolean enProcesamiento = false;

    // ---------------------------------------------------------------- componentes

    private JLabel lblTotalUsd;
    private JLabel lblTotalBs;
    private JLabel lblSaldo;
    private JPanel cardSaldo;
    private JPanel pnlLineas;
    private JLabel lblVueltoValor;
    private JLabel lblEstado;

    private JLabel lblClienteResumen;
    private JButton btnCambiarCliente;
    private JPanel pnlClienteEdicion;
    private JTextField txtClienteNombre;
    private JTextField txtClienteDoc;

    private final Map<Metodo, JButton> botonesMetodo = new EnumMap<>(Metodo.class);
    private final Map<Metodo, Border> bordesOriginales = new EnumMap<>(Metodo.class);
    private final Map<Metodo, Boolean> bordesPintados = new EnumMap<>(Metodo.class);
    private final JButton[] chipsSugeridos = new JButton[4];
    private final BigDecimal[] montosChip = new BigDecimal[4];
    private JButton btnChipExactoUsd;
    private JTextField txtMonto;

    private JButton btnPreFactura;
    private JButton btnMas;
    private JButton btnAyuda;
    private JPopupMenu menuMas;
    private JButton btnCancelar;
    private JButton btnConfirmarCobro;
    private JProgressBar progressBar;

    public DialogoCobroPedido(Frame owner, PedidosControlador controlador, int idPedido,
                              Pedidos pedido, List<DetallePedido> detalles, GestorBloqueoCobro gestorBloqueo,
                              int idUsuario, String nombreUsuario, Config configEmpresa,
                              PoliticaAcceso politicaAcceso, Consumer<Boolean> callbackFinalizado) {
        super(owner, "Cobro y Facturación · Pedido #" + idPedido, true);
        this.controlador = controlador;
        this.idPedido = idPedido;
        this.pedido = pedido;
        this.detalles = detalles != null ? List.copyOf(detalles) : List.of();
        this.gestorBloqueo = gestorBloqueo != null ? gestorBloqueo : new GestorBloqueoCobro();
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.configEmpresa = configEmpresa;
        this.politicaAcceso = politicaAcceso;
        this.callbackFinalizado = callbackFinalizado;

        BigDecimal tasaResuelta = resolverTasa(pedido, configEmpresa);
        this.tasaValida = tasaResuelta != null && tasaResuelta.signum() > 0;
        this.tasaCambio = (this.tasaValida ? tasaResuelta : BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);

        if (!this.detalles.isEmpty()) {
            this.calculo = CalculoMonetarioPedido.calcularPedido(this.detalles, this.tasaCambio);
            this.totalUsd = this.calculo.totalUsd().setScale(2, RoundingMode.HALF_UP);
        } else if (pedido != null && pedido.getTotalDecimal() != null) {
            this.calculo = null;
            this.totalUsd = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
        } else {
            this.calculo = null;
            this.totalUsd = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Anclaje al total_bs oficial de la BD (exigido por PedidosDao para ajusteCambioBs)
        if (pedido != null && pedido.getTotalBs() != null && pedido.getTotalBs().signum() > 0) {
            this.totalBs = pedido.getTotalBs().setScale(2, RoundingMode.HALF_UP);
        } else if (this.calculo != null && this.tasaValida) {
            this.totalBs = this.calculo.totalVes().setScale(2, RoundingMode.HALF_UP);
        } else if (this.tasaValida) {
            this.totalBs = this.totalUsd.multiply(this.tasaCambio).setScale(2, RoundingMode.HALF_UP);
        } else {
            this.totalBs = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        this.plan = new PlanPagosCobro(this.totalUsd, this.totalBs, this.tasaCambio, this.tasaValida);
        this.plan.precargarEfectivoUsd();

        construirUi();
        configurarAtajosTeclado();
        refrescar();
        estado("Efectivo $ por el total (precargado). ENTER cobra, o elija otro método / billete.", Nivel.INFO);
    }

    private static BigDecimal resolverTasa(Pedidos pedido, Config cfg) {
        BigDecimal t = pedido != null ? pedido.getTasaCambio() : null;
        if (t != null && t.signum() > 0) {
            return t;
        }
        t = cfg != null ? cfg.getTasaDolar() : null;
        return (t != null && t.signum() > 0) ? t : null;
    }

    // ================================================================= construcción de la UI

    private void construirUi() {
        setSize(920, 525);
        setMinimumSize(new Dimension(880, 500));
        setResizable(false);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!enProcesamiento) {
                    cancelarYCerrar();
                }
            }

            @Override
            public void windowOpened(WindowEvent e) {
                btnConfirmarCobro.requestFocusInWindow();
            }
        });

        JPanel principal = new JPanel(new BorderLayout(8, 8));
        principal.setBorder(new EmptyBorder(8, 12, 8, 12));
        principal.setBackground(new Color(245, 247, 250));

        principal.add(crearPanelCabecera(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 2, 10, 0));
        centro.setOpaque(false);
        centro.add(crearPanelIzquierdo());
        centro.add(crearPanelDerecho());
        principal.add(centro, BorderLayout.CENTER);

        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.setOpaque(false);
        lblEstado = etiqueta(" ", 11, Font.BOLD, COLOR_TEXTO);
        lblEstado.setName("lblCobroEstado");
        lblEstado.setBorder(new EmptyBorder(0, 2, 0, 2));
        sur.add(lblEstado, BorderLayout.NORTH);
        sur.add(crearBarraInferior(), BorderLayout.CENTER);
        principal.add(sur, BorderLayout.SOUTH);

        setContentPane(principal);
    }

    // ----------------------------------------------------------------- cabecera

    private JPanel crearPanelCabecera() {
        JPanel cab = new JPanel(new BorderLayout(10, 0));
        cab.setBackground(new Color(33, 47, 61));
        cab.setBorder(new EmptyBorder(6, 12, 6, 12));

        String numMesa = pedido != null && pedido.getNum_mesa() > 0 ? String.valueOf(pedido.getNum_mesa()) : "—";
        JLabel tit = etiqueta("PEDIDO #" + idPedido + "  ·  MESA: " + numMesa, 14, Font.BOLD, Color.WHITE);
        cab.add(tit, BorderLayout.WEST);

        String infoTasa = tasaValida
                ? "Tasa: Bs " + tasaCambio.toPlainString() + " / $"
                : "⚠ Sin tasa de cambio (solo Efectivo $)";
        JLabel lblTasa = etiqueta(infoTasa, 12, Font.PLAIN, tasaValida ? new Color(208, 236, 231) : new Color(245, 183, 177));
        cab.add(lblTasa, BorderLayout.EAST);

        return cab;
    }

    // ----------------------------------------------------------------- panel izquierdo: métodos y monto

    private JPanel crearPanelIzquierdo() {
        JPanel pnl = panelBlanco();

        JLabel titMetodos = etiqueta("MÉTODO DE PAGO  (clic = paga saldo completo)", 11, Font.BOLD, COLOR_TEXTO);
        alinear(titMetodos);
        pnl.add(titMetodos);
        pnl.add(Box.createVerticalStrut(4));

        JPanel gridMetodos = new JPanel(new GridLayout(3, 2, 6, 6));
        gridMetodos.setOpaque(false);
        gridMetodos.setMaximumSize(new Dimension(Short.MAX_VALUE, 120));
        alinear(gridMetodos);
        for (Metodo m : Metodo.values()) {
            JButton b = crearBotonMetodo(m);
            botonesMetodo.put(m, b);
            bordesOriginales.put(m, b.getBorder());
            bordesPintados.put(m, b.isBorderPainted());
            gridMetodos.add(b);
        }
        pnl.add(gridMetodos);
        pnl.add(Box.createVerticalStrut(6));

        JLabel titMonto = etiqueta("MONTO A PAGAR  (opcional, para pago parcial o vuelto)", 11, Font.BOLD, COLOR_TEXTO);
        alinear(titMonto);
        pnl.add(titMonto);
        pnl.add(Box.createVerticalStrut(3));

        txtMonto = new JTextField();
        txtMonto.setName("txtCobroMonto");
        txtMonto.setFont(new Font("Segoe UI", Font.BOLD, 18));
        txtMonto.setHorizontalAlignment(JTextField.RIGHT);
        txtMonto.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
        txtMonto.setPreferredSize(new Dimension(300, 36));
        txtMonto.setToolTipText("Escriba el monto y presione ENTER o pulse el método de pago. Vacío = paga el saldo completo.");
        txtMonto.addActionListener(e -> aplicarDesdeUi(metodoActivo));
        txtMonto.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                txtMonto.selectAll();
            }
        });
        alinear(txtMonto);
        pnl.add(txtMonto);

        JLabel hint = etiqueta("<html><span style='font-size:10px; color:#788490;'>Vacío = paga el saldo completo. "
                + "Se interpreta en <b>$</b> para Efectivo $ y en <b>Bs</b> para otros métodos.</span></html>", 10, Font.PLAIN, new Color(120, 130, 140));
        alinear(hint);
        pnl.add(Box.createVerticalStrut(2));
        pnl.add(hint);
        pnl.add(Box.createVerticalStrut(6));

        JLabel titChips = etiqueta("BILLETE ENTREGADO  (Efectivo $)", 11, Font.BOLD, COLOR_TEXTO);
        alinear(titChips);
        pnl.add(titChips);
        pnl.add(Box.createVerticalStrut(3));

        JPanel chips = new JPanel(new GridLayout(1, 5, 4, 0));
        chips.setOpaque(false);
        chips.setMaximumSize(new Dimension(Short.MAX_VALUE, 34));
        chips.setPreferredSize(new Dimension(400, 34));
        alinear(chips);
        for (int i = 0; i < chipsSugeridos.length; i++) {
            final int idx = i;
            JButton chip = new JButton("—");
            chip.setName("btnChipSugerido" + (i + 1));
            chip.setFont(new Font("Segoe UI", Font.BOLD, 13));
            chip.setBackground(new Color(240, 243, 244));
            chip.setForeground(COLOR_TEXTO);
            chip.setFocusable(false);
            chip.addActionListener(e -> {
                if (montosChip[idx] != null) {
                    aplicarPago(Metodo.EFECTIVO_USD, montosChip[idx]);
                }
            });
            chipsSugeridos[i] = chip;
            chips.add(chip);
        }
        btnChipExactoUsd = new JButton("Exacto $");
        btnChipExactoUsd.setName("btnChipExactoUsd");
        btnChipExactoUsd.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnChipExactoUsd.setBackground(new Color(236, 240, 241));
        btnChipExactoUsd.setToolTipText("Pagar exactamente el saldo restante en Efectivo USD ($)");
        btnChipExactoUsd.setFocusable(false);
        btnChipExactoUsd.addActionListener(e -> aplicarPago(Metodo.EFECTIVO_USD, null));
        chips.add(btnChipExactoUsd);
        pnl.add(chips);

        pnl.add(Box.createVerticalGlue());
        return pnl;
    }

    private JButton crearBotonMetodo(Metodo m) {
        EstiloMetodo e = ESTILOS.get(m);
        JButton b = new JButton(e.etiqueta());
        b.setName(e.nombreComponente());
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setToolTipText(m.nombreCorto() + " (" + KeyEvent.getKeyText(e.tecla()) + ")"
                + ((!tasaValida && m != Metodo.EFECTIVO_USD) ? " — requiere tasa de cambio" : ""));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        Sistema.botonSolido(b, e.color(), Color.WHITE);
        b.addActionListener(ev -> aplicarDesdeUi(m));
        return b;
    }

    // ----------------------------------------------------------------- panel derecho: totales y saldo

    private JPanel crearPanelDerecho() {
        JPanel pnl = panelBlanco();

        // 1. Cliente (compacto y plegable)
        JPanel pnlCliente = new JPanel(new BorderLayout(6, 0));
        pnlCliente.setOpaque(false);
        pnlCliente.setMaximumSize(new Dimension(Short.MAX_VALUE, 26));
        alinear(pnlCliente);

        String nomIni = pedido != null && pedido.getClienteNombre() != null && !pedido.getClienteNombre().isBlank()
                ? pedido.getClienteNombre() : CLIENTE_NOMBRE_DEFECTO;
        lblClienteResumen = etiqueta("Cliente: " + nomIni, 11, Font.PLAIN, COLOR_TEXTO);
        lblClienteResumen.setName("lblCobroClienteResumen");
        pnlCliente.add(lblClienteResumen, BorderLayout.CENTER);

        btnCambiarCliente = new JButton("Editar");
        btnCambiarCliente.setName("btnCobroCambiarCliente");
        btnCambiarCliente.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnCambiarCliente.setMargin(new Insets(1, 6, 1, 6));
        btnCambiarCliente.setFocusable(false);
        btnCambiarCliente.setToolTipText("Modificar el nombre y documento fiscal (C.I./RIF) del cliente");
        btnCambiarCliente.addActionListener(e -> alternarEdicionCliente());
        pnlCliente.add(btnCambiarCliente, BorderLayout.EAST);
        pnl.add(pnlCliente);

        pnlClienteEdicion = crearPanelClienteEdicion();
        pnlClienteEdicion.setVisible(false);
        alinear(pnlClienteEdicion);
        pnl.add(pnlClienteEdicion);
        pnl.add(Box.createVerticalStrut(4));

        // 2. Tarjeta Total
        JPanel cardTotal = new JPanel(new GridLayout(2, 1, 0, 1));
        cardTotal.setBackground(new Color(235, 245, 251));
        cardTotal.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(174, 214, 241), 1, true), new EmptyBorder(4, 10, 4, 10)));
        cardTotal.setMaximumSize(new Dimension(Short.MAX_VALUE, 52));
        alinear(cardTotal);

        lblTotalUsd = etiqueta("TOTAL:  $ " + totalUsd.toPlainString(), 18, Font.BOLD, new Color(21, 67, 96));
        lblTotalUsd.setName("lblCobroTotalUsd");
        lblTotalUsd.setToolTipText("Monto total del pedido en dólares estadounidenses (USD)");
        cardTotal.add(lblTotalUsd);

        lblTotalBs = etiqueta(tasaValida ? "Equivalente:  Bs " + totalBs.toPlainString() : "Sin equivalente en Bs", 11, Font.PLAIN, new Color(46, 64, 83));
        lblTotalBs.setName("lblCobroTotalBs");
        lblTotalBs.setToolTipText("Monto equivalente en bolívares según la tasa de cambio oficial");
        cardTotal.add(lblTotalBs);
        pnl.add(cardTotal);
        pnl.add(Box.createVerticalStrut(4));

        // 3. Tarjeta Saldo
        cardSaldo = new JPanel(new BorderLayout());
        cardSaldo.setMaximumSize(new Dimension(Short.MAX_VALUE, 38));
        alinear(cardSaldo);

        lblSaldo = etiqueta("SALDO: —", 14, Font.BOLD, COLOR_TEXTO);
        lblSaldo.setName("lblCobroSaldo");
        lblSaldo.setHorizontalAlignment(SwingConstants.CENTER);
        lblSaldo.setToolTipText("Saldo restante por pagar para completar la factura");
        cardSaldo.add(lblSaldo, BorderLayout.CENTER);
        pnl.add(cardSaldo);
        pnl.add(Box.createVerticalStrut(4));

        // 4. Desglose de pagos
        JLabel titLineas = etiqueta("DESGLOSE DE PAGOS", 11, Font.BOLD, COLOR_TEXTO);
        alinear(titLineas);
        pnl.add(titLineas);
        pnl.add(Box.createVerticalStrut(2));

        pnlLineas = new JPanel();
        pnlLineas.setLayout(new BoxLayout(pnlLineas, BoxLayout.Y_AXIS));
        pnlLineas.setOpaque(false);

        JScrollPane scrollLineas = new JScrollPane(pnlLineas);
        scrollLineas.setName("scrollCobroLineas");
        scrollLineas.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true), new EmptyBorder(2, 4, 2, 4)));
        scrollLineas.setMaximumSize(new Dimension(Short.MAX_VALUE, 75));
        scrollLineas.setPreferredSize(new Dimension(380, 75));
        scrollLineas.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        alinear(scrollLineas);
        pnl.add(scrollLineas);
        pnl.add(Box.createVerticalStrut(4));

        // 5. Vuelto
        JPanel cardVuelto = new JPanel(new BorderLayout());
        cardVuelto.setBackground(new Color(254, 249, 231));
        cardVuelto.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(249, 231, 159), 1, true), new EmptyBorder(4, 8, 4, 8)));
        cardVuelto.setMaximumSize(new Dimension(Short.MAX_VALUE, 32));
        alinear(cardVuelto);

        lblVueltoValor = etiqueta("VUELTO: —", 13, Font.BOLD, new Color(125, 102, 8));
        lblVueltoValor.setName("lblCobroVuelto");
        lblVueltoValor.setToolTipText("Monto a devolver al cliente como cambio en efectivo");
        cardVuelto.add(lblVueltoValor, BorderLayout.CENTER);
        pnl.add(cardVuelto);

        pnl.add(Box.createVerticalGlue());
        return pnl;
    }

    private JPanel crearPanelClienteEdicion() {
        JPanel p = new JPanel(new GridLayout(2, 2, 6, 2));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Short.MAX_VALUE, 44));
        p.setBorder(new EmptyBorder(2, 0, 2, 0));

        p.add(etiqueta("Nombre:", 10, Font.PLAIN, COLOR_TEXTO));
        txtClienteNombre = new JTextField(CLIENTE_NOMBRE_DEFECTO);
        txtClienteNombre.setName("txtCobroClienteNombre");
        txtClienteNombre.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        txtClienteNombre.setToolTipText("Nombre o razón social para la factura fiscal");
        p.add(txtClienteNombre);

        p.add(etiqueta("C.I. / RIF:", 10, Font.PLAIN, COLOR_TEXTO));
        txtClienteDoc = new JTextField(CLIENTE_DOC_DEFECTO);
        txtClienteDoc.setName("txtCobroClienteDoc");
        txtClienteDoc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        txtClienteDoc.setToolTipText("Cédula de Identidad o RIF del cliente");
        p.add(txtClienteDoc);

        return p;
    }

    private void alternarEdicionCliente() {
        boolean visible = !pnlClienteEdicion.isVisible();
        pnlClienteEdicion.setVisible(visible);
        btnCambiarCliente.setText(visible ? "Listo" : "Editar");
        if (!visible) {
            String nom = textoOpcional(txtClienteNombre.getText(), CLIENTE_NOMBRE_DEFECTO);
            String doc = textoOpcional(txtClienteDoc.getText(), CLIENTE_DOC_DEFECTO);
            lblClienteResumen.setText("Cliente: " + nom + " (" + doc + ")");
        }
        revalidate();
        repaint();
    }

    // ----------------------------------------------------------------- barra inferior

    private JPanel crearBarraInferior() {
        JPanel barra = new JPanel(new BorderLayout(8, 0));
        barra.setOpaque(false);

        // Izquierda: Pre-Factura, Menú Más y Ayuda (F1)
        btnPreFactura = new JButton("🧾 Pre-Factura (F8)");
        btnPreFactura.setName("btnCobroPreFactura");
        btnPreFactura.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnPreFactura.setPreferredSize(new Dimension(150, 40));
        btnPreFactura.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnPreFactura.setToolTipText("Imprimir comprobante preliminar / cuenta para el cliente sin cerrar la mesa (Atajo: F8)");
        btnPreFactura.addActionListener(e -> emitirPreFactura());

        btnMas = new JButton("⋯ Más");
        btnMas.setName("btnCobroMas");
        btnMas.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnMas.setPreferredSize(new Dimension(85, 40));
        btnMas.setToolTipText("Opciones avanzadas: Previsualizar ticket o registrar Fuga / No pagó");
        menuMas = crearMenuMas();
        btnMas.addActionListener(e -> {
            if (!enProcesamiento) {
                menuMas.show(btnMas, 0, -menuMas.getPreferredSize().height);
            }
        });

        btnAyuda = new JButton("❓ Ayuda (F1)");
        btnAyuda.setName("btnCobroAyuda");
        btnAyuda.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAyuda.setPreferredSize(new Dimension(125, 40));
        btnAyuda.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAyuda.setToolTipText("Guía interactiva con resumen de situaciones frecuentes de cobro (Atajo: F1)");
        btnAyuda.addActionListener(e -> mostrarGuiaAyudaCobro());

        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        izquierda.setOpaque(false);
        izquierda.add(btnPreFactura);
        izquierda.add(btnMas);
        izquierda.add(btnAyuda);
        barra.add(izquierda, BorderLayout.WEST);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(120, 20));
        JPanel centro = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 10));
        centro.setOpaque(false);
        centro.add(progressBar);
        barra.add(centro, BorderLayout.CENTER);

        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);

        btnCancelar = new JButton("✖ Cancelar (ESC)");
        btnCancelar.setName("btnCancelarCobro");
        btnCancelar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCancelar.setPreferredSize(new Dimension(145, 40));
        btnCancelar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelar.setToolTipText("Cancelar operación y regresar a la atención de mesas (Atajo: ESC)");
        btnCancelar.addActionListener(e -> cancelarYCerrar());
        derecha.add(btnCancelar);

        btnConfirmarCobro = new JButton("✔ COBRAR (ENTER)");
        btnConfirmarCobro.setName("btnConfirmarCobro");
        btnConfirmarCobro.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnConfirmarCobro.setPreferredSize(new Dimension(175, 40));
        btnConfirmarCobro.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnConfirmarCobro.setToolTipText("Confirmar pagos, liberar mesa y emitir factura fiscal (Atajo: ENTER)");
        Sistema.botonSolido(btnConfirmarCobro, new Color(39, 174, 96), Color.WHITE);
        btnConfirmarCobro.addActionListener(e -> ejecutarCobro());
        derecha.add(btnConfirmarCobro);

        barra.add(derecha, BorderLayout.EAST);
        return barra;
    }

    private JPopupMenu crearMenuMas() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem itVisor = new JMenuItem("👁 Previsualizar Ticket");
        itVisor.setName("itemCobroPrevisualizarTicket");
        itVisor.addActionListener(e -> previsualizarTicket());
        menu.add(itVisor);

        menu.addSeparator();

        JMenuItem itFuga = new JMenuItem("🚪 No pagó (Fuga / Anular)");
        itFuga.setName("itemCobroNoPago");
        itFuga.setForeground(new Color(192, 57, 43));
        itFuga.addActionListener(e -> anularPorFuga());
        menu.add(itFuga);

        return menu;
    }

    // ----------------------------------------------------------------- atajos de teclado

    private void configurarAtajosTeclado() {
        JRootPane root = getRootPane();

        mapearAccion(root, KeyEvent.VK_ENTER, "cobrarEnter", this::ejecutarCobro);
        mapearAccion(root, KeyEvent.VK_ESCAPE, "cancelarEsc", this::cancelarYCerrar);
        mapearAccion(root, KeyEvent.VK_F8, "prefacturaF8", this::emitirPreFactura);
        mapearAccion(root, KeyEvent.VK_F1, "ayudaCobroF1", this::mostrarGuiaAyudaCobro);

        for (Metodo m : Metodo.values()) {
            EstiloMetodo e = ESTILOS.get(m);
            mapearAccion(root, e.tecla(), "metodo" + m.name(), () -> aplicarDesdeUi(m));
        }

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            int tecla = KeyEvent.VK_1 + i;
            mapearAccion(root, tecla, "chip" + i, () -> {
                if (montosChip[idx] != null && !enProcesamiento) {
                    aplicarPago(Metodo.EFECTIVO_USD, montosChip[idx]);
                }
            });
        }
    }

    private void mapearAccion(JRootPane root, int keyCode, String nombre, Runnable accion) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyCode, 0);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, nombre);
        root.getActionMap().put(nombre, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (accion != null && !enProcesamiento) {
                    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (keyCode == KeyEvent.VK_ENTER && (focusOwner instanceof JButton)) {
                        ((JButton) focusOwner).doClick();
                        return;
                    }
                    if (focusOwner instanceof JTextComponent && keyCode != KeyEvent.VK_ENTER && keyCode != KeyEvent.VK_ESCAPE && keyCode != KeyEvent.VK_F8 && keyCode != KeyEvent.VK_F1) {
                        return;
                    }
                    accion.run();
                }
            }
        });
    }

    // ================================================================= lógica de interacción

    private void aplicarDesdeUi(Metodo m) {
        if (enProcesamiento) {
            return;
        }
        metodoActivo = m;
        String texto = txtMonto.getText();
        BigDecimal montoIngresado = null;
        try {
            montoIngresado = PlanPagosCobro.parsearMonto(texto);
        } catch (NumberFormatException ex) {
            estado("Monto inválido: '" + texto + "'. Escriba un número positivo o borre para pagar el saldo.", Nivel.ERROR);
            txtMonto.requestFocusInWindow();
            txtMonto.selectAll();
            return;
        }

        aplicarPago(m, montoIngresado);
        txtMonto.setText("");
    }

    private void aplicarPago(Metodo m, BigDecimal monto) {
        if (m == Metodo.CORTESIA) {
            aplicarCortesiaConPermiso();
            return;
        }
        Resultado res = plan.aplicar(m, monto);
        switch (res.estado()) {
            case SIN_TASA -> estado("No hay tasa de cambio válida. Solo se permite Efectivo $.", Nivel.ERROR);
            case SIN_TOTAL -> estado("El pedido no tiene un total positivo.", Nivel.ERROR);
            case YA_CUBIERTO -> estado("La cuenta ya está cubierta. Presione COBRAR o ENTER.", Nivel.AVISO);
            case AJUSTADO_AL_SALDO -> {
                estado(m.nombreCorto() + ": el monto ingresado superaba el saldo; se ajustó al saldo exacto.", Nivel.AVISO);
                refrescar();
            }
            case APLICADO -> {
                estado(m.nombreCorto() + " aplicado. " + (plan.cubierto() ? "Listo para cobrar (ENTER)." : "Saldo restante visible."), Nivel.OK);
                refrescar();
            }
        }
    }

    private void aplicarCortesiaConPermiso() {
        if (politicaAcceso != null && !politicaAcceso.permite(PoliticaAcceso.Accion.APLICAR_CORTESIA)) {
            JOptionPane.showMessageDialog(this,
                    "Su usuario no tiene permisos para otorgar cortesías.",
                    "Acceso Denegado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String motivo = JOptionPane.showInputDialog(this,
                "Indique el motivo / autorización de la cortesía:",
                "Cortesía de la Casa", JOptionPane.QUESTION_MESSAGE);
        if (motivo == null || motivo.trim().isEmpty()) {
            return;
        }
        try {
            plan.aplicarCortesia(motivo.trim());
            estado("Cortesía de la casa aplicada por el total de la cuenta.", Nivel.OK);
            refrescar();
        } catch (Exception ex) {
            estado(ex.getMessage(), Nivel.ERROR);
        }
    }

    private void quitarLinea(int indice) {
        if (enProcesamiento) {
            return;
        }
        Linea quitada = plan.quitar(indice);
        if (quitada != null) {
            estado("Se quitó " + quitada.metodo().nombreCorto() + ". Saldo actualizado.", Nivel.INFO);
            refrescar();
        }
    }

    // ================================================================= ejecución del cobro

    private void ejecutarCobro() {
        if (enProcesamiento) {
            return;
        }
        if (!plan.cubierto()) {
            estado("Aún queda saldo pendiente. Elija un método de pago.", Nivel.AVISO);
            return;
        }

        List<Linea> lineas = plan.lineas();
        List<PagoPedido> pagos = lineas.stream().map(this::aPagoPedido).toList();
        String metodoFinal = lineas.size() == 1 ? lineas.get(0).metodo().codigo() : "MIXTO";

        String cliNom = textoOpcional(txtClienteNombre.getText(), CLIENTE_NOMBRE_DEFECTO);
        String cliDoc = textoOpcional(txtClienteDoc.getText(), CLIENTE_DOC_DEFECTO);
        if (plan.motivoCortesia() != null) {
            cliNom = cliNom + " [Cortesía: " + plan.motivoCortesia() + "]";
        }

        setProcesando(true);
        new FinalizarPedidoSwingWorker(controlador, idPedido, cliNom, cliDoc, metodoFinal, pagos,
                resultado -> {
                    setProcesando(false);
                    if (resultado.finalizado()) {
                        liberarCentinela();
                        if (callbackFinalizado != null) {
                            callbackFinalizado.accept(true);
                        }
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "No se pudo finalizar el pedido en base de datos.",
                                "Error al Cobrar", JOptionPane.ERROR_MESSAGE);
                    }
                },
                (error, finalizado) -> {
                    setProcesando(false);
                    if (Boolean.TRUE.equals(finalizado)) {
                        // Transacción confirmada en BD exitosamente, fallo menor posterior (ej. ticket/impresora)
                        liberarCentinela();
                        if (callbackFinalizado != null) {
                            callbackFinalizado.accept(true);
                        }
                        dispose();
                        JOptionPane.showMessageDialog(null,
                                "Cobro guardado exitosamente en base de datos.\n"
                                + "Aviso secundario de ticket / visor: " + error.getMessage(),
                                "Cobro Registrado", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        String msg = error instanceof ErrorAplicacionException
                                ? error.getMessage() : "Error inesperado al cobrar: " + error.getMessage();
                        JOptionPane.showMessageDialog(this, msg, "Fallo de Cobro", JOptionPane.ERROR_MESSAGE);
                    }
                }).execute();
    }

    private PagoPedido aPagoPedido(Linea l) {
        Metodo m = l.metodo();
        if (m.esEfectivo()) {
            return new PagoPedido(m.codigo(), m.moneda(), l.aplicado(), l.recibido(), l.aplicado(), l.vuelto());
        }
        return new PagoPedido(m.codigo(), m.moneda(), l.aplicado());
    }

    private void anularPorFuga() {
        if (enProcesamiento) {
            return;
        }
        String motivo = JOptionPane.showInputDialog(this,
                "Indique el motivo / detalle del no-pago para el pedido #" + idPedido + ":\n"
                        + "La mesa se liberará inmediatamente sin generar faltantes falsos en caja.",
                "Anular por Fuga / No Pagó", JOptionPane.WARNING_MESSAGE);
        if (motivo == null || motivo.trim().isEmpty()) {
            return;
        }

        try {
            boolean anulado = controlador.anularPedido(idPedido, motivo.trim(), nombreUsuario);
            if (anulado) {
                liberarCentinela();
                if (callbackFinalizado != null) {
                    callbackFinalizado.accept(true);
                }
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo anular el pedido en base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al anular por fuga: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void emitirPreFactura() {
        if (enProcesamiento) {
            return;
        }
        try {
            boolean ok = controlador.emitirPreFactura(idPedido);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Pre-factura emitida exitosamente.", "Pre-Factura", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al emitir pre-factura: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void previsualizarTicket() {
        if (enProcesamiento) {
            return;
        }
        try {
            controlador.previsualizarPdfPedido(idPedido);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al previsualizar ticket: " + ex.getMessage(), "Visor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelarYCerrar() {
        liberarCentinela();
        if (callbackFinalizado != null) {
            callbackFinalizado.accept(false);
        }
        dispose();
    }

    private void liberarCentinela() {
        try {
            if (gestorBloqueo != null && idPedido > 0) {
                gestorBloqueo.liberar(idPedido, idUsuario);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error liberando centinela de pedido #" + idPedido + ": " + ex.getMessage());
        }
    }

    // ================================================================= refresco de la vista

    private void setProcesando(boolean valor) {
        enProcesamiento = valor;
        progressBar.setVisible(valor);
        txtMonto.setEnabled(!valor);
        btnCambiarCliente.setEnabled(!valor);
        txtClienteNombre.setEnabled(!valor);
        txtClienteDoc.setEnabled(!valor);
        refrescar();
    }

    private void refrescar() {
        Saldo saldo = plan.saldo();
        boolean cubierto = saldo.cubierto();
        boolean libre = !enProcesamiento;

        Color colorSaldo = cubierto ? Nivel.OK.color : Nivel.AVISO.color;
        cardSaldo.setBackground(cubierto ? new Color(234, 250, 234) : new Color(253, 243, 227));
        cardSaldo.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(colorSaldo, 2, true), new EmptyBorder(4, 10, 4, 10)));
        lblSaldo.setForeground(colorSaldo);
        lblSaldo.setText(cubierto ? "SALDO CUBIERTO  ✔" : "SALDO:  " + textoSaldo(saldo));

        reconstruirLineas();

        BigDecimal vUsd = plan.vueltoUsd();
        BigDecimal vBs = plan.vueltoBs();
        StringBuilder v = new StringBuilder("VUELTO: ");
        if (vUsd.signum() == 0 && vBs.signum() == 0) {
            v.append('—');
        } else {
            if (vUsd.signum() > 0) {
                v.append("$ ").append(vUsd.toPlainString());
                if (tasaValida) {
                    v.append("  (≈ Bs ").append(vUsd.multiply(tasaCambio).setScale(2, RoundingMode.HALF_UP).toPlainString()).append(')');
                }
            }
            if (vBs.signum() > 0) {
                v.append(vUsd.signum() > 0 ? "  +  " : "").append("Bs ").append(vBs.toPlainString());
            }
        }
        lblVueltoValor.setText(v.toString());

        for (Map.Entry<Metodo, JButton> en : botonesMetodo.entrySet()) {
            Metodo m = en.getKey();
            JButton b = en.getValue();
            boolean disponible = tasaValida || m == Metodo.EFECTIVO_USD;
            b.setEnabled(libre && disponible);
            boolean activo = m == metodoActivo;
            b.setBorder(activo ? BORDE_ACTIVO : bordesOriginales.get(m));
            b.setBorderPainted(activo || Boolean.TRUE.equals(bordesPintados.get(m)));
        }

        List<BigDecimal> sugeridos = libre ? plan.sugerirBilletesUsd() : List.of();
        for (int i = 0; i < chipsSugeridos.length; i++) {
            if (i < sugeridos.size()) {
                montosChip[i] = sugeridos.get(i);
                chipsSugeridos[i].setText("$ " + sugeridos.get(i).toBigInteger());
                chipsSugeridos[i].setToolTipText("Pagar con billete de $" + sugeridos.get(i).toBigInteger() + " (Atajo: tecla " + (i + 1) + ")");
                chipsSugeridos[i].setEnabled(true);
            } else {
                montosChip[i] = null;
                chipsSugeridos[i].setText("—");
                chipsSugeridos[i].setToolTipText(null);
                chipsSugeridos[i].setEnabled(false);
            }
        }
        btnChipExactoUsd.setEnabled(libre && !plan.saldoDisponible().cubierto());

        btnConfirmarCobro.setEnabled(libre && cubierto);
        btnCancelar.setEnabled(libre);
        btnPreFactura.setEnabled(libre);
        btnMas.setEnabled(libre);
        if (btnAyuda != null) {
            btnAyuda.setEnabled(libre);
        }
    }

    public void mostrarGuiaAyudaCobro() {
        if (enProcesamiento) {
            return;
        }
        DialogoGuiaCobro guia = new DialogoGuiaCobro(this);
        guia.setVisible(true);
    }

    private void reconstruirLineas() {
        pnlLineas.removeAll();
        List<Linea> lineas = plan.lineas();
        if (lineas.isEmpty()) {
            JLabel vacio = etiqueta("  Sin pagos aplicados. Elija un método.", 11, Font.ITALIC, new Color(120, 130, 140));
            vacio.setAlignmentX(Component.LEFT_ALIGNMENT);
            pnlLineas.add(vacio);
        }
        for (int i = 0; i < lineas.size(); i++) {
            pnlLineas.add(crearFilaLinea(i, lineas.get(i)));
        }
        pnlLineas.revalidate();
        pnlLineas.repaint();
    }

    private JComponent crearFilaLinea(int indice, Linea l) {
        JPanel fila = new JPanel(new BorderLayout(4, 0));
        fila.setOpaque(false);
        fila.setMaximumSize(new Dimension(Short.MAX_VALUE, 24));
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel texto = etiqueta(describirLinea(l), 11, Font.PLAIN, COLOR_TEXTO);
        texto.setToolTipText(describirLinea(l));

        JButton quitar = new JButton("✕");
        quitar.setName("btnCobroQuitarPago" + indice);
        quitar.setToolTipText("Quitar este pago");
        quitar.setFont(new Font("Segoe UI", Font.BOLD, 10));
        quitar.setMargin(new Insets(0, 6, 0, 6));
        quitar.setFocusable(false);
        quitar.setEnabled(!enProcesamiento);
        quitar.addActionListener(e -> quitarLinea(indice));

        fila.add(texto, BorderLayout.CENTER);
        fila.add(quitar, BorderLayout.EAST);
        return fila;
    }

    private String describirLinea(Linea l) {
        Metodo m = l.metodo();
        if (m == Metodo.CORTESIA) {
            return "Cortesía — " + plan.motivoCortesia();
        }
        String sim = m.enUsd() ? "$ " : "Bs ";
        StringBuilder sb = new StringBuilder(m.nombreCorto()).append("  —  ").append(sim).append(l.aplicado().toPlainString());
        if (l.vuelto().signum() > 0) {
            sb.append(" (rec. ").append(sim).append(l.recibido().toPlainString())
                    .append(" · vto. ").append(sim).append(l.vuelto().toPlainString()).append(')');
        }
        if (l.automatica()) {
            sb.append(" · por defecto");
        }
        return sb.toString();
    }

    private String textoSaldo(Saldo s) {
        if (!tasaValida) {
            return "$ " + s.usd().toPlainString();
        }
        return "$ " + s.usd().toPlainString() + "   /   Bs " + s.bs().toPlainString();
    }

    private void estado(String mensaje, Nivel nivel) {
        lblEstado.setText(mensaje);
        lblEstado.setForeground(nivel.color);
    }

    // ================================================================= utilidades de UI

    private static String textoOpcional(String texto, String defecto) {
        String t = texto == null ? "" : texto.trim();
        return t.isEmpty() ? defecto : t;
    }

    private static JPanel panelBlanco() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true), new EmptyBorder(8, 10, 8, 10)));
        return p;
    }

    private static JLabel etiqueta(String texto, int tamano, int estilo, Color color) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("Segoe UI", estilo, tamano));
        l.setForeground(color);
        return l;
    }

    private static <T extends JComponent> T alinear(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
}

package Vista;

import Controlador.PedidosControlador;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Servicio.PoliticaAcceso;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo unificado y asistido para la anulación ágil de pedidos, cuentas y facturas cobradas.
 * Permite buscar por número en vivo, visualizar los datos completos del comprobante (cliente,
 * mesa, montos en USD y Bs, estado) y ejecutar la anulación con motivo obligatorio en un solo paso.
 */
public class DialogoAnulacionAsistida extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(DialogoAnulacionAsistida.class.getName());

    private static final Color COLOR_FONDO = new Color(248, 250, 252);
    private static final Color COLOR_PELIGRO = new Color(192, 57, 43);
    private static final Color COLOR_PELIGRO_HOVER = new Color(169, 50, 38);
    private static final Color COLOR_EXITO = new Color(39, 174, 96);
    private static final Color COLOR_ADVERTENCIA = new Color(211, 84, 0);
    private static final Color COLOR_BORDE = new Color(226, 232, 240);

    private final PedidosControlador controlador;
    private final PoliticaAcceso politica;
    private final String usuarioActual;

    private JTextField txtIdDocumento;
    private JLabel lblEstadoBusqueda;
    private JPanel pnlTarjetaDetalle;

    private JLabel lblValorId;
    private JLabel lblValorFecha;
    private JLabel lblValorCliente;
    private JLabel lblValorUbicacion;
    private JLabel lblValorTotales;
    private JLabel lblValorEstado;

    private JTextField txtMotivo;
    private JButton btnConfirmarAnular;
    private JButton btnCancelar;

    private Pedidos pedidoCargado = null;
    private boolean anulacionExitosa = false;
    private final Timer timerBusquedaDebounce;

    public DialogoAnulacionAsistida(Frame owner, PedidosControlador controlador, PoliticaAcceso politica, String usuarioActual, Integer idInicial) {
        super(owner, "Anulación Asistida de Facturas y Comprobantes (F8)", true);
        this.controlador = controlador;
        this.politica = politica;
        this.usuarioActual = (usuarioActual != null && !usuarioActual.isBlank()) ? usuarioActual.trim() : "Sistema";

        this.timerBusquedaDebounce = new Timer(180, e -> ejecutarBusquedaDocumento());
        this.timerBusquedaDebounce.setRepeats(false);

        construirUi();

        if (idInicial != null && idInicial > 0) {
            txtIdDocumento.setText(String.valueOf(idInicial));
            ejecutarBusquedaDocumento();
        }
    }

    public boolean isAnulacionExitosa() {
        return anulacionExitosa;
    }

    public Pedidos getPedidoAfectado() {
        return pedidoCargado;
    }

    private void construirUi() {
        setSize(680, 500);
        setMinimumSize(new Dimension(620, 460));
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(COLOR_FONDO);
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Cabecera
        JPanel pnlCabecera = new JPanel(new BorderLayout());
        pnlCabecera.setOpaque(false);
        JLabel lblTitulo = new JLabel("🗑️ Anulación Rápida de Comprobantes y Facturas");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitulo.setForeground(new Color(30, 41, 59));

        JLabel lblSub = new JLabel("Ingrese el número de pedido o factura para consultar sus datos y revertir formalmente.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(100, 116, 139));

        pnlCabecera.add(lblTitulo, BorderLayout.NORTH);
        pnlCabecera.add(lblSub, BorderLayout.SOUTH);
        content.add(pnlCabecera, BorderLayout.NORTH);

        // 2. Cuerpo Central
        JPanel pnlCentro = new JPanel(new BorderLayout(0, 12));
        pnlCentro.setOpaque(false);

        // Barra de búsqueda por número
        JPanel pnlBusqueda = new JPanel(new BorderLayout(10, 0));
        pnlBusqueda.setOpaque(false);

        JLabel lblPrompt = new JLabel("N° de Factura / Ticket:");
        lblPrompt.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblPrompt.setForeground(new Color(51, 65, 85));

        txtIdDocumento = new JTextField();
        txtIdDocumento.setFont(new Font("Segoe UI", Font.BOLD, 15));
        txtIdDocumento.setForeground(new Color(15, 23, 42));
        txtIdDocumento.setPreferredSize(new Dimension(140, 36));
        txtIdDocumento.setToolTipText("Digite el número del pedido o factura (ej. 28 o #00000028)");

        lblEstadoBusqueda = new JLabel(" ");
        lblEstadoBusqueda.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JButton btnBuscar = new JButton("Buscar");
        btnBuscar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnBuscar.setBackground(new Color(241, 245, 249));
        btnBuscar.addActionListener(e -> ejecutarBusquedaDocumento());

        JPanel pnlInputWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pnlInputWrap.setOpaque(false);
        pnlInputWrap.add(lblPrompt);
        pnlInputWrap.add(txtIdDocumento);
        pnlInputWrap.add(btnBuscar);
        pnlInputWrap.add(lblEstadoBusqueda);

        pnlBusqueda.add(pnlInputWrap, BorderLayout.WEST);
        pnlCentro.add(pnlBusqueda, BorderLayout.NORTH);

        // Tarjeta con detalle en vivo del documento
        pnlTarjetaDetalle = new JPanel(new GridLayout(6, 2, 8, 8));
        pnlTarjetaDetalle.setBackground(Color.WHITE);
        pnlTarjetaDetalle.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));

        lblValorId = agregarFilaDetalle(pnlTarjetaDetalle, "Identificador Oficial:", "—");
        lblValorFecha = agregarFilaDetalle(pnlTarjetaDetalle, "Fecha y Hora:", "—");
        lblValorCliente = agregarFilaDetalle(pnlTarjetaDetalle, "Cliente / Documento:", "—");
        lblValorUbicacion = agregarFilaDetalle(pnlTarjetaDetalle, "Ubicación / Mesa:", "—");
        lblValorTotales = agregarFilaDetalle(pnlTarjetaDetalle, "Total Facturado:", "—");
        lblValorEstado = agregarFilaDetalle(pnlTarjetaDetalle, "Estado del Documento:", "—");

        pnlCentro.add(pnlTarjetaDetalle, BorderLayout.CENTER);

        // Panel inferior para motivo obligatorio
        JPanel pnlMotivo = new JPanel(new BorderLayout(0, 6));
        pnlMotivo.setOpaque(false);
        JLabel lblMotivoPrompt = new JLabel("Motivo de anulación (Obligatorio para auditoría fiscal y de caja):");
        lblMotivoPrompt.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblMotivoPrompt.setForeground(new Color(51, 65, 85));

        txtMotivo = new JTextField();
        txtMotivo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtMotivo.setPreferredSize(new Dimension(100, 34));
        txtMotivo.setToolTipText("Indique la justificación formal de la anulación");

        pnlMotivo.add(lblMotivoPrompt, BorderLayout.NORTH);
        pnlMotivo.add(txtMotivo, BorderLayout.CENTER);
        pnlCentro.add(pnlMotivo, BorderLayout.SOUTH);

        content.add(pnlCentro, BorderLayout.CENTER);

        // 3. Botonera Inferior
        JPanel pnlBotonera = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        pnlBotonera.setOpaque(false);

        btnCancelar = new JButton("Cancelar (ESC)");
        btnCancelar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancelar.addActionListener(e -> dispose());

        btnConfirmarAnular = new JButton("Confirmar Anulación");
        btnConfirmarAnular.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnConfirmarAnular.setBackground(COLOR_PELIGRO);
        btnConfirmarAnular.setForeground(Color.WHITE);
        btnConfirmarAnular.setOpaque(true);
        btnConfirmarAnular.setContentAreaFilled(true);
        btnConfirmarAnular.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        btnConfirmarAnular.setEnabled(false);
        btnConfirmarAnular.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnConfirmarAnular.addActionListener(e -> confirmarYEjecutarAnulacion());

        pnlBotonera.add(btnCancelar);
        pnlBotonera.add(btnConfirmarAnular);
        content.add(pnlBotonera, BorderLayout.SOUTH);

        setContentPane(content);

        // Eventos en vivo
        txtIdDocumento.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { dispararBusqueda(); }
            @Override public void removeUpdate(DocumentEvent e) { dispararBusqueda(); }
            @Override public void changedUpdate(DocumentEvent e) { dispararBusqueda(); }
            private void dispararBusqueda() {
                timerBusquedaDebounce.restart();
            }
        });

        txtIdDocumento.addActionListener(e -> {
            if (pedidoCargado != null) {
                txtMotivo.requestFocusInWindow();
            } else {
                ejecutarBusquedaDocumento();
            }
        });

        txtMotivo.addActionListener(e -> {
            if (btnConfirmarAnular.isEnabled()) {
                btnConfirmarAnular.doClick();
            }
        });

        // Configuración de atajos ESC y ENTER
        KeyStroke strokeEsc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().registerKeyboardAction(e -> dispose(), strokeEsc, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JLabel agregarFilaDetalle(JPanel contenedor, String etiqueta, String valorInicial) {
        JLabel lblEtiq = new JLabel(etiqueta);
        lblEtiq.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEtiq.setForeground(new Color(100, 116, 139));

        JLabel lblVal = new JLabel(valorInicial);
        lblVal.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblVal.setForeground(new Color(15, 23, 42));

        contenedor.add(lblEtiq);
        contenedor.add(lblVal);
        return lblVal;
    }

    private void ejecutarBusquedaDocumento() {
        String texto = txtIdDocumento.getText().trim().replace("#", "").trim();
        if (texto.isEmpty()) {
            limpiarDetalle("Ingrese un número");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(texto);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            limpiarDetalle("Número inválido");
            lblEstadoBusqueda.setText("❌ Número no válido");
            lblEstadoBusqueda.setForeground(COLOR_PELIGRO);
            return;
        }

        try {
            Pedidos p = controlador.verPedido(id);
            if (p == null) {
                limpiarDetalle("No encontrado");
                lblEstadoBusqueda.setText("❌ No existe documento #" + id);
                lblEstadoBusqueda.setForeground(COLOR_PELIGRO);
                return;
            }

            poblarDetalleConPedido(p);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al consultar pedido #" + id, ex);
            limpiarDetalle("Error de consulta");
            lblEstadoBusqueda.setText("❌ Error al consultar");
            lblEstadoBusqueda.setForeground(COLOR_PELIGRO);
        }
    }

    private void poblarDetalleConPedido(Pedidos p) {
        this.pedidoCargado = p;

        lblValorId.setText(String.format(java.util.Locale.US, "#%08d (ID %d)", p.getId(), p.getId()));
        lblValorFecha.setText(p.getFecha() != null ? p.getFecha() : "N/D");

        String doc = (p.getClienteDocumento() != null && !p.getClienteDocumento().isBlank()) ? p.getClienteDocumento() : "V-00000000";
        String nom = (p.getClienteNombre() != null && !p.getClienteNombre().isBlank()) ? p.getClienteNombre() : "Consumidor Final";
        lblValorCliente.setText(nom + " (" + doc + ")");

        String ubicacion = p.esPedidoRapido()
                ? "Pedido Rápido (Barra / Para llevar)"
                : (p.getSala() != null ? p.getSala() : "General") + " — Mesa " + p.getNum_mesa();
        lblValorUbicacion.setText(ubicacion);

        BigDecimal montoUsd = p.getTotalDecimal() != null ? p.getTotalDecimal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal montoBs = p.getTotalBs() != null ? p.getTotalBs().setScale(2, RoundingMode.HALF_UP) : null;
        String totalTexto = montoBs != null
                ? String.format(java.util.Locale.US, "$ %.2f (Bs. %.2f)", montoUsd, montoBs)
                : String.format(java.util.Locale.US, "$ %.2f", montoUsd);
        lblValorTotales.setText(totalTexto);

        String estado = p.getEstado() != null ? p.getEstado().toUpperCase() : "DESCONOCIDO";
        if ("FINALIZADO".equals(estado)) {
            lblValorEstado.setText("FINALIZADO (Venta cobrada — Generará contra-asiento en caja)");
            lblValorEstado.setForeground(COLOR_EXITO);
            lblValorEstado.setFont(new Font("Segoe UI", Font.BOLD, 12));

            boolean permite = politica != null && politica.permite(PoliticaAcceso.Accion.ANULAR_FACTURA_COBRADA);
            btnConfirmarAnular.setText("Confirmar Anulación y Revertir Caja");
            btnConfirmarAnular.setEnabled(permite);
            if (!permite) {
                lblEstadoBusqueda.setText("🔒 Requiere permiso de Administrador");
                lblEstadoBusqueda.setForeground(COLOR_PELIGRO);
            } else {
                lblEstadoBusqueda.setText("✓ Documento listo para anular");
                lblEstadoBusqueda.setForeground(COLOR_EXITO);
            }
        } else if ("PENDIENTE".equals(estado)) {
            lblValorEstado.setText("PENDIENTE (Cuenta activa en mesa — Liberará la mesa sin tocar caja)");
            lblValorEstado.setForeground(COLOR_ADVERTENCIA);
            lblValorEstado.setFont(new Font("Segoe UI", Font.BOLD, 12));

            boolean permite = politica != null && politica.permite(PoliticaAcceso.Accion.CANCELAR_CUENTA_PENDIENTE);
            btnConfirmarAnular.setText("Anular Cuenta Pendiente (Fuga / No pagó)");
            btnConfirmarAnular.setEnabled(permite);
            if (!permite) {
                lblEstadoBusqueda.setText("🔒 Requiere permiso para cancelar cuenta");
                lblEstadoBusqueda.setForeground(COLOR_PELIGRO);
            } else {
                lblEstadoBusqueda.setText("✓ Cuenta de mesa lista para anular");
                lblEstadoBusqueda.setForeground(COLOR_EXITO);
            }
        } else if ("ANULADO".equals(estado)) {
            lblValorEstado.setText("ANULADO (Este documento ya fue anulado previamente)");
            lblValorEstado.setForeground(COLOR_PELIGRO);
            lblValorEstado.setFont(new Font("Segoe UI", Font.BOLD, 12));

            btnConfirmarAnular.setText("Documento Ya Anulado");
            btnConfirmarAnular.setEnabled(false);
            lblEstadoBusqueda.setText("⚠️ Ya se encuentra anulado");
            lblEstadoBusqueda.setForeground(COLOR_ADVERTENCIA);
        } else {
            lblValorEstado.setText(estado);
            btnConfirmarAnular.setEnabled(false);
            lblEstadoBusqueda.setText(" ");
        }
    }

    private void limpiarDetalle(String estadoMsg) {
        this.pedidoCargado = null;
        lblValorId.setText("—");
        lblValorFecha.setText("—");
        lblValorCliente.setText("—");
        lblValorUbicacion.setText("—");
        lblValorTotales.setText("—");
        lblValorEstado.setText(estadoMsg);
        lblValorEstado.setForeground(new Color(100, 116, 139));
        lblValorEstado.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnConfirmarAnular.setEnabled(false);
        btnConfirmarAnular.setText("Confirmar Anulación");
        lblEstadoBusqueda.setText(" ");
    }

    private void confirmarYEjecutarAnulacion() {
        if (pedidoCargado == null) return;

        String motivo = txtMotivo.getText().trim();
        if (motivo.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Debe ingresar el motivo de la anulación para cumplir con la auditoría fiscal.",
                    "Motivo Requerido", JOptionPane.WARNING_MESSAGE);
            txtMotivo.requestFocusInWindow();
            return;
        }

        int id = pedidoCargado.getId();
        String estado = pedidoCargado.getEstado();

        String mensajeConfirm;
        if ("FINALIZADO".equalsIgnoreCase(estado)) {
            mensajeConfirm = String.format(java.util.Locale.US,
                    "¿Confirma la anulación definitiva de la FACTURA #%08d?\n\n" +
                    "• Cliente: %s\n" +
                    "• Total a revertir: $ %.2f\n" +
                    "• Motivo: %s\n\n" +
                    "Esta acción generará un contra-asiento formal en caja y sellará el evento en el Ledger Fiscal.",
                    id, pedidoCargado.getClienteNombre(), pedidoCargado.getTotalDecimal(), motivo);
        } else {
            mensajeConfirm = String.format(java.util.Locale.US,
                    "¿Confirma la anulación del PEDIDO PENDIENTE #%08d?\n\n" +
                    "• Ubicación: %s\n" +
                    "• Motivo: %s\n\n" +
                    "La mesa quedará liberada inmediatamente.",
                    id, pedidoCargado.esPedidoRapido() ? "Pedido Rápido" : "Mesa " + pedidoCargado.getNum_mesa(), motivo);
        }

        int opcion = JOptionPane.showConfirmDialog(this, mensajeConfirm,
                "Confirmar Anulación #" + id, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opcion != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            boolean exito;
            if ("FINALIZADO".equalsIgnoreCase(estado)) {
                exito = controlador.anularFacturaCobradaConDevolucion(id, motivo, usuarioActual);
            } else {
                exito = controlador.anularPedido(id, motivo, usuarioActual);
            }

            if (exito) {
                anulacionExitosa = true;
                JOptionPane.showMessageDialog(this,
                        "El documento #" + id + " ha sido anulado con éxito.",
                        "Anulación Exitosa", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "No se pudo completar la anulación del documento #" + id + ".",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al anular documento #" + id, ex);
            String msj = (ex instanceof ErrorAplicacionException) ? ex.getMessage() : ex.toString();
            JOptionPane.showMessageDialog(this,
                    "Error al anular documento: " + msj,
                    "Error en Transacción", JOptionPane.ERROR_MESSAGE);
        }
    }
}

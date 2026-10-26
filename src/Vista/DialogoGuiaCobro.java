package Vista;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Diálogo modal de ayuda interactiva (F1) para el módulo de Cobro y Facturación.
 * Explica de forma visual y estructurada las operaciones y situaciones de cobro más frecuentes.
 */
public class DialogoGuiaCobro extends JDialog {

    private static final Color COLOR_CABECERA = new Color(33, 47, 61);
    private static final Color COLOR_TEXTO = new Color(44, 62, 80);
    private static final Color COLOR_FONDO = new Color(248, 249, 250);

    public DialogoGuiaCobro(Dialog owner) {
        super(owner, "Guía Rápida de Cobro y Facturación · Atajo F1", true);
        inicializarUi();
    }

    public DialogoGuiaCobro(Frame owner) {
        super(owner, "Guía Rápida de Cobro y Facturación · Atajo F1", true);
        inicializarUi();
    }

    public DialogoGuiaCobro(Window owner) {
        super(owner, "Guía Rápida de Cobro y Facturación · Atajo F1", ModalityType.APPLICATION_MODAL);
        inicializarUi();
    }

    private void inicializarUi() {
        setSize(780, 520);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel panelRaiz = new JPanel(new BorderLayout(0, 0));
        panelRaiz.setBackground(COLOR_FONDO);

        panelRaiz.add(crearCabecera(), BorderLayout.NORTH);
        panelRaiz.add(crearContenidoTabs(), BorderLayout.CENTER);
        panelRaiz.add(crearBarraInferior(), BorderLayout.SOUTH);

        setContentPane(panelRaiz);
        configurarAtajosTeclado();
    }

    private JPanel crearCabecera() {
        JPanel cab = new JPanel(new BorderLayout(8, 4));
        cab.setBackground(COLOR_CABECERA);
        cab.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblTitulo = new JLabel("📖 GUÍA RÁPIDA DE COBRO Y SITUACIONES FRECUENTES");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitulo.setForeground(Color.WHITE);

        JLabel lblSubtitulo = new JLabel("Resumen operativo de cobros exactos, combinados, vueltos multimoneda y atajos de teclado");
        lblSubtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitulo.setForeground(new Color(208, 236, 231));

        cab.add(lblTitulo, BorderLayout.NORTH);
        cab.add(lblSubtitulo, BorderLayout.SOUTH);
        return cab;
    }

    private JTabbedPane crearContenidoTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        tabs.addTab("💳 Casos Frecuentes de Pago", crearTabHtml(obtenerHtmlCasosFrecuentes()));
        tabs.addTab("⚠ Situaciones Especiales & Fuga", crearTabHtml(obtenerHtmlSituacionesEspeciales()));
        tabs.addTab("⌨ Atajos de Teclado (F1..F8)", crearTabHtml(obtenerHtmlAtajosTeclado()));

        return tabs;
    }

    private JComponent crearTabHtml(String htmlContenido) {
        JEditorPane editor = new JEditorPane();
        editor.setContentType("text/html");
        editor.setEditable(false);
        editor.setText(htmlContenido);
        editor.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel crearBarraInferior() {
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        sur.setBackground(new Color(238, 242, 245));
        sur.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 225, 231)));

        JLabel lblNota = new JLabel("Presione ESC o F1 para cerrar esta ventana y regresar al cobro");
        lblNota.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblNota.setForeground(new Color(100, 110, 120));

        JButton btnCerrar = new JButton("Entendido / Cerrar (ESC)");
        btnCerrar.setName("btnCerrarGuiaCobro");
        btnCerrar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCerrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCerrar.addActionListener(e -> dispose());

        sur.add(lblNota);
        sur.add(Box.createHorizontalStrut(15));
        sur.add(btnCerrar);
        return sur;
    }

    private void configurarAtajosTeclado() {
        JRootPane root = getRootPane();
        AbstractAction cerrarAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        };

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cerrarGuia");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "cerrarGuia");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "cerrarGuia");
        root.getActionMap().put("cerrarGuia", cerrarAction);
    }

    private String obtenerHtmlEstiloBase() {
        return "<style>"
                + "body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 11px; color: #2c3e50; margin: 8px; }"
                + ".card { background-color: #ffffff; border: 1px solid #dce2e6; border-radius: 5px; padding: 10px 14px; margin-bottom: 10px; }"
                + ".titulo-card { font-size: 13px; font-weight: bold; color: #1a5276; margin-bottom: 4px; }"
                + ".paso { margin-left: 14px; margin-top: 3px; margin-bottom: 3px; }"
                + ".tecla { background-color: #ebedef; border: 1px solid #bdc3c7; border-radius: 3px; padding: 1px 5px; font-weight: bold; font-family: monospace; color: #212f3d; }"
                + ".badge-usd { background-color: #d4efdf; color: #196f3d; font-weight: bold; padding: 2px 6px; border-radius: 3px; }"
                + ".badge-bs { background-color: #d6eaf8; color: #1b4f72; font-weight: bold; padding: 2px 6px; border-radius: 3px; }"
                + ".badge-aviso { background-color: #fdebd0; color: #935116; font-weight: bold; padding: 2px 6px; border-radius: 3px; }"
                + "table { width: 100%; border-collapse: collapse; margin-top: 6px; }"
                + "th { background-color: #2c3e50; color: white; padding: 6px; text-align: left; font-size: 11px; }"
                + "td { border-bottom: 1px solid #e5e8e8; padding: 5px 6px; font-size: 11px; }"
                + "tr:nth-child(even) { background-color: #f9fbfd; }"
                + "</style>";
    }

    private String obtenerHtmlCasosFrecuentes() {
        return "<html><head>" + obtenerHtmlEstiloBase() + "</head><body>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>1. Pago Exacto Total (1 Solo Método)</div>"
                + "  <div class='paso'>• <b>Efectivo $:</b> Por defecto viene precargado por el monto exacto total. Solo presione <span class='tecla'>ENTER</span> para finalizar y facturar.</div>"
                + "  <div class='paso'>• <b>Tarjeta / Punto:</b> Presione <span class='tecla'>F4</span> (o clic en Punto/Tarjeta) y luego <span class='tecla'>ENTER</span>.</div>"
                + "  <div class='paso'>• <b>Efectivo Bs:</b> Presione <span class='tecla'>F3</span> (o clic en Efectivo Bs) y luego <span class='tecla'>ENTER</span>.</div>"
                + "  <div class='paso'>• <b>Pago Móvil / Transferencia:</b> Presione <span class='tecla'>F5</span> o <span class='tecla'>F6</span> y luego <span class='tecla'>ENTER</span>.</div>"
                + "</div>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>2. Pago con Billete Mayor y Cálculo de Vuelto (Cambio)</div>"
                + "  <div class='paso'>• <b>Ejemplo:</b> La cuenta es de $16.50 y el comensal entrega un billete de $20 o $50.</div>"
                + "  <div class='paso'>• Presione el chip de billete rápido <span class='tecla'>[ $20 ]</span> (o tecla <span class='tecla'>1</span>..<span class='tecla'>4</span>), o escriba <b>20</b> en el campo de monto y pulse <span class='tecla'>F2</span>.</div>"
                + "  <div class='paso'>• La tarjeta <b>VUELTO</b> mostrará de inmediato el cambio exacto en <span class='badge-usd'>$</span> y su conversión oficial en <span class='badge-bs'>Bs</span>.</div>"
                + "  <div class='paso'>• Presione <span class='tecla'>ENTER</span> para emitir la factura. El vuelto queda formalmente registrado.</div>"
                + "</div>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>3. Pagos Mixtos / Combinados (Multimoneda y Multimétodo)</div>"
                + "  <div class='paso'>• <b>Ejemplo:</b> Cuenta total de $50. El cliente paga $20 en Efectivo $ y los $30 restantes con Tarjeta de Débito.</div>"
                + "  <div class='paso'>1. Digite <b>20</b> en la casilla de monto y presione <span class='tecla'>F2</span> (Efectivo $). Se abonan los primeros $20.</div>"
                + "  <div class='paso'>2. La tarjeta de <b>SALDO PENDIENTE</b> se actualiza mostrando exactamente <b>$ 30.00</b> por pagar.</div>"
                + "  <div class='paso'>3. Presione <span class='tecla'>F4</span> (Punto/Tarjeta). Al dejar el monto vacío, el sistema asigna automáticamente el saldo restante.</div>"
                + "  <div class='paso'>4. El desglose confirma que el saldo llegó a $0.00. Presione <span class='tecla'>ENTER</span> para facturar.</div>"
                + "</div>"
                + "</body></html>";
    }

    private String obtenerHtmlSituacionesEspeciales() {
        return "<html><head>" + obtenerHtmlEstiloBase() + "</head><body>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>🚪 Mesa que se Retira sin Pagar (Fuga / Incobrable)</div>"
                + "  <div class='paso'>• Si un cliente abandona el establecimiento sin cancelar su consumo:</div>"
                + "  <div class='paso'>1. En la parte inferior izquierda, haga clic en el botón <span class='tecla'>⋯ Más</span>.</div>"
                + "  <div class='paso'>2. Seleccione la opción en rojo <span class='tecla'>🚪 No pagó (Fuga / Anular)</span>.</div>"
                + "  <div class='paso'>3. Confirme la anulación. La mesa queda disponible de inmediato sin emitir factura ni generar faltantes ficticios en caja.</div>"
                + "</div>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>🧾 Pre-Factura / Estado de Cuenta para la Mesa</div>"
                + "  <div class='paso'>• Para entregar el ticket informativo al cliente para su revisión previa:</div>"
                + "  <div class='paso'>• Presione <span class='tecla'>F8</span> o haga clic en <span class='tecla'>🧾 Pre-Factura</span>.</div>"
                + "  <div class='paso'>• Se imprime la pre-cuenta desglosada. <b>Importante:</b> La pre-factura no cierra la mesa ni tiene validez fiscal definitiva.</div>"
                + "</div>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>👤 Facturación con Datos del Cliente (C.I. / RIF / Razón Social)</div>"
                + "  <div class='paso'>• Por defecto, la factura se emite a 'Consumidor Final (V-00000000)'.</div>"
                + "  <div class='paso'>• Si el cliente requiere factura formal con sus datos:</div>"
                + "  <div class='paso'>• Haga clic en <span class='tecla'>Editar</span> junto al nombre del cliente en la sección derecha, ingrese el Nombre/Razón Social y C.I./RIF, y pulse <span class='tecla'>Listo</span>.</div>"
                + "</div>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>🎁 Cortesías y Consumos de la Casa</div>"
                + "  <div class='paso'>• Presione <span class='tecla'>F7</span> para aplicar consumo de cortesía institucional. Requiere autorización según permisos del operador.</div>"
                + "</div>"
                + "</body></html>";
    }

    private String obtenerHtmlAtajosTeclado() {
        return "<html><head>" + obtenerHtmlEstiloBase() + "</head><body>"
                + "<div class='card'>"
                + "  <div class='titulo-card'>Resumen de Atajos de Teclado del Módulo de Cobro</div>"
                + "  <table>"
                + "    <tr><th>Tecla</th><th>Acción</th><th>Descripción</th></tr>"
                + "    <tr><td><span class='tecla'>F1</span></td><td><b>Ayuda</b></td><td>Abre esta guía interactiva con procedimientos y ejemplos.</td></tr>"
                + "    <tr><td><span class='tecla'>F2</span></td><td><b>Efectivo $</b></td><td>Pagar con Dólares en efectivo (calcula vuelto si excede el total).</td></tr>"
                + "    <tr><td><span class='tecla'>F3</span></td><td><b>Efectivo Bs</b></td><td>Pagar con Bolívares en efectivo según la tasa de cambio.</td></tr>"
                + "    <tr><td><span class='tecla'>F4</span></td><td><b>Punto / Tarjeta</b></td><td>Registrar cobro con tarjeta de débito o crédito nacional/internacional.</td></tr>"
                + "    <tr><td><span class='tecla'>F5</span></td><td><b>Pago Móvil</b></td><td>Pago Móvil interbancario con referencia.</td></tr>"
                + "    <tr><td><span class='tecla'>F6</span></td><td><b>Transferencia</b></td><td>Transferencia bancaria directa.</td></tr>"
                + "    <tr><td><span class='tecla'>F7</span></td><td><b>Cortesía</b></td><td>Cortesía institucional autorizada a costo cero.</td></tr>"
                + "    <tr><td><span class='tecla'>F8</span></td><td><b>Pre-Factura</b></td><td>Imprime el comprobante preliminar para la mesa.</td></tr>"
                + "    <tr><td><span class='tecla'>1</span> .. <span class='tecla'>4</span></td><td><b>Billetes $</b></td><td>Pagar con billete sugerido de Efectivo $ (Chip 1 al 4).</td></tr>"
                + "    <tr><td><span class='tecla'>ENTER</span></td><td><b>COBRAR</b></td><td>Confirma los pagos, cierra el pedido y emite la Factura fiscal.</td></tr>"
                + "    <tr><td><span class='tecla'>ESC</span></td><td><b>Cancelar</b></td><td>Cierra la ventana de cobro y regresa a la atención de la mesa.</td></tr>"
                + "  </table>"
                + "</div>"
                + "</body></html>";
    }
}

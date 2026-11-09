package Vista;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Diálogo interactivo tipo "Cheat Sheet" con el mapa completo de atajos de teclado del sistema.
 * Accesible mediante Shift+F1, F11 o el botón de ayuda en la barra de estado.
 */
public class DialogoAtajosTeclado extends JDialog {

    public DialogoAtajosTeclado(Frame owner) {
        super(owner, "Atajos de Teclado del Sistema (Cheat Sheet)", true);
        construirUi();
    }

    private void construirUi() {
        setSize(780, 520);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(new Color(248, 250, 252));
        content.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Cabecera informativa
        JPanel pnlCabecera = new JPanel(new BorderLayout());
        pnlCabecera.setOpaque(false);
        JLabel lblTitulo = new JLabel("⚡ Mapa de Atajos y Operación Rápida por Teclado");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitulo.setForeground(new Color(0, 102, 102));
        JLabel lblSub = new JLabel("Diseñado para agilizar la toma de pedidos, despacho a cocina y cobro en barra o caja.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(100, 105, 115));
        pnlCabecera.add(lblTitulo, BorderLayout.NORTH);
        pnlCabecera.add(lblSub, BorderLayout.SOUTH);
        content.add(pnlCabecera, BorderLayout.NORTH);

        // Cuadrícula central de secciones
        JPanel pnlSecciones = new JPanel(new GridLayout(1, 2, 12, 0));
        pnlSecciones.setOpaque(false);

        // Columna Izquierda: POS Táctil / Atención Directa
        JPanel colPOS = crearTarjetaSeccion("Punto de Venta / Atención Directa (Split)", new String[][]{
                {"F1", "Enfocar y seleccionar el buscador de platos"},
                {"F2", "Enviar comanda a cocina / Despachar pedido"},
                {"F3", "Emitir Pre-Factura informativa de la mesa activa"},
                {"F4", "Filtrar rápidamente platos favoritos"},
                {"F5", "Actualizar estado en vivo de las mesas de la sala"},
                {"F12", "Abrir ventana de Cobro y Facturación"},
                {"Enter", "Agregar plato seleccionado o avanzar campo"},
                {"ESC", "Limpiar filtro de búsqueda (sin borrar comanda)"},
                {"Flechas ↑/↓", "Navegar catálogo de platos"},
                {"Flecha →", "Pasar foco del catálogo a la tabla comanda"}
        });

        // Columna Derecha: Ventana de Cobro y Globales
        JPanel colCobro = new JPanel(new GridLayout(2, 1, 0, 10));
        colCobro.setOpaque(false);

        JPanel secCobro = crearTarjetaSeccion("Ventana de Cobro y Facturación", new String[][]{
                {"Enter", "Confirmar cobro, emitir factura y liberar mesa"},
                {"ESC", "Cancelar cobro y volver a atención de mesas"},
                {"F8", "Emitir pre-factura preliminar del pedido"},
                {"F1", "Guía interactiva de casos frecuentes de cobro"},
                {"Click Exacto $", "Aplica el saldo total restante en Efectivo USD"}
        });

        JPanel secGlobal = crearTarjetaSeccion("Atajos Globales de Navegación", new String[][]{
                {"F8", "Abrir anulación asistida de facturas y tickets"},
                {"F11 / Shift+F1", "Abrir esta hoja de atajos (Cheat Sheet)"},
                {"Tab", "Saltar ordenadamente entre campos de texto y botones"},
                {"Barra Espacio", "Accionar botón o casilla de verificación enfocada"}
        });

        colCobro.add(secCobro);
        colCobro.add(secGlobal);

        pnlSecciones.add(colPOS);
        pnlSecciones.add(colCobro);

        JScrollPane scroll = new JScrollPane(pnlSecciones);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);

        // Barra inferior: Cerrar
        JPanel pnlSur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlSur.setOpaque(false);
        JButton btnCerrar = new JButton("Cerrar (ESC)");
        btnCerrar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCerrar.setPreferredSize(new Dimension(120, 32));
        btnCerrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        Sistema.botonSolido(btnCerrar, new Color(44, 62, 80), Color.WHITE);
        btnCerrar.addActionListener(e -> dispose());
        pnlSur.add(btnCerrar);
        content.add(pnlSur, BorderLayout.SOUTH);

        // Mapeo tecla ESC para cerrar
        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cerrar");
        content.getActionMap().put("cerrar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        setContentPane(content);
    }

    private JPanel crearTarjetaSeccion(String titulo, String[][] atajos) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(220, 224, 230), 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel lbl = new JLabel(titulo);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(new Color(0, 102, 102));
        card.add(lbl, BorderLayout.NORTH);

        JPanel pnlLista = new JPanel(new GridLayout(atajos.length, 1, 0, 4));
        pnlLista.setOpaque(false);

        for (String[] par : atajos) {
            JPanel fila = new JPanel(new BorderLayout(6, 0));
            fila.setOpaque(false);

            JLabel kbd = new JLabel(par[0], SwingConstants.CENTER);
            kbd.setFont(new Font("Segoe UI", Font.BOLD, 11));
            kbd.setForeground(new Color(33, 33, 33));
            kbd.setBackground(new Color(236, 240, 243));
            kbd.setOpaque(true);
            kbd.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(189, 195, 199), 1, true),
                    new EmptyBorder(1, 6, 1, 6)
            ));
            kbd.setPreferredSize(new Dimension(105, 22));

            JLabel desc = new JLabel(par[1]);
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            desc.setForeground(new Color(60, 64, 70));

            fila.add(kbd, BorderLayout.WEST);
            fila.add(desc, BorderLayout.CENTER);
            pnlLista.add(fila);
        }

        card.add(pnlLista, BorderLayout.CENTER);
        return card;
    }
}

package aceptacion;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import Vista.Sistema;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;

/**
 * Prueba E2E Virtual en Xvfb :99 para el flujo de guardado inteligente de platos (Opción 2),
 * atajo Enter exclusivo en actualización de precios de platos existentes (Opción 3) y botón Limpiar Campos por grupo de inputs.
 *
 * Ejecutable vía:
 *   ./e2e_virtual.sh aceptacion.E2EGuardadoPlatosVirtual
 */
public class E2EGuardadoPlatosVirtual {

    private static int fallos = 0;

    private static void check(boolean condicion, String mensaje) {
        if (condicion) {
            System.out.println("  [OK] " + mensaje);
        } else {
            System.err.println("  [FALLO] " + mensaje);
            fallos++;
        }
    }

    private static <T> T enEdt(Callable<T> callable) throws Exception {
        final Object[] res = new Object[1];
        final Throwable[] err = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                res[0] = callable.call();
            } catch (Throwable t) {
                err[0] = t;
            }
        });
        if (err[0] != null) {
            throw new RuntimeException(err[0]);
        }
        @SuppressWarnings("unchecked")
        T t = (T) res[0];
        return t;
    }

    private static void enEdtV(Runnable runnable) throws Exception {
        enEdt(() -> {
            runnable.run();
            return null;
        });
    }

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================================");
        System.out.println("INICIANDO PRUEBA E2E VIRTUAL: GUARDADO INTELIGENTE, ENTER Y LIMPIAR CAMPOS");
        System.out.println("================================================================================");

        Usuario admin = new Usuario(1, "xavier", "admin", "admin", "Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(admin);
        PedidosDao pedidosDao = new PedidosDao();
        LoginDao empresaDao = new LoginDao();
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

        PlatosDao platosDao = new PlatosDao();
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosDao, politica));
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(new SalasDao(), politica));
        CategoriaControlador catCtrl = new CategoriaControlador(new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politica));
        FavoritoControlador favCtrl = new FavoritoControlador(new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politica));

        Sistema sistema = enEdt(() -> {
            Sistema s = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, catCtrl, favCtrl, empresaDao);
            s.setVisible(true);
            return s;
        });

        int idPlatoCreado = -1;
        String platoNombre = "E2E Plato Especial " + System.currentTimeMillis();

        try {
            // 1. Navegar a pestaña Platos
            System.out.println("\n[Paso 1] Navegar a la pestaña de Platos y verificar estado limpio");
            enEdtV(() -> {
                sistema.getBtnNuevoPlato().doClick();
            });
            Thread.sleep(300);

            enEdtV(() -> {
                check("".equals(sistema.getTxtIdPlato().getText().trim()), "Formulario inicia con txtIdPlato vacío");
                check("Guardar".equals(sistema.getBtnGuardarPlato().getText()), "Botón de la izquierda inicia como 'Guardar'");
                check("Editar".equals(sistema.getBtnEditarPlato().getText()), "Botón de la derecha inicia como 'Editar'");
                check("Limpiar Campos".equals(sistema.getBtnNuevoPlato().getText()), "Botón de limpieza dice 'Limpiar Campos'");
            });

            // 2. Verificar que en creación el atajo Enter NO guarda (evita registros accidentales)
            System.out.println("\n[Paso 2] Verificar que Enter en creación NO registra (evita accidentes)");
            enEdtV(() -> {
                sistema.getTxtNombrePlato().setText(platoNombre);
                sistema.getTxtPrecioPlato().setText("25.00");
                for (var l : sistema.getTxtPrecioPlato().getActionListeners()) {
                    l.actionPerformed(new ActionEvent(sistema.getTxtPrecioPlato(), ActionEvent.ACTION_PERFORMED, null));
                }
            });
            Thread.sleep(300);

            Optional<Platos> noExisteAun = platosDao.buscarActivoPorNombre(platoNombre);
            check(!noExisteAun.isPresent(), "El plato NO se guardó con Enter en modo creación (comportamiento esperado)");

            // Guardar con clic en botón Guardar
            enEdtV(() -> {
                sistema.getBtnGuardarPlato().doClick();
            });
            Thread.sleep(500);

            Optional<Platos> platoDbOpt = platosDao.buscarActivoPorNombre(platoNombre);
            check(platoDbOpt.isPresent(), "El plato fue registrado tras hacer clic deliberado en 'Guardar'");
            if (platoDbOpt.isPresent()) {
                Platos platoDb = platoDbOpt.get();
                idPlatoCreado = platoDb.getId();
                check(new BigDecimal("25.00").compareTo(platoDb.getPrecioDecimal()) == 0, "El precio registrado es 25.00");
            }

            enEdtV(() -> {
                check("".equals(sistema.getTxtIdPlato().getText().trim()), "Tras registrar, el formulario se autolimpia");
                check("Guardar".equals(sistema.getBtnGuardarPlato().getText()), "Botón regresa al modo 'Guardar'");
            });

            // 3. Cargar el plato y verificar cambio reactivo de los botones a 'Guardar Cambios'
            System.out.println("\n[Paso 3] Cargar el plato y verificar reactividad a 'Guardar Cambios'");
            final int idPlato = idPlatoCreado;
            enEdtV(() -> {
                sistema.getTxtIdPlato().setText(String.valueOf(idPlato));
                sistema.getTxtNombrePlato().setText(platoNombre);
                sistema.getTxtPrecioPlato().setText("25.00");
                sistema.actualizarEstadoBotonesPlatos();

                check("Guardar Cambios".equals(sistema.getBtnGuardarPlato().getText()),
                        "Botón de la izquierda cambia dinámicamente a 'Guardar Cambios'");
                check("Guardar Cambios".equals(sistema.getBtnEditarPlato().getText()),
                        "Botón de la derecha cambia dinámicamente a 'Guardar Cambios'");
            });

            // 4. Modificar precio y presionar botón de la IZQUIERDA (Opción 2)
            System.out.println("\n[Paso 4] Modificar precio a 29.50 y pulsar botón de la IZQUIERDA");
            enEdtV(() -> {
                sistema.getTxtPrecioPlato().setText("29.50");
                sistema.getBtnGuardarPlato().doClick();
            });
            Thread.sleep(500);

            Optional<Platos> platoModificadoOpt = platosDao.buscarActivoPorNombre(platoNombre);
            check(platoModificadoOpt.isPresent() && new BigDecimal("29.50").compareTo(platoModificadoOpt.get().getPrecioDecimal()) == 0,
                    "El precio fue actualizado exitosamente a 29.50 pulsando el botón de la izquierda");

            // 5. Modificar precio y presionar Enter en el precio (Opción 3: SOLO en plato existente)
            System.out.println("\n[Paso 5] Modificar precio a 35.00 y presionar Enter en el precio (Plato existente)");
            enEdtV(() -> {
                sistema.getTxtIdPlato().setText(String.valueOf(idPlato));
                sistema.getTxtNombrePlato().setText(platoNombre);
                sistema.getTxtPrecioPlato().setText("35.00");
                sistema.actualizarEstadoBotonesPlatos();

                // Aquí SÍ se dispara el guardado porque el plato ya existe
                for (var l : sistema.getTxtPrecioPlato().getActionListeners()) {
                    l.actionPerformed(new ActionEvent(sistema.getTxtPrecioPlato(), ActionEvent.ACTION_PERFORMED, null));
                }
            });
            Thread.sleep(500);

            platoModificadoOpt = platosDao.buscarActivoPorNombre(platoNombre);
            check(platoModificadoOpt.isPresent() && new BigDecimal("35.00").compareTo(platoModificadoOpt.get().getPrecioDecimal()) == 0,
                    "El precio fue actualizado exitosamente a 35.00 con la tecla Enter en plato existente");

            // 6. Decisión del operador: Cancelar edición y limpiar campos con botón 'Limpiar Campos'
            System.out.println("\n[Paso 6] Operador pulsa botón 'Limpiar Campos' para resetear el formulario");
            enEdtV(() -> {
                sistema.getTxtIdPlato().setText(String.valueOf(idPlato));
                sistema.getTxtNombrePlato().setText(platoNombre);
                sistema.getTxtPrecioPlato().setText("99.99");
                sistema.actualizarEstadoBotonesPlatos();

                // Clic en Limpiar Campos
                sistema.getBtnNuevoPlato().doClick();

                check("".equals(sistema.getTxtIdPlato().getText().trim()), "txtIdPlato queda vacío tras limpiar");
                check("".equals(sistema.getTxtNombrePlato().getText().trim()), "txtNombrePlato queda vacío tras limpiar");
                check("".equals(sistema.getTxtPrecioPlato().getText().trim()), "txtPrecioPlato queda vacío tras limpiar");
                check("Guardar".equals(sistema.getBtnGuardarPlato().getText()), "Botón regresa a 'Guardar'");
                check("Editar".equals(sistema.getBtnEditarPlato().getText()), "Botón derecho regresa a 'Editar'");
            });

            // 7. Grupo de inputs de Salas: Verificar botón 'Limpiar Campos'
            System.out.println("\n[Paso 7] Grupo de inputs de Salas: Verificar botón 'Limpiar Campos'");
            enEdtV(() -> {
                check("Limpiar Campos".equals(sistema.getBtnNuevoSala().getText()),
                        "Botón de salas dice 'Limpiar Campos'");
                sistema.getTxtIdSala().setText("99");
                sistema.getTxtNombreSala().setText("Sala Prueba");
                sistema.getTxtMesas().setText("5");
                sistema.actualizarEstadoBotonesSalas();

                sistema.getBtnNuevoSala().doClick();
                check("".equals(sistema.getTxtIdSala().getText().trim()), "ID de sala limpio");
                check("".equals(sistema.getTxtNombreSala().getText().trim()), "Nombre de sala limpio");
                check("".equals(sistema.getTxtMesas().getText().trim()), "Mesas de sala limpio");
            });

            // 8. Grupo de inputs de Usuarios: Verificar botón 'Limpiar Campos'
            System.out.println("\n[Paso 8] Grupo de inputs de Usuarios: Verificar botón 'Limpiar Campos'");
            enEdtV(() -> {
                check(sistema.getBtnLimpiarUsuario() != null, "Botón limpiar usuario existe");
                check("Limpiar Campos".equals(sistema.getBtnLimpiarUsuario().getText()),
                        "Botón de usuarios dice 'Limpiar Campos'");
                sistema.getTxtNombre().setText("Admin Temp");
                sistema.getTxtCorreo().setText("temp@correo.com");
                sistema.getTxtPass().setText("secreto123");

                sistema.getBtnLimpiarUsuario().doClick();
                check("".equals(sistema.getTxtNombre().getText().trim()), "Nombre de usuario limpio");
                check("".equals(sistema.getTxtCorreo().getText().trim()), "Correo de usuario limpio");
                check(sistema.getTxtPass().getPassword().length == 0, "Password de usuario limpio");
            });

        } finally {
            if (idPlatoCreado > 0) {
                try {
                    platosDao.desactivar(idPlatoCreado);
                } catch (Exception ignored) {}
            }
            enEdtV(sistema::dispose);
        }

        System.out.println("\n================================================================================");
        if (fallos == 0) {
            System.out.println("RESULTADO E2E: TODAS LAS PRUEBAS PASARON EXITOSAMENTE (0 fallos)");
            System.out.println("================================================================================");
            System.exit(0);
        } else {
            System.err.println("RESULTADO E2E: HUBIERON " + fallos + " FALLOS");
            System.out.println("================================================================================");
            System.exit(1);
        }
    }
}

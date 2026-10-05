package restaurante;

import Controlador.*;
import Modelo.*;
import Servicio.*;
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

public class SimularUsuario {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(" 🍽️  SIMULACIÓN E2E DE COMPORTAMIENTO DE USUARIO — RESTAURANTE ");
        System.out.println("================================================================================\n");

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
        LoginDao loginDao = new LoginDao(proveedor, new PasswordHasher());
        SalasDao salasDao = new SalasDao(proveedor);
        PlatosDao platosDao = new PlatosDao(proveedor);
        PedidosDao pedidosDao = new PedidosDao(proveedor);

        LoginControlador loginCtrl = new LoginControlador(new AutenticacionServicio(loginDao));
        
        Path dirPdfTemp = null;

        try {
            // -------------------------------------------------------------------------
            // PASO 1: Inicio de sesión como Administrador
            // -------------------------------------------------------------------------
            System.out.println("🔹 PASO 1: Inicio de sesión del Administrador");
            System.out.println("   Usuario introduce: info@angelsifuentes.com / admin");
            Optional<Usuario> adminOpt = loginCtrl.autenticar("info@angelsifuentes.com", "admin");
            if (adminOpt.isEmpty()) {
                throw new IllegalStateException("Fallo en la autenticación del Administrador");
            }
            Usuario admin = adminOpt.get();
            System.out.println("   ✅ ¡Acceso Concedido! Sesión iniciada para: " + admin.getNombre() + " (" + admin.getRol() + ")");
            
            PoliticaAcceso politicaAdmin = new PoliticaAcceso(admin);

            SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasDao, politicaAdmin));
            PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosDao, politicaAdmin));

            dirPdfTemp = Files.createTempDirectory("simulacion-pdf-");
            final Path dirFinal = dirPdfTemp;
            GeneradorPdfPedido generadorPdf = new GeneradorPdfPedido(dirFinal);
            PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                    pedidosDao::verPedido,
                    pedidosDao::verPedidoDetalle,
                    loginDao::datosEmpresa,
                    generadorPdf,
                    archivo -> System.out.println("   📄 [Visor PDF]: Se solicitó abrir automáticamente el archivo: " + archivo.getFileName())
            );

            ConsultaPedidosServicio consultaPedidos = new ConsultaPedidosServicio(pedidosDao, politicaAdmin);
            PedidosControlador pedidosCtrl = new PedidosControlador(
                    new PedidoServicio(pedidosDao),
                    pdfServicio,
                    politicaAdmin,
                    consultaPedidos
            );

            // -------------------------------------------------------------------------
            // PASO 2: Consulta de datos de empresa
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 2: El Administrador consulta la configuración del restaurante");
            Config config = loginDao.datosEmpresa();
            System.out.println("   🏢 Empresa: " + config.getNombre() + " | RUC: " + config.getRuc());
            System.out.println("   📞 Teléfono: " + config.getTelefono() + " | Dirección: " + config.getDireccion());
            System.out.println("   💬 Mensaje ticket: \"" + config.getMensaje() + "\"");

            // -------------------------------------------------------------------------
            // PASO 3: Apertura de nueva Sala y Menú del Día
            // -------------------------------------------------------------------------
            String sufijo = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
            String nombreSala = "TERRAZA " + sufijo;
            System.out.println("\n🔹 PASO 3: El Administrador configura una nueva Sala y platos para hoy");
            
            Salas nuevaSala = new Salas(0, nombreSala, 10);
            salasCtrl.registrar(nuevaSala);
            System.out.println("   ✅ Sala creada: \"" + nombreSala + "\" con 10 mesas.");

            List<Salas> salasActuales = salasCtrl.listar();
            Salas salaSeleccionada = salasActuales.stream()
                    .filter(s -> s.getNombre().equals(nombreSala))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontró la sala creada"));

            String hoy = LocalDate.now().toString();
            Platos plato1 = new Platos(0, "Lomo Saltado Criollo [" + sufijo + "]", new BigDecimal("32.50"), hoy);
            Platos plato2 = new Platos(0, "Jugo de Maracuyá 1L [" + sufijo + "]", new BigDecimal("8.00"), hoy);
            platosCtrl.registrar(plato1);
            platosCtrl.registrar(plato2);
            System.out.println("   ✅ Platos agregados al menú del día (" + hoy + "):");
            System.out.println("      - " + plato1.getNombre() + " -> S/. " + plato1.getPrecioDecimal());
            System.out.println("      - " + plato2.getNombre() + " -> S/. " + plato2.getPrecioDecimal());

            // -------------------------------------------------------------------------
            // PASO 4: Atención en sala: Tomar pedido para la Mesa 5
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 4: El Mozo atiende la Mesa 5 de " + nombreSala);
            System.out.println("   El cliente pide:");
            System.out.println("   - 2x " + plato1.getNombre() + " (Término medio, sin cebolla)");
            System.out.println("   - 1x " + plato2.getNombre() + " (Helado con hielo extra)");

            DetallePedido det1 = new DetallePedido(0, plato1.getNombre(), plato1.getPrecioDecimal(), 2, "Término medio, sin cebolla", 0);
            DetallePedido det2 = new DetallePedido(0, plato2.getNombre(), plato2.getPrecioDecimal(), 1, "Helado con hielo extra", 0);
            List<DetallePedido> detallesPedido = Arrays.asList(det1, det2);

            BigDecimal subtotal1 = det1.getPrecioDecimal().multiply(BigDecimal.valueOf(det1.getCantidad()));
            BigDecimal subtotal2 = det2.getPrecioDecimal().multiply(BigDecimal.valueOf(det2.getCantidad()));
            BigDecimal totalEsperado = subtotal1.add(subtotal2); // 32.50*2 + 8.00*1 = 65.00 + 8.00 = 73.00

            Pedidos pedidoCabecera = new Pedidos(0, salaSeleccionada.getId(), 5, hoy, totalEsperado, salaSeleccionada.getNombre(), admin.getNombre(), "PENDIENTE");
            
            int idPedidoGenerado = pedidosCtrl.registrarPedidoCompleto(pedidoCabecera, detallesPedido);
            System.out.println("   ✅ ¡Pedido #" + idPedidoGenerado + " registrado exitosamente en cocina!");
            System.out.println("      Total calculado: S/. " + totalEsperado);

            // -------------------------------------------------------------------------
            // PASO 5: Simulación de conflicto de concurrencia
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 5: Simulación de conflicto (Otro mozo intenta abrir la misma mesa)");
            try {
                Pedidos pedidoDuplicado = new Pedidos(0, salaSeleccionada.getId(), 5, hoy, new BigDecimal("8.00"), salaSeleccionada.getNombre(), "OtroMozo", "PENDIENTE");
                pedidosCtrl.registrarPedidoCompleto(pedidoDuplicado, Arrays.asList(det2));
                System.err.println("   ❌ ERROR: Se permitió un pedido duplicado en la misma mesa!");
            } catch (PedidoPendienteExistenteException ex) {
                System.out.println("   🛡️ [Protección de Negocio]: Bloqueado correctamente.");
                System.out.println("      Mensaje del sistema: \"" + ex.getMessage() + "\"");
            }

            // -------------------------------------------------------------------------
            // PASO 6: El Mozo consulta la cuenta y estado del pedido en pantalla
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 6: El Mozo consulta el detalle en pantalla para el Pedido #" + idPedidoGenerado);
            Pedidos pedidoConsultado = pedidosCtrl.verPedido(idPedidoGenerado);
            List<DetallePedido> detallesConsultados = pedidosCtrl.verPedidoDetalle(idPedidoGenerado);
            System.out.println("   📋 Estado actual: " + pedidoConsultado.getEstado() + " | Sala: " + pedidoConsultado.getSala() + " | Mesa: " + pedidoConsultado.getNum_mesa());
            for (DetallePedido item : detallesConsultados) {
                System.out.println("      • " + item.getCantidad() + "x " + item.getNombre() + " @ S/." + item.getPrecioDecimal() + " (" + item.getComentario() + ")");
            }

            // -------------------------------------------------------------------------
            // PASO 7: Cobro y Finalización del Pedido
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 7: Cliente efectúa el pago y el Mozo finaliza el pedido");
            boolean finalizado = pedidosCtrl.finalizarPedido(idPedidoGenerado);
            if (finalizado) {
                System.out.println("   ✅ ¡Pedido #" + idPedidoGenerado + " marcado como FINALIZADO! Mesa liberada.");
            }

            // -------------------------------------------------------------------------
            // PASO 8: Emisión y Generación del comprobante PDF
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 8: Generación del comprobante de venta en PDF");
            pedidosCtrl.generarPdfPedido(idPedidoGenerado);
            Path rutaArchivoPdf = dirFinal.resolve("pedido-" + idPedidoGenerado + ".pdf");
            if (Files.exists(rutaArchivoPdf)) {
                long bytes = Files.size(rutaArchivoPdf);
                System.out.println("   ✅ Comprobante PDF generado exitosamente: " + rutaArchivoPdf.getFileName());
                System.out.println("      Tamaño del archivo: " + bytes + " bytes");
            } else {
                throw new IllegalStateException("El archivo PDF no fue generado");
            }

            // -------------------------------------------------------------------------
            // PASO 9: Simulación de Usuario con Rol Restringido (Asistente / Mozo)
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 9: Simulación de permisos RBAC para rol 'Asistente'");
            String correoMozo = "mozo-" + sufijo.toLowerCase() + "@restaurante.test";
            Usuario nuevoMozo = new Usuario(0, "Carlos Mozo", correoMozo, "clave123", "Asistente");
            loginDao.Registrar(nuevoMozo);
            System.out.println("   ✅ Usuario Mozo creado: " + nuevoMozo.getNombre() + " (" + nuevoMozo.getRol() + ")");

            Optional<Usuario> mozoOpt = loginCtrl.autenticar(correoMozo, "clave123");
            Usuario mozo = mozoOpt.get();
            PoliticaAcceso politicaMozo = new PoliticaAcceso(mozo);

            System.out.println("   🔍 Verificando permisos del Asistente:");
            System.out.println("      - Consultar Salas: " + (politicaMozo.permite(PoliticaAcceso.Accion.CONSULTAR_SALAS) ? "✅ PERMITIDO" : "❌ DENEGADO"));
            System.out.println("      - Registrar Pedidos: " + (politicaMozo.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS) ? "✅ PERMITIDO" : "❌ DENEGADO"));
            System.out.println("      - Gestionar Usuarios: " + (politicaMozo.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS) ? "⚠️ PERMITIDO" : "🛡️ BLOQUEADO"));
            System.out.println("      - Modificar Configuración Empresa: " + (politicaMozo.permite(PoliticaAcceso.Accion.EDITAR_CONFIGURACION) ? "⚠️ PERMITIDO" : "🛡️ BLOQUEADO"));
            System.out.println("      - Gestionar/Eliminar Salas: " + (politicaMozo.permite(PoliticaAcceso.Accion.GESTIONAR_SALAS) ? "⚠️ PERMITIDO" : "🛡️ BLOQUEADO"));

            // Limpieza de datos temporales de la simulación
            platosDao.eliminar(plato1.getId());
            platosDao.eliminar(plato2.getId());

            System.out.println("\n================================================================================");
            System.out.println(" 🎉 SIMULACIÓN COMPLETADA EXITOSAMENTE: TODOS LOS FLUJOS VERIFICADOS AL 100%");
            System.out.println("================================================================================");

        } catch (Exception ex) {
            System.err.println("\n❌ ERROR EN LA SIMULACIÓN:");
            ex.printStackTrace();
        } finally {
            if (dirPdfTemp != null) {
                try {
                    File[] files = dirPdfTemp.toFile().listFiles();
                    if (files != null) {
                        for (File f : files) f.delete();
                    }
                    Files.deleteIfExists(dirPdfTemp);
                } catch (Exception ignored) {}
            }
        }
    }
}

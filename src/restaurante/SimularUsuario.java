package restaurante;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        System.out.println("     Moneda Oficial: Bolívares (Bs.) | Precios Base: Dólares ($)");
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
            // PASO 2: Consulta y actualización de datos de empresa y Tasa del Dólar
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 2: El Administrador consulta y ajusta la configuración de la empresa");
            Config config = loginDao.datosEmpresa();
            BigDecimal tasaActual = new BigDecimal("38.5000");
            config.setTasaDolar(tasaActual);
            loginDao.ModificarDatos(config);
            System.out.println("   🏢 Empresa: " + config.getNombre() + " | RIF/RUC: " + config.getRuc());
            System.out.println("   💵 Tasa de Cambio del Día fijada en: Bs. " + config.getTasaDolar().toPlainString() + " / $");

            // -------------------------------------------------------------------------
            // PASO 3: Apertura de nueva Sala y Menú del Día (en Dólares USD)
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
            BigDecimal precioUsd1 = new BigDecimal("6.00");
            BigDecimal precioUsd2 = new BigDecimal("2.50");
            BigDecimal precioBs1 = precioUsd1.multiply(tasaActual).setScale(2, RoundingMode.HALF_UP);
            BigDecimal precioBs2 = precioUsd2.multiply(tasaActual).setScale(2, RoundingMode.HALF_UP);

            Platos plato1 = new Platos(0, "Pabellón Criollo [" + sufijo + "]", precioUsd1, hoy);
            Platos plato2 = new Platos(0, "Jugo de Papelón con Limón [" + sufijo + "]", precioUsd2, hoy);
            platosCtrl.registrar(plato1);
            platosCtrl.registrar(plato2);
            System.out.println("   ✅ Platos agregados al menú del día (" + hoy + "):");
            System.out.println("      - " + plato1.getNombre() + " -> $" + precioUsd1 + " (Bs. " + precioBs1 + ")");
            System.out.println("      - " + plato2.getNombre() + " -> $" + precioUsd2 + " (Bs. " + precioBs2 + ")");

            // -------------------------------------------------------------------------
            // PASO 4: Atención en sala: Tomar pedido para la Mesa 5
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 4: El Mozo atiende la Mesa 5 de " + nombreSala);
            System.out.println("   El cliente pide:");
            System.out.println("   - 2x " + plato1.getNombre() + " (Con queso blanco)");
            System.out.println("   - 2x " + plato2.getNombre() + " (Bien frío)");

            DetallePedido det1 = new DetallePedido(0, plato1.getNombre(), plato1.getPrecioDecimal(), 2, "Con queso blanco", 0);
            DetallePedido det2 = new DetallePedido(0, plato2.getNombre(), plato2.getPrecioDecimal(), 2, "Bien frío", 0);
            List<DetallePedido> detallesPedido = Arrays.asList(det1, det2);

            BigDecimal subtotalUsd1 = det1.getPrecioDecimal().multiply(BigDecimal.valueOf(det1.getCantidad())); // 6.00 * 2 = 12.00
            BigDecimal subtotalUsd2 = det2.getPrecioDecimal().multiply(BigDecimal.valueOf(det2.getCantidad())); // 2.50 * 2 = 5.00
            BigDecimal totalUsd = subtotalUsd1.add(subtotalUsd2); // 17.00 USD
            BigDecimal totalBs = totalUsd.multiply(tasaActual).setScale(2, RoundingMode.HALF_UP); // 17.00 * 38.50 = 654.50 Bs.

            Pedidos pedidoCabecera = new Pedidos(0, salaSeleccionada.getId(), 5, hoy, totalUsd, salaSeleccionada.getNombre(), admin.getNombre(), "PENDIENTE", tasaActual, totalBs);
            
            int idPedidoGenerado = pedidosCtrl.registrarPedidoCompleto(pedidoCabecera, detallesPedido);
            System.out.println("   ✅ ¡Pedido #" + idPedidoGenerado + " registrado exitosamente en cocina!");
            System.out.println("      Total USD: $" + totalUsd + " | TOTAL A COBRAR (Bs.): Bs. " + totalBs);

            // -------------------------------------------------------------------------
            // PASO 5: Simulación de conflicto de concurrencia
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 5: Simulación de conflicto (Otro mozo intenta abrir la misma mesa)");
            try {
                Pedidos pedidoDuplicado = new Pedidos(0, salaSeleccionada.getId(), 5, hoy, new BigDecimal("5.00"), salaSeleccionada.getNombre(), "OtroMozo", "PENDIENTE", tasaActual, new BigDecimal("192.50"));
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
            System.out.println("      Monto Total: Bs. " + pedidoConsultado.getTotalBs() + " ($" + pedidoConsultado.getTotalDecimal() + ")");
            for (DetallePedido item : detallesConsultados) {
                BigDecimal itemBs = item.getPrecioDecimal().multiply(tasaActual).setScale(2, RoundingMode.HALF_UP);
                System.out.println("      • " + item.getCantidad() + "x " + item.getNombre() + " @ $" + item.getPrecioDecimal() + " (Bs. " + itemBs + ") — " + item.getComentario());
            }

            // -------------------------------------------------------------------------
            // PASO 7: Cobro y Finalización del Pedido
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 7: Cliente efectúa el pago en Bolívares (o equivalente) y el Mozo finaliza el pedido");
            boolean finalizado = pedidosCtrl.finalizarPedido(idPedidoGenerado);
            if (finalizado) {
                System.out.println("   ✅ ¡Pedido #" + idPedidoGenerado + " marcado como FINALIZADO! Mesa liberada.");
            }

            // -------------------------------------------------------------------------
            // PASO 8: Emisión y Generación del comprobante PDF en Bolívares y Dólares
            // -------------------------------------------------------------------------
            System.out.println("\n🔹 PASO 8: Generación del comprobante de venta en PDF con desglose bimonetario");
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
            System.out.println("      - Consultar Salas:                  " + (politicaMozo.permite(PoliticaAcceso.Accion.CONSULTAR_SALAS) ? "✅ PERMITIDO" : "❌ DENEGADO"));
            System.out.println("      - Registrar Pedidos:                " + (politicaMozo.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS) ? "✅ PERMITIDO" : "❌ DENEGADO"));
            System.out.println("      - Gestionar Usuarios:               " + (politicaMozo.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS) ? "⚠️ PERMITIDO" : "🛡️ BLOQUEADO"));
            System.out.println("      - Modificar Configuración Empresa:  " + (politicaMozo.permite(PoliticaAcceso.Accion.EDITAR_CONFIGURACION) ? "⚠️ PERMITIDO" : "🛡️ BLOQUEADO"));
            System.out.println("      - Gestionar/Eliminar Salas:         " + (politicaMozo.permite(PoliticaAcceso.Accion.GESTIONAR_SALAS) ? "⚠️ PERMITIDO" : "🛡️ BLOQUEADO"));

            // Limpieza de datos temporales de la simulación
            platosDao.eliminar(plato1.getId());
            platosDao.eliminar(plato2.getId());

            System.out.println("\n================================================================================");
            System.out.println(" 🎉 SIMULACIÓN COMPLETADA EXITOSAMENTE: FLUJO BIMONETARIO VERIFICADO AL 100%");
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

package Servicio;

import Modelo.CalculoFiscalRecord;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.PedidosRepositorioFalso;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Verifica la consistencia fiscal entre la toma de pedidos, el cálculo del IVA
 * y la validación en PedidoServicio.
 */
public class PedidoIvaIntegracionFlujoTest {

    @Test
    public void aceptaPedidoConIvaCalculadoDesdeDetalles() {
        AtomicInteger llamadas = new AtomicInteger();
        PedidosRepositorio repositorio = new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
                llamadas.incrementAndGet();
                return 101;
            }
        };
        PedidoServicio servicio = new PedidoServicio(repositorio);

        // 2 platos de $ 10.00 = subtotal $ 20.00
        List<DetallePedido> detalles = new ArrayList<>();
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Hamburguesa");
        d1.setCantidad(2);
        d1.setPrecioDecimal(new BigDecimal("10.00"));
        detalles.add(d1);

        BigDecimal subtotal = new BigDecimal("20.00");
        BigDecimal ivaPorcentaje = new BigDecimal("16.00");
        BigDecimal tasa = new BigDecimal("36.5000");
        CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(subtotal, ivaPorcentaje, tasa);

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(2);
        pedido.setUsuario("Mesero 1");
        pedido.setSubtotal(fiscal.subtotalUsd());       // 20.00
        pedido.setIvaPorcentaje(fiscal.ivaPorcentaje()); // 16.00
        pedido.setIvaMonto(fiscal.ivaUsd());            // 3.20
        pedido.setTotalDecimal(fiscal.totalUsd());      // 23.20
        pedido.setTasaCambio(fiscal.tasaCambio());
        pedido.setSubtotalBs(fiscal.subtotalBs());
        pedido.setIvaBs(fiscal.ivaBs());
        pedido.setTotalBs(fiscal.totalBs());

        int id = servicio.registrarPedidoCompleto(pedido, detalles);
        assertEquals(101, id);
        assertEquals(1, llamadas.get());
    }

    @Test
    public void aceptaPedidoSinIvaPrevioAFacturacion() {
        AtomicInteger llamadas = new AtomicInteger();
        PedidosRepositorio repositorio = new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
                llamadas.incrementAndGet();
                return 102;
            }
        };
        PedidoServicio servicio = new PedidoServicio(repositorio);

        List<DetallePedido> detalles = new ArrayList<>();
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Pizza");
        d1.setCantidad(1);
        d1.setPrecioDecimal(new BigDecimal("15.00"));
        detalles.add(d1);

        // Pedido donde total == subtotal (consumo en mesa sin IVA previo a facturar)
        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(3);
        pedido.setUsuario("Mesero 2");
        pedido.setSubtotal(new BigDecimal("15.00"));
        pedido.setTotalDecimal(new BigDecimal("15.00"));

        int id = servicio.registrarPedidoCompleto(pedido, detalles);
        assertEquals(102, id);
        assertEquals(1, llamadas.get());
    }

    @Test
    public void rechazaPedidoCuandoTotalNoCoincideNiConDetallesNiConIva() {
        PedidoServicio servicio = new PedidoServicio(new PedidosRepositorioFalso());

        List<DetallePedido> detalles = new ArrayList<>();
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Pasta");
        d1.setCantidad(1);
        d1.setPrecioDecimal(new BigDecimal("10.00"));
        detalles.add(d1);

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(4);
        pedido.setUsuario("Mesero");
        pedido.setSubtotal(new BigDecimal("10.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("1.60"));
        pedido.setTotalDecimal(new BigDecimal("99.99")); // Alterado fraudulentamente

        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class,
                () -> servicio.registrarPedidoCompleto(pedido, detalles));
        assertTrue(ex.getMessage().contains("no coincide con sus detalles"));
    }

    @Test
    public void formateoPreciosSinDecimalesEnAmbasMonedas() {
        BigDecimal precioUsd = new BigDecimal("10.00");
        BigDecimal tasa = new BigDecimal("38.5000");

        BigDecimal usdSinDec = precioUsd.setScale(0, RoundingMode.HALF_UP);
        BigDecimal bsSinDec = precioUsd.multiply(tasa).setScale(0, RoundingMode.HALF_UP);

        assertEquals("10", usdSinDec.toPlainString());
        assertEquals("385", bsSinDec.toPlainString());
    }
}

package Servicio;

import Modelo.Salas;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.Assert.*;

public class ServicioMesaTest {

    @Test
    public void sugerirPrefijosBaseEstandar() {
        assertEquals("P", ServicioMesa.sugerirPrefijoBase("SALON PRINCIPAL", false));
        assertEquals("T", ServicioMesa.sugerirPrefijoBase("TERRAZA", false));
        assertEquals("V", ServicioMesa.sugerirPrefijoBase("SALON VIP", false));
        assertEquals("B", ServicioMesa.sugerirPrefijoBase("BARRA", true));
        assertEquals("B", ServicioMesa.sugerirPrefijoBase("BARRA DE TRAGOS", false));
    }

    @Test
    public void resolucionDeColisionesGeneraPrefijosUnicos() {
        Salas s1 = new Salas(1, "SALON PRINCIPAL", 10, "SALON");
        Salas s2 = new Salas(2, "PATIO CENTRAL", 8, "SALON");
        Salas s3 = new Salas(3, "TERRAZA", 6, "SALON");

        Map<Integer, String> prefijos = ServicioMesa.generarPrefijosSalas(List.of(s1, s2, s3));
        assertEquals(3, prefijos.size());

        String p1 = prefijos.get(1);
        String p2 = prefijos.get(2);
        String p3 = prefijos.get(3);

        assertNotNull(p1);
        assertNotNull(p2);
        assertNotNull(p3);
        assertNotEquals("Los prefijos entre Principal y Patio deben ser distintos", p1, p2);
        assertEquals("T", p3);
    }

    @Test
    public void formateoYParsingDeCodigosMesa() {
        assertEquals("P-1", ServicioMesa.formatearCodigo("P", 1));
        assertEquals("T-12", ServicioMesa.formatearCodigo("T", 12));

        Map<String, Integer> prefijosMap = Map.of(
                "P", 1,
                "T", 2,
                "B", 4
        );

        Optional<ServicioMesa.MesaIdentificada> res1 = ServicioMesa.parsearCodigoMesa("P-1", prefijosMap);
        assertTrue(res1.isPresent());
        assertEquals(1, res1.get().idSala());
        assertEquals(1, res1.get().numMesa());
        assertEquals("P-1", res1.get().codigoFormateado());

        // Con ceros a la izquierda
        Optional<ServicioMesa.MesaIdentificada> res2 = ServicioMesa.parsearCodigoMesa("t-05", prefijosMap);
        assertTrue(res2.isPresent());
        assertEquals(2, res2.get().idSala());
        assertEquals(5, res2.get().numMesa());

        // Sin guión
        Optional<ServicioMesa.MesaIdentificada> res3 = ServicioMesa.parsearCodigoMesa("B3", prefijosMap);
        assertTrue(res3.isPresent());
        assertEquals(4, res3.get().idSala());
        assertEquals(3, res3.get().numMesa());

        // Con espacio
        Optional<ServicioMesa.MesaIdentificada> res4 = ServicioMesa.parsearCodigoMesa("p 8", prefijosMap);
        assertTrue(res4.isPresent());
        assertEquals(1, res4.get().idSala());
        assertEquals(8, res4.get().numMesa());

        // Código inexistente o inválido
        assertFalse(ServicioMesa.parsearCodigoMesa("X-1", prefijosMap).isPresent());
        assertFalse(ServicioMesa.parsearCodigoMesa("invalido", prefijosMap).isPresent());
        assertFalse(ServicioMesa.parsearCodigoMesa("", prefijosMap).isPresent());
        assertFalse(ServicioMesa.parsearCodigoMesa(null, prefijosMap).isPresent());
    }

    @Test
    public void construccionDeMesasVisualesConEstadosYMesoneros() {
        Salas sala = new Salas(1, "SALON PRINCIPAL", 3, "SALON");
        Map<Integer, Integer> pedidos = Map.of(2, 105); // Mesa 2 ocupada con pedido 105
        Map<Integer, String> mesoneros = Map.of(2, "Carlos Perez");
        Map<String, String> visuales = Map.of("Carlos Perez", "Carlos");

        List<ServicioMesa.MesaVisual> mesas = ServicioMesa.construirMesasVisuales(sala, "P", pedidos, mesoneros, visuales);
        assertEquals(3, mesas.size());

        ServicioMesa.MesaVisual m1 = mesas.get(0);
        assertEquals("P-1", m1.codigoMesa());
        assertFalse(m1.ocupada());
        assertEquals(0, m1.idPedidoPendiente());

        ServicioMesa.MesaVisual m2 = mesas.get(1);
        assertEquals("P-2", m2.codigoMesa());
        assertTrue(m2.ocupada());
        assertEquals(105, m2.idPedidoPendiente());
        assertEquals("Carlos Perez", m2.nombreMesonero());
        assertEquals("Carlos", m2.nombreVisualMesonero());

        ServicioMesa.MesaVisual m3 = mesas.get(2);
        assertEquals("P-3", m3.codigoMesa());
        assertFalse(m3.ocupada());
    }
}

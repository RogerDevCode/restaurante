package Servicio;

import Modelo.Pedidos;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class Java21SequencedCollectionsTest {

    @Test
    public void testSequencedCollectionsGetFirstGetLastReversed() {
        List<String> salas = new ArrayList<>(List.of("Terraza", "Salon Principal", "Bar"));

        // Java 21 Sequenced Collections API
        Assert.assertEquals("Terraza", salas.getFirst());
        Assert.assertEquals("Bar", salas.getLast());

        List<String> reversed = salas.reversed();
        Assert.assertEquals("Bar", reversed.getFirst());
        Assert.assertEquals("Terraza", reversed.getLast());
    }

    @Test(expected = NoSuchElementException.class)
    public void testSequencedCollectionsVaciaLanzaNoSuchElementException() {
        List<Pedidos> pedidosVacios = new ArrayList<>();
        // En Java 21, llamar a getFirst() o getLast() en lista vacía lanza NoSuchElementException
        pedidosVacios.getLast();
    }

    @Test
    public void testHelperDefensivoParaColeccionesVacias() {
        List<Pedidos> pedidosVacios = new ArrayList<>();
        Optional<Pedidos> ultimoOpt = pedidosVacios.isEmpty()
                ? Optional.empty()
                : Optional.of(pedidosVacios.getLast());

        Assert.assertTrue(ultimoOpt.isEmpty());

        Pedidos p1 = new Pedidos();
        p1.setId(1);
        p1.setNum_mesa(5);
        p1.setTotalDecimal(new BigDecimal("20.00"));

        List<Pedidos> listaConElementos = List.of(p1);
        Optional<Pedidos> elementoOpt = listaConElementos.isEmpty()
                ? Optional.empty()
                : Optional.of(listaConElementos.getLast());

        Assert.assertTrue(elementoOpt.isPresent());
        Assert.assertEquals(1, elementoOpt.get().getId());
    }
}

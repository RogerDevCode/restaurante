package Controlador;

import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Usuario;
import Servicio.PlatosServicio;
import Servicio.PoliticaAcceso;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PlatosControladorTest {
    @Test
    public void delegaConsultaYMutacionesAlServicio() {
        RepositorioFalso repositorio = new RepositorioFalso();
        PlatosControlador controlador = controlador(repositorio);
        Platos plato = new Platos(7, "Sopa", new BigDecimal("4.50"), "2026-10-05");

        assertTrue(controlador.listarPorFecha("", "2026-10-05").isEmpty());
        assertTrue(controlador.registrar(plato));
        assertTrue(controlador.modificar(plato));
        assertTrue(controlador.eliminar(7));
        assertTrue(controlador.desactivar(7));
        assertTrue(controlador.reactivar(7));
        assertTrue(controlador.listarInactivos().isEmpty());
        assertEquals(7, repositorio.operaciones.get());
        assertEquals(7, repositorio.ultimoId.get());
    }

    @Test
    public void conservaCausaAlPropagarElFalloDelServicio() {
        RepositorioFalso repositorio = new RepositorioFalso();
        IllegalStateException fallo = new IllegalStateException("error simulado");
        repositorio.fallo = fallo;

        IllegalStateException propagada = assertThrows(IllegalStateException.class,
                () -> controlador(repositorio).listarPorFecha("", "2026-10-05"));

        assertSame(fallo, propagada);
    }

    private PlatosControlador controlador(RepositorioFalso repositorio) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        return new PlatosControlador(new PlatosServicio(repositorio, new PoliticaAcceso(usuario)));
    }

    private static final class RepositorioFalso implements PlatosRepositorio {
        private final AtomicInteger operaciones = new AtomicInteger();
        private final AtomicInteger ultimoId = new AtomicInteger();
        private final AtomicReference<Platos> ultimoPlato = new AtomicReference<>();
        private RuntimeException fallo;

        @Override public boolean registrar(Platos plato) { verificar(); ultimoPlato.set(plato); return true; }
        @Override public List<Platos> listarPorFecha(String nombre, String fecha) { verificar(); return Collections.emptyList(); }
        @Override public boolean desactivar(int id) { verificar(); ultimoId.set(id); return true; }
        @Override public boolean reactivar(int id) { verificar(); ultimoId.set(id); return true; }
        @Override public List<Platos> listarInactivos() { verificar(); return Collections.emptyList(); }
        @Override public boolean eliminar(int id) { return desactivar(id); }
        @Override public boolean modificar(Platos plato) { verificar(); ultimoPlato.set(plato); ultimoId.set(plato.getId()); return true; }
        private void verificar() { operaciones.incrementAndGet(); if (fallo != null) throw fallo; }
    }
}

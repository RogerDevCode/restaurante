package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SalasServicioTest {
    @Test
    public void asistentePuedeConsultarPeroNoPuedeMutarYNoLlegaAlRepositorio() {
        FakeSalasRepositorio repositorio = new FakeSalasRepositorio();
        SalasServicio servicio = new SalasServicio(repositorio, politica("Asistente"));

        assertTrue(servicio.listar().isEmpty());
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(new Salas(0, "Principal", 4)));
        assertThrows(ErrorAplicacionException.class, () -> servicio.modificar(new Salas(1, "Principal", 4)));
        assertThrows(ErrorAplicacionException.class, () -> servicio.eliminar(1));

        assertEquals(1, repositorio.listados.get());
        assertEquals(0, repositorio.escrituras.get());
    }

    @Test
    public void administradorValidaNormalizaYPropagaResultadoDePersistencia() {
        FakeSalasRepositorio repositorio = new FakeSalasRepositorio();
        repositorio.resultadoEscritura = false;
        SalasServicio servicio = new SalasServicio(repositorio, politica("Administrador"));

        assertTrue(servicio.listar().isEmpty());
        assertEquals(false, servicio.registrar(new Salas(0, "  Terraza  ", 3)));
        assertEquals("Terraza", repositorio.recibida.get().getNombre());
        assertEquals(3, repositorio.recibida.get().getMesas());
        assertEquals(false, servicio.modificar(new Salas(7, "  Interior ", 5)));
        assertEquals(7, repositorio.recibida.get().getId());
        assertEquals(true, servicio.eliminar(7));
        assertEquals(3, repositorio.escrituras.get());
    }

    @Test
    public void rechazaDatosInvalidosAntesDeLlamarAlRepositorio() {
        FakeSalasRepositorio repositorio = new FakeSalasRepositorio();
        SalasServicio servicio = new SalasServicio(repositorio, politica("Administrador"));

        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(null));
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(new Salas(0, " ", 2)));
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(new Salas(0, "Principal", 0)));
        assertThrows(ErrorAplicacionException.class, () -> servicio.modificar(new Salas(0, "Principal", 2)));
        assertThrows(ErrorAplicacionException.class, () -> servicio.eliminar(0));

        assertEquals(0, repositorio.escrituras.get());
    }

    @Test
    public void conservaExcepcionDelRepositorio() {
        FakeSalasRepositorio repositorio = new FakeSalasRepositorio();
        IllegalStateException fallo = new IllegalStateException("fallo JDBC simulado");
        repositorio.fallo = fallo;
        SalasServicio servicio = new SalasServicio(repositorio, politica("Administrador"));

        IllegalStateException propagada = assertThrows(IllegalStateException.class,
                () -> servicio.registrar(new Salas(0, "Principal", 2)));

        assertSame(fallo, propagada);
    }

    private PoliticaAcceso politica(String rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return new PoliticaAcceso(usuario);
    }

    private static final class FakeSalasRepositorio implements SalasRepositorio {
        private final AtomicInteger listados = new AtomicInteger();
        private final AtomicInteger escrituras = new AtomicInteger();
        private final AtomicReference<Salas> recibida = new AtomicReference<>();
        private boolean resultadoEscritura = true;
        private RuntimeException fallo;

        @Override
        public boolean registrar(Salas sala) {
            verificarEscritura(sala);
            return resultadoEscritura;
        }

        @Override
        public List<Salas> listar() {
            listados.incrementAndGet();
            if (fallo != null) throw fallo;
            return Collections.emptyList();
        }

        @Override
        public boolean eliminar(int id) {
            verificarEscritura(null);
            return true;
        }

        @Override
        public boolean modificar(Salas sala) {
            verificarEscritura(sala);
            return resultadoEscritura;
        }

        private void verificarEscritura(Salas sala) {
            escrituras.incrementAndGet();
            if (fallo != null) throw fallo;
            recibida.set(sala);
        }
    }
}

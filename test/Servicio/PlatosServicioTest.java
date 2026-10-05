package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Usuario;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PlatosServicioTest {
    @Test
    public void asistenteConsultaMenuPeroNoPuedeCambiarCatalogo() {
        FakePlatosRepositorio repositorio = new FakePlatosRepositorio();
        PlatosServicio servicio = new PlatosServicio(repositorio, politica("Asistente"));

        assertTrue(servicio.listarPorFecha("  sopa ", "2026-10-05").isEmpty());
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(plato(0, "Sopa", "4.50")));
        assertThrows(ErrorAplicacionException.class, () -> servicio.modificar(plato(4, "Sopa", "4.50")));
        assertThrows(ErrorAplicacionException.class, () -> servicio.eliminar(4));

        assertEquals("sopa", repositorio.filtro.get());
        assertEquals("2026-10-05", repositorio.fecha.get());
        assertEquals(0, repositorio.escrituras.get());
    }

    @Test
    public void administradorNormalizaYPropagaResultadosDeEscritura() {
        FakePlatosRepositorio repositorio = new FakePlatosRepositorio();
        repositorio.resultado = false;
        PlatosServicio servicio = new PlatosServicio(repositorio, politica("Administrador"));

        assertFalse(servicio.registrar(plato(0, "  Sopa  ", "4.50")));
        assertEquals("Sopa", repositorio.recibido.get().getNombre());
        assertFalse(servicio.modificar(plato(9, "  Crema ", "5.25")));
        assertEquals(9, repositorio.recibido.get().getId());
        assertTrue(servicio.eliminar(9));
        assertEquals(3, repositorio.escrituras.get());
    }

    @Test
    public void rechazaPreciosFechasYSeleccionInvalidaAntesDePersistir() {
        FakePlatosRepositorio repositorio = new FakePlatosRepositorio();
        PlatosServicio servicio = new PlatosServicio(repositorio, politica("Administrador"));

        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(plato(0, "Sopa", "0")));
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(plato(0, "Sopa", "1.001")));
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(plato(0, "Sopa", "100000000.00")));
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(plato(0, "Sopa", "1.00", "2026-02-30")));
        assertThrows(ErrorAplicacionException.class, () -> servicio.modificar(plato(0, "Sopa", "1.00")));
        assertThrows(ErrorAplicacionException.class, () -> servicio.eliminar(0));
        assertThrows(ErrorAplicacionException.class, () -> servicio.listarPorFecha("", "ayer"));

        assertEquals(0, repositorio.escrituras.get());
        assertEquals(0, repositorio.consultas.get());
    }

    @Test
    public void propagaSinCambiarLaExcepcionDelRepositorio() {
        FakePlatosRepositorio repositorio = new FakePlatosRepositorio();
        IllegalStateException fallo = new IllegalStateException("fallo de repositorio");
        repositorio.fallo = fallo;
        PlatosServicio servicio = new PlatosServicio(repositorio, politica("Administrador"));

        IllegalStateException propagada = assertThrows(IllegalStateException.class,
                () -> servicio.listarPorFecha("", "2026-10-05"));

        assertSame(fallo, propagada);
    }

    private PoliticaAcceso politica(String rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return new PoliticaAcceso(usuario);
    }

    private Platos plato(int id, String nombre, String precio) {
        return plato(id, nombre, precio, "2026-10-05");
    }

    private Platos plato(int id, String nombre, String precio, String fecha) {
        return new Platos(id, nombre, new BigDecimal(precio), fecha);
    }

    private static final class FakePlatosRepositorio implements PlatosRepositorio {
        private final AtomicInteger escrituras = new AtomicInteger();
        private final AtomicInteger consultas = new AtomicInteger();
        private final AtomicReference<Platos> recibido = new AtomicReference<>();
        private final AtomicReference<String> filtro = new AtomicReference<>();
        private final AtomicReference<String> fecha = new AtomicReference<>();
        private boolean resultado = true;
        private RuntimeException fallo;

        @Override public boolean registrar(Platos plato) { escritura(plato); return resultado; }
        @Override public List<Platos> listarPorFecha(String nombre, String fechaConsulta) {
            consultas.incrementAndGet();
            if (fallo != null) throw fallo;
            filtro.set(nombre);
            fecha.set(fechaConsulta);
            return Collections.emptyList();
        }
        @Override public boolean eliminar(int id) { escritura(null); return true; }
        @Override public boolean modificar(Platos plato) { escritura(plato); return resultado; }
        private void escritura(Platos plato) {
            escrituras.incrementAndGet();
            if (fallo != null) throw fallo;
            recibido.set(plato);
        }
    }
}

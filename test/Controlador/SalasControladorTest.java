package Controlador;

import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.PoliticaAcceso;
import Servicio.SalasServicio;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class SalasControladorTest {
    @Test
    public void coordinaLasOperacionesDeSalaSinCambiarSusResultados() {
        RepositorioFalso repositorio = new RepositorioFalso();
        SalasControlador controlador = controlador(repositorio);
        Salas sala = new Salas(8, "Principal", 4);

        assertEquals(0, controlador.listar().size());
        assertEquals(true, controlador.registrar(sala));
        assertEquals(true, controlador.modificar(sala));
        assertEquals(true, controlador.eliminar(8));

        assertEquals(4, repositorio.operaciones.get());
        assertEquals(8, repositorio.ultimoId.get());
        assertEquals("Principal", repositorio.ultimaSala.get().getNombre());
    }

    @Test
    public void conservaLaExcepcionQuePropagaElCasoDeUso() {
        RepositorioFalso repositorio = new RepositorioFalso();
        IllegalStateException fallo = new IllegalStateException("error de persistencia");
        repositorio.fallo = fallo;
        SalasControlador controlador = controlador(repositorio);

        IllegalStateException propagada = assertThrows(IllegalStateException.class,
                () -> controlador.listar());

        assertSame(fallo, propagada);
    }

    private SalasControlador controlador(RepositorioFalso repositorio) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        return new SalasControlador(new SalasServicio(repositorio, new PoliticaAcceso(usuario)));
    }

    private static final class RepositorioFalso implements SalasRepositorio {
        private final AtomicInteger operaciones = new AtomicInteger();
        private final AtomicInteger ultimoId = new AtomicInteger();
        private final AtomicReference<Salas> ultimaSala = new AtomicReference<>();
        private RuntimeException fallo;

        @Override
        public boolean registrar(Salas sala) {
            verificar();
            ultimaSala.set(sala);
            return true;
        }

        @Override
        public List<Salas> listar() {
            verificar();
            return Collections.emptyList();
        }

        @Override
        public boolean eliminar(int id) {
            verificar();
            ultimoId.set(id);
            return true;
        }

        @Override
        public boolean modificar(Salas sala) {
            verificar();
            ultimaSala.set(sala);
            ultimoId.set(sala.getId());
            return true;
        }

        private void verificar() {
            operaciones.incrementAndGet();
            if (fallo != null) throw fallo;
        }
    }
}

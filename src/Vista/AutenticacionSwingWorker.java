package Vista;

import Controlador.LoginControlador;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Ejecuta JDBC/hash de autenticación fuera del EDT y entrega resultados dentro del EDT. */
final class AutenticacionSwingWorker extends SwingWorker<Optional<Usuario>, Void> {
    private final LoginControlador controlador;
    private final String correo;
    private final String clave;
    private final Consumer<Optional<Usuario>> alCompletar;
    private final Consumer<Throwable> alFallar;

    AutenticacionSwingWorker(LoginControlador controlador, String correo, String clave,
            Consumer<Optional<Usuario>> alCompletar, Consumer<Throwable> alFallar) {
        if (controlador == null || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El controlador y callbacks de autenticación son obligatorios.");
        }
        this.controlador = controlador;
        this.correo = correo;
        this.clave = clave;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected Optional<Usuario> doInBackground() {
        return controlador.autenticarUsuario(correo, clave);
    }

    @Override
    protected void done() {
        try {
            alCompletar.accept(get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            alFallar.accept(ex);
        } catch (ExecutionException ex) {
            Throwable causa = ex.getCause() == null ? ex : ex.getCause();
            if (causa instanceof Error) {
                throw (Error) causa;
            }
            alFallar.accept(causa);
        } catch (CancellationException ex) {
            alFallar.accept(ex);
        }
    }
}

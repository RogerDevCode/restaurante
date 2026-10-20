package Servicio;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServicioRespaldoCoberturaTest {

    @Test
    public void listaBlancaIncluyeLasTablasFinancierasNecesariasParaConciliar() {
        for (String tabla : List.of("pedido_liquidaciones", "pedido_saldos_movimientos", "auditoria_cambios_precio")) {
            assertTrue("El respaldo debe cubrir la tabla financiera " + tabla,
                    ServicioRespaldoBaseDatos.TABLAS_PERMITIDAS_RESPALDO.contains(tabla));
        }
    }

    @Test
    public void laListaBlancaNuncaIncluyeTablasDeInfraestructura() {
        for (String tabla : ServicioRespaldoBaseDatos.TABLAS_PROTEGIDAS_INFRAESTRUCTURA) {
            assertFalse("La tabla de infraestructura " + tabla + " no puede ser restaurable",
                    ServicioRespaldoBaseDatos.TABLAS_PERMITIDAS_RESPALDO.contains(tabla));
        }
    }
}

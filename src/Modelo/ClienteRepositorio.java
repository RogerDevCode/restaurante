package Modelo;

import java.util.List;

public interface ClienteRepositorio {
    boolean guardarOActualizar(Cliente cliente);
    List<Cliente> buscarClientes(String criterio);
    Cliente buscarPorDocumento(String documento);
    List<Pedidos> listarFacturasCliente(String documentoOCriterio);
    default List<Pedidos> listarFacturasRecientesDelDia() {
        return List.of();
    }
    EstadisticasDashboard obtenerEstadisticasDashboard();
}

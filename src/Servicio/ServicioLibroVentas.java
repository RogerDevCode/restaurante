package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.FiltroLibroVentas;
import Modelo.LibroVentasDao;
import Modelo.RegistroLibroVenta;
import Modelo.ResumenFiscalVentas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de negocio para la orquestación, validación tributaria y cálculo
 * del Libro de Ventas Fiscal conforme a los lineamientos del SENIAT.
 */
public class ServicioLibroVentas {

    private final LibroVentasDao libroVentasDao;

    public ServicioLibroVentas() {
        this(new LibroVentasDao());
    }

    public ServicioLibroVentas(LibroVentasDao libroVentasDao) {
        this.libroVentasDao = Objects.requireNonNull(libroVentasDao, "El DAO del libro de ventas no puede ser nulo.");
    }

    public List<RegistroLibroVenta> consultarLibro(FiltroLibroVentas filtro) {
        if (filtro == null) {
            throw ErrorAplicacionException.validacion("El filtro de libro de ventas es obligatorio.");
        }
        return libroVentasDao.consultarLibroVentas(filtro);
    }

    public ResumenFiscalVentas calcularResumen(List<RegistroLibroVenta> registros) {
        return ResumenFiscalVentas.desdeRegistros(registros);
    }

    /**
     * Valida que no existan saltos en los números de factura correlativos dentro del lote de registros.
     * Retorna una lista con los números de factura faltantes en caso de detectar vacíos.
     */
    public List<Integer> detectarSaltosCorrelativo(List<RegistroLibroVenta> registros) {
        if (registros == null || registros.size() < 2) {
            return Collections.emptyList();
        }

        List<Integer> faltantes = new ArrayList<>();
        Integer anterior = null;

        for (RegistroLibroVenta reg : registros) {
            try {
                int actual = Integer.parseInt(reg.getNroFactura());
                if (anterior != null && actual > anterior + 1) {
                    for (int salto = anterior + 1; salto < actual; salto++) {
                        faltantes.add(salto);
                    }
                }
                anterior = actual;
            } catch (NumberFormatException ignored) {
                // Si la factura contiene caracteres alfanuméricos especiales se omite la validación aritmética
            }
        }

        return faltantes;
    }
}

package Modelo;

import java.math.BigDecimal;

/** Snapshot inmutable de una línea enviada a cocina o retirada de su preparación. */
public record MovimientoComanda(
        Accion accion,
        String nombre,
        int cantidad,
        String comentario,
        Integer idPlato,
        String monedaBase,
        BigDecimal precio,
        Boolean aplicaIva,
        Boolean precioIncluyeIva,
        BigDecimal ivaPorcentaje) {

    public enum Accion {
        PREPARAR,
        RETIRAR
    }

    public MovimientoComanda {
        if (accion == null || nombre == null || nombre.isBlank() || cantidad <= 0) {
            throw ErrorAplicacionException.validacion("El movimiento de comanda debe tener acción, plato y cantidad positiva.");
        }
        comentario = comentario == null ? "" : comentario;
        precio = precio == null ? BigDecimal.ZERO.setScale(2) : precio.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public static MovimientoComanda desde(Accion accion, DetallePedido detalle, int cantidad) {
        if (detalle == null) {
            throw ErrorAplicacionException.validacion("La línea de comanda es obligatoria.");
        }
        return new MovimientoComanda(accion, detalle.getNombre(), cantidad, detalle.getComentario(),
                detalle.getIdPlato(), detalle.getMonedaBase(), detalle.getPrecioDecimal(), detalle.getAplicaIva(),
                detalle.getPrecioIncluyeIva(), detalle.getIvaPorcentaje());
    }

    public DetallePedido convertirADetalle(int cantidadNueva) {
        DetallePedido detalle = new DetallePedido();
        detalle.setNombre(nombre);
        detalle.setCantidad(cantidadNueva);
        detalle.setComentario(comentario);
        detalle.setIdPlato(idPlato);
        detalle.setMonedaBase(monedaBase);
        detalle.setPrecioDecimal(precio);
        detalle.setAplicaIva(aplicaIva);
        detalle.setPrecioIncluyeIva(precioIncluyeIva);
        detalle.setIvaPorcentaje(ivaPorcentaje);
        return detalle;
    }
}

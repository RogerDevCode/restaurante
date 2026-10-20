package Modelo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Marcha de cocina inmutable; representa exactamente un despacho o ajuste. */
public record ComandaMarcha(
        long id,
        int idPedido,
        int secuencia,
        Tipo tipo,
        List<MovimientoComanda> movimientos) {

    public enum Tipo {
        INICIAL,
        INCREMENTO,
        AJUSTE,
        BASE_LEGADA
    }

    public ComandaMarcha {
        if (idPedido <= 0 || secuencia <= 0 || tipo == null) {
            throw ErrorAplicacionException.validacion("La marcha de cocina requiere pedido, secuencia y tipo válidos.");
        }
        movimientos = Collections.unmodifiableList(new ArrayList<>(movimientos == null ? List.of() : movimientos));
    }

    /** Calcula diferencias por plato y comentario. Precio/fiscalidad no vuelven a mandar un plato a cocina. */
    public static List<MovimientoComanda> calcularDiferencias(
            List<DetallePedido> anteriores, List<DetallePedido> actuales) {
        Map<String, Acumulado> previos = acumular(anteriores);
        Map<String, Acumulado> nuevos = acumular(actuales);
        List<MovimientoComanda> resultado = new ArrayList<>();

        for (Map.Entry<String, Acumulado> entrada : nuevos.entrySet()) {
            Acumulado nuevo = entrada.getValue();
            int anterior = previos.containsKey(entrada.getKey()) ? previos.get(entrada.getKey()).cantidad : 0;
            if (nuevo.cantidad > anterior) {
                resultado.add(MovimientoComanda.desde(MovimientoComanda.Accion.PREPARAR,
                        nuevo.detalle, nuevo.cantidad - anterior));
            }
        }
        for (Map.Entry<String, Acumulado> entrada : previos.entrySet()) {
            Acumulado anterior = entrada.getValue();
            int nuevo = nuevos.containsKey(entrada.getKey()) ? nuevos.get(entrada.getKey()).cantidad : 0;
            if (anterior.cantidad > nuevo) {
                resultado.add(MovimientoComanda.desde(MovimientoComanda.Accion.RETIRAR,
                        anterior.detalle, anterior.cantidad - nuevo));
            }
        }
        return List.copyOf(resultado);
    }

    /** Reconstruye lo ya enviado aplicando PREPARAR/RETIRAR en orden histórico. */
    public static List<DetallePedido> componerEstadoEnviado(List<MovimientoComanda> historial) {
        Map<String, EstadoLinea> estado = new LinkedHashMap<>();
        if (historial != null) {
            for (MovimientoComanda movimiento : historial) {
                String clave = clave(movimiento);
                EstadoLinea linea = estado.get(clave);
                int cantidadAnterior = linea == null ? 0 : linea.cantidad;
                int cantidadNueva = movimiento.accion() == MovimientoComanda.Accion.PREPARAR
                        ? Math.addExact(cantidadAnterior, movimiento.cantidad())
                        : Math.max(0, cantidadAnterior - movimiento.cantidad());
                if (cantidadNueva == 0) estado.remove(clave);
                else estado.put(clave, new EstadoLinea(movimiento, cantidadNueva));
            }
        }
        return estado.values().stream()
                .map(linea -> linea.movimiento.convertirADetalle(linea.cantidad))
                .toList();
    }

    private static Map<String, Acumulado> acumular(List<DetallePedido> detalles) {
        Map<String, Acumulado> resultado = new LinkedHashMap<>();
        if (detalles == null) return resultado;
        for (DetallePedido detalle : detalles) {
            if (detalle == null || detalle.getCantidad() <= 0) continue;
            String clave = clave(detalle);
            Acumulado existente = resultado.get(clave);
            if (existente == null) {
                resultado.put(clave, new Acumulado(detalle, detalle.getCantidad()));
            } else {
                resultado.put(clave, new Acumulado(existente.detalle, existente.cantidad + detalle.getCantidad()));
            }
        }
        return resultado;
    }

    private static String clave(DetallePedido detalle) {
        String identidadPlato = detalle.getIdPlato() != null && detalle.getIdPlato() > 0
                ? "id:" + detalle.getIdPlato()
                : "nombre:" + normalizar(detalle.getNombre());
        return identidadPlato + "|nota:" + normalizar(detalle.getComentario());
    }

    private static String clave(MovimientoComanda movimiento) {
        String identidadPlato = movimiento.idPlato() != null && movimiento.idPlato() > 0
                ? "id:" + movimiento.idPlato()
                : "nombre:" + normalizar(movimiento.nombre());
        return identidadPlato + "|nota:" + normalizar(movimiento.comentario());
    }

    private static String normalizar(String texto) {
        String valor = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(valor, Normalizer.Form.NFC);
    }

    private record Acumulado(DetallePedido detalle, int cantidad) {}
    private record EstadoLinea(MovimientoComanda movimiento, int cantidad) {}
}

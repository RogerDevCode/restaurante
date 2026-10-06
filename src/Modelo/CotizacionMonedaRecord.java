package Modelo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Registro inmutable (Java 21 Record - JEP 395) que encapsula el cálculo
 * bimonetario de una transacción (Total en USD, Total en Bs. y Tasa de Cambio).
 */
public record CotizacionMonedaRecord(BigDecimal totalUsd, BigDecimal totalBs, BigDecimal tasaCambio) {

    public CotizacionMonedaRecord {
        Objects.requireNonNull(totalUsd, "El total en USD no puede ser nulo.");
        Objects.requireNonNull(totalBs, "El total en Bs. no puede ser nulo.");
        Objects.requireNonNull(tasaCambio, "La tasa de cambio no puede ser nula.");

        if (totalUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El total en USD no puede ser negativo.");
        }
        if (totalBs.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El total en Bs. no puede ser negativo.");
        }
        if (tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio debe ser un valor positivo.");
        }
    }

    /**
     * Retorna una representación formateada para mostrar en la interfaz de usuario.
     */
    public String textoBimonetario() {
        return "Bs. %s ($ %s)".formatted(totalBs.toPlainString(), totalUsd.toPlainString());
    }
}

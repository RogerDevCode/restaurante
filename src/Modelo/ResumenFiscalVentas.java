package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Encapsula los totales y el resumen consolidado del Libro de Ventas para el período
 * (mes o quincena), listo para transferir a la Forma 30 del IVA y declaración de IGTF.
 */
public record ResumenFiscalVentas(
        int totalOperaciones,
        int totalFacturasEmitidas,
        int totalFacturasAnuladas,
        BigDecimal totalVentasExentasBs,
        BigDecimal totalBaseImponibleGeneralBs,
        BigDecimal totalDebitoFiscalGeneralBs,
        BigDecimal totalBaseImponibleReducidaBs,
        BigDecimal totalDebitoFiscalReducidoBs,
        BigDecimal totalBaseImponibleAdicionalBs,
        BigDecimal totalDebitoFiscalAdicionalBs,
        BigDecimal totalVentasConIvaBs,
        BigDecimal totalIvaRetenidoBs,
        BigDecimal totalIgtfPercibidoBs) {

    public static ResumenFiscalVentas desdeRegistros(List<RegistroLibroVenta> registros) {
        if (registros == null || registros.isEmpty()) {
            return new ResumenFiscalVentas(
                    0, 0, 0,
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }

        int operaciones = registros.size();
        int emitidas = 0;
        int anuladas = 0;

        BigDecimal exentas = BigDecimal.ZERO;
        BigDecimal baseGen = BigDecimal.ZERO;
        BigDecimal debitoGen = BigDecimal.ZERO;
        BigDecimal baseRed = BigDecimal.ZERO;
        BigDecimal debitoRed = BigDecimal.ZERO;
        BigDecimal baseAdic = BigDecimal.ZERO;
        BigDecimal debitoAdic = BigDecimal.ZERO;
        BigDecimal totalVentas = BigDecimal.ZERO;
        BigDecimal retenido = BigDecimal.ZERO;
        BigDecimal igtf = BigDecimal.ZERO;

        for (RegistroLibroVenta reg : registros) {
            if (reg.esAnulada()) {
                anuladas++;
            } else {
                emitidas++;
            }
            exentas = exentas.add(reg.getVentasExentasBs());
            baseGen = baseGen.add(reg.getBaseImponibleBs());
            debitoGen = debitoGen.add(reg.getIvaBs());
            baseRed = baseRed.add(reg.getBaseImponibleReducidaBs());
            debitoRed = debitoRed.add(reg.getIvaReducidoBs());
            baseAdic = baseAdic.add(reg.getBaseImponibleAdicionalBs());
            debitoAdic = debitoAdic.add(reg.getIvaAdicionalBs());
            totalVentas = totalVentas.add(reg.getTotalVentasBs());
            retenido = retenido.add(reg.getIvaRetenidoBs());
            igtf = igtf.add(reg.getIgtfPercibidoBs());
        }

        return new ResumenFiscalVentas(
                operaciones,
                emitidas,
                anuladas,
                exentas.setScale(2, RoundingMode.HALF_UP),
                baseGen.setScale(2, RoundingMode.HALF_UP),
                debitoGen.setScale(2, RoundingMode.HALF_UP),
                baseRed.setScale(2, RoundingMode.HALF_UP),
                debitoRed.setScale(2, RoundingMode.HALF_UP),
                baseAdic.setScale(2, RoundingMode.HALF_UP),
                debitoAdic.setScale(2, RoundingMode.HALF_UP),
                totalVentas.setScale(2, RoundingMode.HALF_UP),
                retenido.setScale(2, RoundingMode.HALF_UP),
                igtf.setScale(2, RoundingMode.HALF_UP));
    }
}

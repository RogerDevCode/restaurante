package diferencial;

import Modelo.DetallePedido;
import Servicio.CalculoMonetarioPedido;
import Servicio.CalculoMonetarioPedido.CalculoPedido;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Arnés de Prueba Diferencial Contable y Fiscal.
 * Genera con semilla pseudoaleatoria fija (20261017L) un corpus representativo de 100 pedidos
 * con combinatoria exhaustiva (líneas gravadas/exentas, VES/USD, IVA incluido/excluido, de 1 a 20 líneas, diversas tasas).
 *
 * Registra y valida contra el archivo golden de la línea base ('golden_corpus_calculo.tsv').
 * Aplica la lista blanca estricta autorizada únicamente para N-01 (exento USD en IVA Bs) y N-05 (D8 aditividad USD).
 */
public class PruebaDiferencialCalculoTest {

    public static final long SEED = 20261017L;
    public static final int NUM_PEDIDOS = 100;
    public static final Path GOLDEN_PATH = Paths.get("test/diferencial/golden_corpus_calculo.tsv");

    public static final BigDecimal[] TASAS = new BigDecimal[] {
            new BigDecimal("36.5000"),
            new BigDecimal("45.1500"),
            new BigDecimal("45.2500"),
            new BigDecimal("50.0000"),
            new BigDecimal("1014.0000")
    };

    /** Genera el corpus determinista de pedidos. */
    public static List<CorpusPedido> generarCorpus() {
        Random rnd = new Random(SEED);
        List<CorpusPedido> corpus = new ArrayList<>(NUM_PEDIDOS);

        for (int p = 1; p <= NUM_PEDIDOS; p++) {
            BigDecimal tasa = TASAS[rnd.nextInt(TASAS.length)];
            int numLineas = 1 + rnd.nextInt(20); // 1 a 20 líneas
            List<DetallePedido> detalles = new ArrayList<>(numLineas);

            for (int l = 1; l <= numLineas; l++) {
                DetallePedido d = new DetallePedido();
                d.setId(l);
                d.setNombre("Item-" + p + "-" + l);
                int cantidad = 1 + rnd.nextInt(10);
                d.setCantidad(cantidad);

                // Moneda: 50% USD, 50% VES
                String moneda = rnd.nextBoolean() ? "USD" : "VES";
                d.setMonedaBase(moneda);

                // Precio: entre 0.50 y 150.00
                double precioDbl = 0.50 + (rnd.nextDouble() * 149.50);
                BigDecimal precio = BigDecimal.valueOf(precioDbl).setScale(2, RoundingMode.HALF_UP);
                d.setPrecioDecimal(precio);

                // Aplica IVA: 70% gravado, 30% exento
                boolean gravado = rnd.nextInt(100) < 70;
                d.setAplicaIva(gravado);

                if (gravado) {
                    boolean incIva = rnd.nextBoolean();
                    d.setPrecioIncluyeIva(incIva);
                    d.setIvaPorcentaje(new BigDecimal("16.00"));
                } else {
                    d.setPrecioIncluyeIva(false);
                    d.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));
                }

                detalles.add(d);
            }

            corpus.add(new CorpusPedido(p, tasa, detalles));
        }

        return corpus;
    }

    public record CorpusPedido(int id, BigDecimal tasa, List<DetallePedido> detalles) { }

    /** Genera o regenera el golden TSV de la línea base si no existe. */
    public static void asegurarGoldenLineBase() throws IOException {
        if (Files.exists(GOLDEN_PATH)) {
            return;
        }
        Files.createDirectories(GOLDEN_PATH.getParent());
        List<CorpusPedido> corpus = generarCorpus();
        try (BufferedWriter bw = Files.newBufferedWriter(GOLDEN_PATH)) {
            bw.write("id\ttasa\tcant_lineas\thas_exento_usd\thas_ves\tsubtotal_usd\tiva_usd\ttotal_usd\tsubtotal_ves\tiva_ves\ttotal_ves\n");
            for (CorpusPedido p : corpus) {
                CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(p.detalles(), p.tasa());
                boolean hasExentoUsd = p.detalles().stream().anyMatch(d -> !d.getAplicaIva() && "USD".equalsIgnoreCase(d.getMonedaBase()));
                boolean hasVes = p.detalles().stream().anyMatch(d -> "VES".equalsIgnoreCase(d.getMonedaBase()));

                bw.write(String.format(java.util.Locale.US,
                        "%d\t%s\t%d\t%b\t%b\t%s\t%s\t%s\t%s\t%s\t%s\n",
                        p.id(),
                        p.tasa().toPlainString(),
                        p.detalles().size(),
                        hasExentoUsd,
                        hasVes,
                        calc.subtotalUsd().toPlainString(),
                        calc.ivaUsd().toPlainString(),
                        calc.totalUsd().toPlainString(),
                        calc.subtotalVes().toPlainString(),
                        calc.ivaVes().toPlainString(),
                        calc.totalVes().toPlainString()
                ));
            }
        }
    }

    /**
     * Prueba de verificación diferencial:
     * Ejecuta el cálculo contra el corpus actual y lo compara con el golden base.
     * Solo tolera desviaciones pertenecientes a la lista blanca de N-01 y N-05.
     */
    @Test
    public void testDiferencialContraLineaBase() throws Exception {
        asegurarGoldenLineBase();
        List<CorpusPedido> corpus = generarCorpus();

        List<String> lineasGolden = Files.readAllLines(GOLDEN_PATH);
        assertEquals("El corpus y el archivo golden deben tener el mismo tamaño (+ header)",
                corpus.size() + 1, lineasGolden.size());

        int totalVerificados = 0;
        int diferenciasN01 = 0;
        int diferenciasN05 = 0;

        for (int i = 0; i < corpus.size(); i++) {
            CorpusPedido pedido = corpus.get(i);
            CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(pedido.detalles(), pedido.tasa());

            String[] cols = lineasGolden.get(i + 1).split("\t");
            int idGolden = Integer.parseInt(cols[0]);
            assertEquals(pedido.id(), idGolden);

            boolean hasExentoUsd = Boolean.parseBoolean(cols[3]);
            boolean hasVes = Boolean.parseBoolean(cols[4]);

            BigDecimal goldSubUsd = new BigDecimal(cols[5]);
            BigDecimal goldIvaUsd = new BigDecimal(cols[6]);
            BigDecimal goldTotUsd = new BigDecimal(cols[7]);
            BigDecimal goldSubVes = new BigDecimal(cols[8]);
            BigDecimal goldIvaVes = new BigDecimal(cols[9]);
            BigDecimal goldTotVes = new BigDecimal(cols[10]);

            // En la línea base sin cambios, todo coincide exactamente con el golden
            assertEquals("Pedido #" + pedido.id() + " subtotal_ves alterado sin autorización",
                    goldSubVes, calc.subtotalVes());
            assertEquals("Pedido #" + pedido.id() + " total_ves alterado sin autorización",
                    goldTotVes, calc.totalVes());

            if (calc.ivaVes().compareTo(goldIvaVes) != 0) {
                // Solo permitido si tiene exento USD (N-01)
                assertTrue("Diferencia en iva_ves en pedido #" + pedido.id() + " sin líneas exentas USD",
                        hasExentoUsd);
                diferenciasN01++;
            }

            if (calc.subtotalUsd().compareTo(goldSubUsd) != 0 || calc.ivaUsd().compareTo(goldIvaUsd) != 0 || calc.totalUsd().compareTo(goldTotUsd) != 0) {
                // Solo permitido bajo D8 para platos con componentes en VES (N-05)
                assertTrue("Diferencia en importes USD en pedido #" + pedido.id() + " sin líneas en VES",
                        hasVes);
                diferenciasN05++;
            }

            totalVerificados++;
        }

        System.out.println("Verificación diferencial completada: " + totalVerificados + " pedidos evaluados.");
        System.out.println("Diferencias N-01 (exento USD en IVA Bs): " + diferenciasN01);
        System.out.println("Diferencias N-05 (D8 aditividad USD en VES): " + diferenciasN05);
    }
}

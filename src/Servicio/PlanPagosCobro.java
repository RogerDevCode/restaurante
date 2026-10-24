package Servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Plan de pagos de un cobro bi-moneda (USD base, Bs derivado). Sin dependencias de Swing,
 * para que la regla de dinero se pueda probar sin abrir ninguna ventana.
 *
 * <p>Modelo: el pedido tiene un total en USD y uno en Bs (ambos oficiales, ya calculados).
 * El cajero va agregando <b>líneas de pago</b>; cada una cubre una parte de la cuenta en la
 * moneda de su método. El saldo pendiente se muestra siempre en ambas monedas y el cobro
 * se puede finalizar cuando el saldo queda en cero.</p>
 *
 * <p>Reglas de conciliación (equivalentes a las exigidas por {@code PedidosDao}):</p>
 * <ul>
 *   <li>Una línea en USD cubre {@code aplicado × tasa} Bs (redondeado a 2 decimales por línea).</li>
 *   <li>Una línea en Bs cubre {@code aplicado} Bs y {@code aplicado ÷ tasa} USD.</li>
 *   <li>El saldo Bs se calcula contra {@code totalBs} oficial. Si todos los pagos son en Bs,
 *       la cuenta solo se considera cubierta si {@code pendienteBs == 0} exacto (evita el falso
 *       positivo de redondeo a 0.00 en USD que rechaza la base de datos).</li>
 *   <li>Con pagos en USD presentes, la cuenta se considera cubierta cuando el saldo USD o
 *       el saldo Bs llegan a cero (dentro de la tolerancia cambiaria legal).</li>
 * </ul>
 */
public final class PlanPagosCobro {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /** Métodos de pago. {@link #codigo()} coincide con los códigos históricos de {@code PagoPedido}. */
    public enum Metodo {
        EFECTIVO_USD("USD", true, "Efectivo $"),
        EFECTIVO_BS("VES", true, "Efectivo Bs"),
        TARJETA("VES", false, "Punto / Tarjeta"),
        PAGO_MOVIL("VES", false, "Pago Móvil"),
        TRANSFERENCIA("VES", false, "Transferencia"),
        CORTESIA("VES", false, "Cortesía");

        private final String moneda;
        private final boolean efectivo;
        private final String nombreCorto;

        Metodo(String moneda, boolean efectivo, String nombreCorto) {
            this.moneda = moneda;
            this.efectivo = efectivo;
            this.nombreCorto = nombreCorto;
        }

        public String codigo() { return name(); }
        public String moneda() { return moneda; }
        public boolean esEfectivo() { return efectivo; }
        public boolean enUsd() { return "USD".equals(moneda); }
        public String nombreCorto() { return nombreCorto; }
    }

    /** Resultado de intentar aplicar un pago. */
    public enum Estado { APLICADO, AJUSTADO_AL_SALDO, YA_CUBIERTO, SIN_TASA, SIN_TOTAL }

    /**
     * Una línea de pago. Todos los montos están en la moneda del método.
     *
     * @param aplicado  parte de la cuenta que cubre esta línea
     * @param recibido  lo que entregó el cliente (== aplicado salvo efectivo con vuelto)
     * @param vuelto    {@code recibido - aplicado} (solo efectivo)
     * @param automatica línea precargada por defecto; la primera acción manual la reemplaza
     */
    public record Linea(Metodo metodo, BigDecimal aplicado, BigDecimal recibido, BigDecimal vuelto, boolean automatica) {
        public boolean enUsd() { return metodo.enUsd(); }
    }

    public record Saldo(BigDecimal usd, BigDecimal bs, boolean cubierto) { }

    public record Resultado(Estado estado, Linea linea) {
        public boolean aplicado() {
            return estado == Estado.APLICADO || estado == Estado.AJUSTADO_AL_SALDO;
        }
    }

    private final BigDecimal totalUsd;
    private final BigDecimal totalBs;
    private final BigDecimal tasa;
    private final boolean tasaValida;
    private final List<Linea> lineas = new ArrayList<>();
    private String motivoCortesia;

    /**
     * @param tasaValida si es {@code false} solo se permite Efectivo USD (los totales en Bs no son confiables)
     */
    public PlanPagosCobro(BigDecimal totalUsd, BigDecimal totalBs, BigDecimal tasa, boolean tasaValida) {
        this.totalUsd = escala2(Objects.requireNonNull(totalUsd, "totalUsd"));
        this.totalBs = escala2(Objects.requireNonNull(totalBs, "totalBs"));
        this.tasa = Objects.requireNonNull(tasa, "tasa");
        this.tasaValida = tasaValida;
        if (tasa.signum() <= 0) {
            throw new IllegalArgumentException("La tasa debe ser positiva");
        }
    }

    // ------------------------------------------------------------------ consulta

    public List<Linea> lineas() { return List.copyOf(lineas); }

    public String motivoCortesia() { return motivoCortesia; }

    /** Saldo considerando todas las líneas (incluida la automática). Es lo que decide si se puede cobrar. */
    public Saldo saldo() { return saldoDe(lineas); }

    public boolean cubierto() { return saldo().cubierto(); }

    /** Saldo ignorando la línea automática: es contra lo que opera el cajero al elegir método o billete. */
    public Saldo saldoDisponible() { return saldoDe(manuales()); }

    public BigDecimal vueltoUsd() { return sumarVuelto(true); }

    public BigDecimal vueltoBs() { return sumarVuelto(false); }

    /**
     * Billetes sugeridos para pagar el saldo USD en efectivo: los 4 montos más bajos, mayores que el
     * saldo, que resultan de redondear hacia arriba al dólar y a múltiplos de 5, 10, 20, 50 y 100.
     * (Para $13.50: 14, 15, 20, 50.) El monto exacto lo cubre el botón "Exacto".
     */
    public List<BigDecimal> sugerirBilletesUsd() {
        Saldo s = saldoDisponible();
        if (s.cubierto() || s.usd().signum() <= 0) {
            return List.of();
        }
        BigDecimal pendiente = s.usd();
        TreeSet<BigDecimal> candidatos = new TreeSet<>();
        candidatos.add(pendiente.setScale(0, RoundingMode.CEILING));
        for (int paso : new int[]{5, 10, 20, 50, 100}) {
            BigDecimal p = BigDecimal.valueOf(paso);
            candidatos.add(pendiente.divide(p, 0, RoundingMode.CEILING).multiply(p));
        }
        List<BigDecimal> res = new ArrayList<>();
        for (BigDecimal c : candidatos) {
            if (c.compareTo(pendiente) > 0 && res.size() < 4) {
                res.add(c);
            }
        }
        return res;
    }

    // ------------------------------------------------------------------ acciones

    /** Precarga Efectivo USD por el total (camino de 0 clics). Solo si no hay líneas. */
    public void precargarEfectivoUsd() {
        if (lineas.isEmpty()) {
            lineas.add(new Linea(Metodo.EFECTIVO_USD, totalUsd, totalUsd, CERO, true));
        }
    }

    /**
     * Aplica un pago por el saldo restante (si {@code ingresado} es null) o por el monto ingresado,
     * interpretado en la moneda del método. Si el cliente entrega más que el saldo: en efectivo se
     * genera vuelto; en métodos electrónicos el monto se recorta al saldo.
     *
     * @throws IllegalArgumentException si {@code ingresado} no es positivo, o si se pide CORTESIA
     */
    public Resultado aplicar(Metodo metodo, BigDecimal ingresado) {
        Objects.requireNonNull(metodo, "metodo");
        if (metodo == Metodo.CORTESIA) {
            throw new IllegalArgumentException("La cortesía se aplica con aplicarCortesia(motivo)");
        }
        if (!tasaValida && metodo != Metodo.EFECTIVO_USD) {
            return new Resultado(Estado.SIN_TASA, null);
        }
        if (totalUsd.signum() <= 0) {
            return new Resultado(Estado.SIN_TOTAL, null);
        }

        Saldo s = saldoDisponible();
        if (s.cubierto()) {
            return new Resultado(Estado.YA_CUBIERTO, null);
        }

        BigDecimal pendiente = metodo.enUsd() ? s.usd() : s.bs();
        BigDecimal recibido = ingresado != null ? escala2(ingresado) : pendiente;
        if (recibido.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser positivo");
        }

        boolean ajustado = !metodo.esEfectivo() && recibido.compareTo(pendiente) > 0;
        BigDecimal aplicado = recibido.min(pendiente);
        BigDecimal vuelto = metodo.esEfectivo() ? recibido.subtract(aplicado) : CERO;
        if (!metodo.esEfectivo()) {
            recibido = aplicado;
        }

        if (hayUnicaLineaAutomatica() || hayCortesia()) {
            lineas.clear();
            motivoCortesia = null;
        }
        Linea linea = new Linea(metodo, aplicado, recibido, vuelto, false);
        lineas.add(linea);
        return new Resultado(ajustado ? Estado.AJUSTADO_AL_SALDO : Estado.APLICADO, linea);
    }

    /** Cortesía: exclusiva. Reemplaza cualquier otro pago y cubre la cuenta completa. */
    public void aplicarCortesia(String motivo) {
        if (!tasaValida) {
            throw new IllegalStateException("No hay tasa de cambio válida: la cortesía se registra en Bs");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("La cortesía requiere un motivo");
        }
        lineas.clear();
        lineas.add(new Linea(Metodo.CORTESIA, totalBs, totalBs, CERO, false));
        motivoCortesia = motivo.trim();
    }

    /** Quita la línea en la posición indicada. Devuelve la línea quitada o null si el índice no existe. */
    public Linea quitar(int indice) {
        if (indice < 0 || indice >= lineas.size()) {
            return null;
        }
        Linea quitada = lineas.remove(indice);
        if (quitada.metodo() == Metodo.CORTESIA) {
            motivoCortesia = null;
        }
        return quitada;
    }

    // ------------------------------------------------------------------ utilidades

    /**
     * Interpreta un monto escrito por el cajero. Acepta coma o punto decimal y separadores de miles
     * ("1.234,56", "1,234.56", "20,5"). Devuelve {@code null} si el texto está vacío.
     *
     * @throws NumberFormatException si no es un número o no es positivo
     */
    public static BigDecimal parsearMonto(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim().replace(" ", "");
        if (t.isEmpty()) {
            return null;
        }
        int coma = t.lastIndexOf(',');
        int punto = t.lastIndexOf('.');
        if (coma >= 0 && punto >= 0) {
            // El último separador es el decimal; el otro es de miles.
            t = coma > punto ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else if (coma >= 0) {
            t = coma != t.indexOf(',') ? t.replace(",", "") : t.replace(',', '.');
        } else if (punto != t.indexOf('.')) {
            t = t.replace(".", "");
        }
        BigDecimal v = new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        if (v.signum() <= 0) {
            throw new NumberFormatException("El monto debe ser positivo");
        }
        return v;
    }

    /** Líneas que el cajero agregó y que cuentan para el saldo "real" (ni la automática ni la cortesía). */
    private List<Linea> manuales() {
        List<Linea> res = new ArrayList<>();
        for (Linea l : lineas) {
            if (!l.automatica() && l.metodo() != Metodo.CORTESIA) {
                res.add(l);
            }
        }
        return res;
    }

    private boolean hayCortesia() {
        for (Linea l : lineas) {
            if (l.metodo() == Metodo.CORTESIA) {
                return true;
            }
        }
        return false;
    }

    private boolean hayUnicaLineaAutomatica() {
        return lineas.size() == 1 && lineas.get(0).automatica();
    }

    private BigDecimal sumarVuelto(boolean enUsd) {
        BigDecimal suma = CERO;
        for (Linea l : lineas) {
            if (l.enUsd() == enUsd) {
                suma = suma.add(l.vuelto());
            }
        }
        return suma;
    }

    private Saldo saldoDe(List<Linea> ls) {
        for (Linea l : ls) {
            if (l.metodo() == Metodo.CORTESIA) {
                return new Saldo(CERO, CERO, true);
            }
        }
        BigDecimal cubiertoUsd = BigDecimal.ZERO;
        BigDecimal cubiertoBs = BigDecimal.ZERO;
        boolean tieneUsd = false;
        for (Linea l : ls) {
            if (l.enUsd()) {
                tieneUsd = true;
                cubiertoUsd = cubiertoUsd.add(l.aplicado());
                cubiertoBs = cubiertoBs.add(l.aplicado().multiply(tasa).setScale(2, RoundingMode.HALF_UP));
            } else {
                cubiertoBs = cubiertoBs.add(l.aplicado());
                cubiertoUsd = cubiertoUsd.add(l.aplicado().divide(tasa, 6, RoundingMode.HALF_UP));
            }
        }
        BigDecimal pendienteUsd = noNegativo(totalUsd.subtract(cubiertoUsd));
        BigDecimal pendienteBs = tasaValida ? noNegativo(totalBs.subtract(cubiertoBs)) : CERO;

        boolean cubierto;
        if (!tieneUsd) {
            // Pagos 100% en Bs: la cobertura DEBE ser estricta contra totalBs (exigido por PedidosDao ajusteCambioBs == 0)
            cubierto = !ls.isEmpty() && tasaValida && pendienteBs.signum() == 0;
        } else {
            // Con pagos en USD: cubierto si pendienteUsd llega a 0 o pendienteBs llega a 0
            cubierto = !ls.isEmpty() && (pendienteUsd.signum() == 0 || (tasaValida && pendienteBs.signum() == 0));
        }
        return cubierto ? new Saldo(CERO, CERO, true) : new Saldo(pendienteUsd, pendienteBs, false);
    }

    private static BigDecimal noNegativo(BigDecimal x) {
        BigDecimal r = x.setScale(2, RoundingMode.HALF_UP);
        return r.signum() < 0 ? CERO : r;
    }

    private static BigDecimal escala2(BigDecimal x) {
        return x.setScale(2, RoundingMode.HALF_UP);
    }
}

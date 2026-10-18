package Vista;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Datos de prueba puros; la creación y lectura de fixtures ocurre en la UI. */
final class DiaE2EFixture {
    static final String DB_URL_ESPERADO =
            "jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    static final String DB_USER_ESPERADO = "restaurante_test_app";
    static final String DB_PASSWORD_ESPERADO =
            System.getenv().getOrDefault("DB_PASSWORD", "test_only_app_password");
    static final BigDecimal TASA_INICIAL = new BigDecimal("40.0000");
    static final BigDecimal TASA_NUEVA = new BigDecimal("42.0000");
    static final BigDecimal IVA_INICIAL = new BigDecimal("16.00");
    static final BigDecimal IVA_NUEVO = new BigDecimal("10.00");
    static final String[] CLAVES = {"EX_USD", "EX_VES", "MAS_USD", "MAS_VES", "INC_USD", "INC_VES"};

    record ProductoFiscal(String clave, String nombre, String moneda,
            BigDecimal precio, boolean aplicaIva, boolean incluyeIva) {}

    record Fixture(String sufijo, String nombreSala, Map<String, ProductoFiscal> productos) {}

    private DiaE2EFixture() {}

    static void exigirBdAislada() {
        String display = System.getenv("DISPLAY");
        if (!":99".equals(display)) {
            throw new IllegalStateException("E2E rechazado: debe ejecutarse en Xvfb DISPLAY=:99.");
        }
        if (!"SI".equals(System.getenv("E2E_BD_DESECHABLE"))) {
            throw new IllegalStateException("E2E rechazado: exige E2E_BD_DESECHABLE=SI.");
        }
        if (System.getenv("E2E_WORKDIR") == null
                || System.getenv("E2E_WORKDIR").isBlank()) {
            throw new IllegalStateException("E2E bloqueado: debe iniciarse con e2e_dia_completo_virtual.sh.");
        }
        exigirPropiedad("DB_URL", DB_URL_ESPERADO);
        exigirPropiedad("DB_USER", DB_USER_ESPERADO);
        exigirPropiedad("DB_PASSWORD", DB_PASSWORD_ESPERADO);
    }

    static Fixture nuevo() {
        exigirBdAislada();
        String sufijo = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase(java.util.Locale.ROOT);
        Map<String, ProductoFiscal> productos = new LinkedHashMap<>();
        agregar(productos, sufijo, "EX_USD", "USD", "3.00", false, false);
        agregar(productos, sufijo, "EX_VES", "VES", "120.00", false, false);
        agregar(productos, sufijo, "MAS_USD", "USD", "10.00", true, false);
        agregar(productos, sufijo, "MAS_VES", "VES", "400.00", true, false);
        agregar(productos, sufijo, "INC_USD", "USD", "11.60", true, true);
        agregar(productos, sufijo, "INC_VES", "VES", "464.00", true, true);
        return new Fixture(sufijo, "E2ES" + sufijo, Map.copyOf(productos));
    }

    private static void agregar(Map<String, ProductoFiscal> productos, String sufijo,
            String clave, String moneda, String precio, boolean aplicaIva, boolean incluyeIva) {
        productos.put(clave, new ProductoFiscal(clave, "DIAE2E" + clave + sufijo,
                moneda, new BigDecimal(precio), aplicaIva, incluyeIva));
    }

    private static void exigirPropiedad(String nombre, String esperado) {
        String actual = System.getProperty(nombre);
        if (!esperado.equals(actual)) {
            throw new IllegalStateException("E2E bloqueado: " + nombre
                    + " debe ser explícito y coincidir con la BD temporal restaurante_test.");
        }
    }
}

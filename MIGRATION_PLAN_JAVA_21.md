# PLAN DE MIGRACIÓN ARQUITECTÓNICA A JAVA 21 LTS
## Sistema de Gestión de Restaurante 2026 (Producción)

**Versión:** 2.0.0-PROD-MIGRATION  
**Fecha:** Octubre 2026  
**Estado:** REVISADO Y APROBADO TRAS AUDITORÍA ADVERSARIAL  
**Auditor:** Agente Adversarial de Arquitectura Java y Concurrencia  

---

## 1. RESUMEN EJECUTIVO Y OBJETIVOS

Este plan define la hoja de ruta técnica paso a paso para migrar el Sistema de Restaurante a **Java 21 LTS**, aprovechando todas sus capacidades modernas:
1. **Virtual Threads (JEP 444):** Para I/O no bloqueante (consultas MySQL y generación de PDFs con iText).
2. **Records (JEP 395) & Record Patterns (JEP 440):** DTOs inmutables de cálculo bimonetario y datos fiscales con desestructuración directa.
3. **Sealed Classes & Interfaces (JEP 409):** Modelado algebraico cerrado de resultados de operación (`ResultadoOperacion`).
4. **Pattern Matching for `switch` & `instanceof` (JEP 441, 394):** Control de acceso en `PoliticaAcceso`, enrutamiento de errores en `ManejadorErroresSwing` y validación de tipos sin cast manual.
5. **Text Blocks (JEP 378):** Consultas SQL multilínea legibles y formateadas en todos los DAOs.
6. **Sequenced Collections (JEP 431):** Navegación ordenada segura (`getFirst()`, `getLast()`, `reversed()`) en ventas y pedidos.
7. **Generational ZGC (JEP 439):** Recolección de basura con pausas `< 1 ms` para máxima fluidez en Swing.

---

## 2. AUDITORÍA ADVERSARIAL: VULNERABILIDADES IDENTIFICADAS Y MITIGACIONES OBLIGATORIAS

| ID | Riesgo / Vulnerabilidad Crítica | Impacto Potencial | Mitigación Arquitectónica Mandatoria |
| :--- | :--- | :--- | :--- |
| **ADV-01** | **Carrier Thread Pinning en MySQL Connector/J 8.0.31** | Bytecode contiene 39 bloques `monitorenter` (`synchronized`). Cargas masivas concurrentes fijan el hilo virtual al carrier thread del SO saturando el pool. | 1. Configurar bandera JVM `-Djdk.tracePinnedThreads=full` para diagnóstico.<br>2. Delimitar la concurrencia de I/O de BD con un semáforo o pool de conexiones acotado.<br>3. Eliminar librerías huérfanas en `librerias/`. |
| **ADV-02** | **Violación del EDT de Swing y Excepciones Ocultas** | Lanzar `CompletableFuture` en hilos virtuales sin capturar excepciones puede ocultar errores y congelar la UI sin notificar a `ManejadorErroresSwing`. | Crear un wrapper `VirtualSwingTask` o mantener `SwingWorker` delegando la computación pesada al hilo virtual y garantizando el callback a la UI mediante `SwingUtilities.invokeLater`. |
| **ADV-03** | **`NoSuchElementException` en Sequenced Collections** | Llamadas ciegas a `.getLast()` o `.getFirst()` sobre listas vacías tiran excepciones en tiempo de ejecución. | Exigir guardas explícitas `if (!lista.isEmpty())` o helpers de colección defensivos (`CollectionUtils.lastOptional(lista)`). |
| **ADV-04** | **Inmutabilidad en `Stream.toList()` vs `DefaultTableModel`** | `Stream.toList()` produce listas inmutables (`ListN`). Intentar modificar o agregar elementos en caliente lanza `UnsupportedOperationException`. | Donde la UI Swing requiera mutabilidad (ej. agregar filas a tablas), crear una copia explícita `new ArrayList<>(lista.stream().toList())`. |
| **ADV-05** | **Frontera de Inmutabilidad: Records vs JavaBeans** | Las entidades `Pedidos`, `Platos`, `Salas`, `Usuario`, `Config`, `DetallePedido` son JavaBeans mutables vinculados a formularios Swing y tests existentes. | **Regla Estricta:** Las entidades de dominio se mantienen como JavaBeans. Los `record` se reservan **exclusivamente** para DTOs (`CotizacionMonedaRecord`, `DatosTicketFiscal`), cálculos y tipos cerrados. |

---

## 3. PLAN DE MIGRACIÓN POR FASES TÉCNICAS

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                               FLUJO DE MIGRACIÓN JAVA 21                         │
├───────────────────────────────────────────────────────────────────────────────────┤
│  FASE 0: Higiene de Librerías y Configuración de Toolchain (javac --release 21)   │
│       │                                                                           │
│  FASE 1: Infraestructura de Concurrencia con Virtual Threads y EDT Guard          │
│       │                                                                           │
│  FASE 2: DTOs Inmutables con Records y Tipos Cerrados (Sealed Interfaces)         │
│       │                                                                           │
│  FASE 3: Pattern Matching en Switch y Control de Errores                          │
│       │                                                                           │
│  FASE 4: Text Blocks en Capa de Persistencia (DAOs)                              │
│       │                                                                           │
│  FASE 5: Sequenced Collections y Stream API Defensivo                             │
│       │                                                                           │
│  FASE 6: Verificación de Regresión (111 tests) + Nuevas Pruebas Java 21           │
│       │                                                                           │
│  FASE 7: Actualización de Scripts (.bat), Documentación y Despliegue              │
└───────────────────────────────────────────────────────────────────────────────────┘
```

---

### FASE 0: Higiene de Dependencias y Toolchain de Compilación

1. **Limpieza de JARs Huérfanos:**
   - Eliminar el archivo obsoleto `librerias/mysql-connector-java-8.0.19.jar` para evitar duplicidad de paquetes de driver JDBC.
   - Mantener `librerias/mysql-connector-j-8.0.31.jar`, `librerias/itextpdf-5.5.1.jar`, `librerias/AbsoluteLayout.jar`, `librerias/junit-4.13.2.jar`, `librerias/hamcrest-core-1.3.jar`.

2. **Ajuste en `nbproject/project.properties`:**
   ```properties
   javac.source=21
   javac.target=21
   javac.compilerargs=--release 21 -Xlint:all,-serial
   ```

3. **Configuración de Flags de JVM en Runtime (`iniciar_restaurante.bat`):**
   ```bat
   java -XX:+UseZGC -XX:+ZGenerational -Djdk.tracePinnedThreads=full -jar dist/Restaurante.jar
   ```

---

### FASE 1: Concurrencia con Virtual Threads (JEP 444) y Seguridad en Swing EDT

* **Archivo Nuevo:** `src/Servicio/ConcurrenciaServicio.java`
  ```java
  package Servicio;

  import java.util.concurrent.ExecutorService;
  import java.util.concurrent.Executors;
  import java.util.concurrent.Future;
  import java.util.function.Consumer;
  import java.util.function.Supplier;
  import javax.swing.SwingUtilities;

  /**
   * Gestor de concurrencia basado en Virtual Threads de Java 21
   * garantizando el retorno seguro al Swing Event Dispatch Thread (EDT).
   */
  public final class ConcurrenciaServicio {
      private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

      private ConcurrenciaServicio() {}

      public static <T> Future<?> ejecutarAsync(Supplier<T> backgroundTask,
                                               Consumer<T> onExitoEnEdt,
                                               Consumer<Throwable> onErrorEnEdt) {
          return VIRTUAL_EXECUTOR.submit(() -> {
              try {
                  T resultado = backgroundTask.get();
                  if (onExitoEnEdt != null) {
                      SwingUtilities.invokeLater(() -> onExitoEnEdt.accept(resultado));
                  }
              } catch (Throwable ex) {
                  if (onErrorEnEdt != null) {
                      SwingUtilities.invokeLater(() -> onErrorEnEdt.accept(ex));
                  }
              }
          });
      }
  }
  ```

* **Aplicación en Generación de PDFs y Tareas de Base de Datos:**
  - `src/Servicio/PedidoPdfServicio.java`: La exportación de PDFs se ejecuta en un hilo virtual independiente, evitando bloquear la interfaz gráfica y sin consumo excesivo de memoria del SO.

---

### FASE 2: DTOs Inmutables con Records (JEP 395) y Jerarquías Selladas (JEP 409)

* **1. DTO Inmutable para Cotización Bimonetaria:**
  `src/Modelo/CotizacionMonedaRecord.java`
  ```java
  package Modelo;

  import java.math.BigDecimal;
  import java.util.Objects;

  public record CotizacionMonedaRecord(BigDecimal totalUsd, BigDecimal totalBs, BigDecimal tasaCambio) {
      public CotizacionMonedaRecord {
          Objects.requireNonNull(totalUsd, "El monto en USD no puede ser nulo");
          Objects.requireNonNull(totalBs, "El monto en Bs no puede ser nulo");
          Objects.requireNonNull(tasaCambio, "La tasa de cambio no puede ser nula");
          if (totalUsd.compareTo(BigDecimal.ZERO) < 0 || totalBs.compareTo(BigDecimal.ZERO) < 0 || tasaCambio.compareTo(BigDecimal.ZERO) <= 0) {
              throw ErrorAplicacionException.validacion("Montos y tasas deben ser positivos.");
          }
      }
  }
  ```

* **2. DTO Inmutable para Datos Fiscales de Ticket:**
  `src/Modelo/DatosTicketFiscal.java`
  ```java
  package Modelo;

  public record DatosTicketFiscal(String ruc, String nombre, String telefono, String direccion, String mensaje) {}
  ```

* **3. Jerarquía Sellada de Resultados de Operación (`sealed interface`):**
  `src/Servicio/ResultadoOperacion.java`
  ```java
  package Servicio;

  public sealed interface ResultadoOperacion<T> permits ResultadoOperacion.Exito, ResultadoOperacion.Fallo {
      record Exito<T>(T datos, String mensaje) implements ResultadoOperacion<T> {}
      record Fallo<T>(String mensajeError, Throwable causa) implements ResultadoOperacion<T> {}
  }
  ```

---

### FASE 3: Pattern Matching y Switch Expressions (JEP 441, 394, 440)

* **1. Refactorización en `src/Servicio/PoliticaAcceso.java`:**
  ```java
  public boolean permite(Accion accion) {
      if (accion == null) {
          throw ErrorAplicacionException.validacion("La acción solicitada es obligatoria.");
      }
      return switch (rol) {
          case "Administrador" -> true;
          case "Asistente" -> switch (accion) {
              case CONSULTAR_SALAS, CONSULTAR_PLATOS, REGISTRAR_PEDIDOS -> true;
              case GESTIONAR_PEDIDOS, GESTIONAR_SALAS, GESTIONAR_PLATOS, GESTIONAR_USUARIOS, EDITAR_CONFIGURACION -> false;
          };
          default -> false;
      };
  }
  ```

* **2. Refactorización en `src/Vista/ManejadorErroresSwing.java` con Pattern Matching:**
  ```java
  public static void manejarError(Throwable error, Component parent) {
      if (error instanceof ErrorAplicacionException appEx) {
          JOptionPane.showMessageDialog(parent, appEx.getMessage(), "Aviso de Validación", JOptionPane.WARNING_MESSAGE);
      } else if (error instanceof DataAccessException daEx) {
          JOptionPane.showMessageDialog(parent, "Error de Persistencia: " + daEx.getMessage(), "Error BD", JOptionPane.ERROR_MESSAGE);
      } else {
          JOptionPane.showMessageDialog(parent, "Ocurrió un error inesperado en el sistema.", "Error", JOptionPane.ERROR_MESSAGE);
      }
  }
  ```

* **3. Desestructuración de Records en Controladores:**
  ```java
  switch (resultado) {
      case ResultadoOperacion.Exito(var datos, var msg) -> {
          lblNotificacion.setText(msg);
          actualizarVista(datos);
      }
      case ResultadoOperacion.Fallo(var errMsg, var causa) -> {
          ManejadorErroresSwing.manejarError(causa != null ? causa : new ErrorAplicacionException(errMsg), frame);
      }
  }
  ```

---

### FASE 4: Capa de Persistencia con Text Blocks (JEP 378)

* **Migración en DAOs (`PedidosDao.java`, `PlatosDao.java`, `SalasDao.java`, `ConfiguracionDao.java`, `UsuariosDao.java`):**
  - Eliminar concatenaciones manuales `"SELECT ... " + "FROM ..."`.
  - Usar sintaxis `"""` limpia con sangrado natural:
  ```java
  // En PedidosDao.java:
  String sql = """
      SELECT p.id, p.id_sala, p.num_mesa, p.total, p.total_bs, p.tasa_cambio, p.usuario, p.fecha, p.estado
      FROM pedidos p
      WHERE p.id_sala = ?
        AND p.num_mesa = ?
        AND p.estado = 'PENDIENTE'
      ORDER BY p.id DESC
      LIMIT 1
      """;
  ```

---

### FASE 5: Colecciones Secuenciadas (JEP 431) y Stream API con Guardas

* **1. Navegación Segura de Colecciones:**
  - Para obtener el último pedido o sala registrada, usar guardas obligatorias:
  ```java
  public Optional<Pedido> obtenerUltimoPedidoRegistrado(List<Pedido> pedidos) {
      if (pedidos == null || pedidos.isEmpty()) {
          return Optional.empty();
      }
      return Optional.of(pedidos.getLast()); // Java 21 Sequenced Collections
  }
  ```
* **2. Inversión Cronológica para Tablas Históricas:**
  ```java
  List<Pedido> historialOrdenado = listaPedidos.reversed();
  ```
* **3. Uso de `Stream.toList()` con Copia Defensiva para Modelos Mutables de Swing:**
  ```java
  List<String> nombresPlatos = listaPlatos.stream()
      .map(Platos::getNombre)
      .toList(); // Lista inmutable

  // Si el JTable requiere modificar la lista:
  List<Platos> listaMutable = new ArrayList<>(platosDao.listar().stream().toList());
  ```

---

### FASE 6: Estrategia de Pruebas y Validación Automatizada

1. **Ejecución de la Suite de Regresión Existente (111 tests):**
   - Ejecutar `ant test` completo en JVM 21 para validar que los 111 tests de negocio, DAOs y suites adversariales pasen con **0 fallos y 0 errores**.
2. **Nuevas Suites de Validación Específicas de Java 21:**
   - `test/Servicio/Java21VirtualThreadsConcurrencyTest.java`:
     - Lanzar 100 tareas concurrentes en hilos virtuales simulando cálculos bimonetarios y consultas I/O.
     - Validar que no existan bloqueos de carrier thread ni excepciones de concurrencia.
   - `test/Modelo/Java21RecordPatternMatchingTest.java`:
     - Probar exhaustividad en `switch` con guard clauses (`when`).
     - Probar inmutabilidad y validación en constructores compactos de `CotizacionMonedaRecord`.
   - `test/Servicio/Java21SequencedCollectionsTest.java`:
     - Probar comportamiento de `getFirst()`, `getLast()` y `reversed()`, verificando la captura adecuada de `NoSuchElementException` en listas vacías.

---

### FASE 7: Empaquetado Distribuible y Actualización de Guías

1. **Generación del JAR con compilador 21:**
   ```bash
   ant clean jar
   ```
2. **Actualización de `iniciar_restaurante.bat`:**
   - Incorporar flags de alto rendimiento:
     ```bat
     java -XX:+UseZGC -XX:+ZGenerational -jar dist/Restaurante.jar
     ```
3. **Actualización de `README.txt`:**
   - Confirmar Java 21 LTS como el estándar oficial de compilación y ejecución.

---

## 4. PLAN DE ROLLBACK Y CONTINGENCIA

Si durante las pruebas en algún entorno específico de Windows se detectara una incompatibilidad con hardware legado o drivers de terceros:
1. Revertir `javac.source=17` y `javac.target=17` en `project.properties`.
2. Como los JavaBeans de dominio se preservaron intactos, el sistema mantiene compatibilidad hacia atrás inmediata sin pérdida de datos.

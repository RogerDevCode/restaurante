# E2E virtual de jornada completa y combinatoria fiscal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Crear un recorrido E2E determinista de jornada completa que use la ventana Swing real, `Robot` y MySQL aislado para cubrir cuentas, rondas, IVA, tasas, pagos, cancelación y cierre Z.

**Architecture:** Un lanzador inicia MySQL desde `docker-compose.integration.yml` con un proyecto Compose único y efímero y luego delega el display a `e2e_virtual.sh`. Una clase prepara fixtures con DAO, opera `Sistema` con `Robot`, verifica persistencia SQL tras las transiciones y deja que el lanzador destruya la BD de prueba.

**Tech Stack:** Java 21, Swing, `java.awt.Robot`, JDBC/MySQL 8.4, Docker Compose, Apache Ant, Bash, Xvfb.

## Global Constraints

- La prueba exige `DB_URL`, `DB_USER` y `DB_PASSWORD` explícitos para `jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`, `restaurante_test_app` y `test_only_app_password`.
- El E2E se ejecuta mediante `./e2e_virtual.sh Vista.DiaCompletoFiscalE2EVirtual` en Xvfb `:99` a `1366x711x24`; nunca controla el display `:0`.
- Las interacciones de operador usan mouse y teclado de `Robot`; la reflexión sobre `Sistema` solo localiza controles y lee estado para aserciones.
- Los montos se persisten y comparan a dos decimales; la tasa usa hasta cuatro decimales, según la política monetaria del proyecto.
- La generación del Cierre Z usa `CierreCajaServicio` real con generador temporal y adaptador de impresión/visor sin-op para evitar hardware y aplicaciones externas.
- El lanzador solo elimina su propio proyecto Compose `restaurante-dia-e2e-<pid>-<epoch>` y su volumen. Debe fallar si el puerto 3307 está ocupado y no debe detener contenedores ajenos.
- No se ejecutan las pruebas en esta implementación sin una solicitud explícita del usuario para verificarlas; `ant -q compile-test` solo compila las clases.

---

### Task 1: Lanzador MySQL efímero y documentación del comando

**Files:**
- Create: `e2e_dia_completo_virtual.sh`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: `docker-compose.integration.yml`, `./e2e_virtual.sh`, credenciales declaradas en `build.xml`.
- Produces: `./e2e_dia_completo_virtual.sh`, que ejecuta `Vista.DiaCompletoFiscalE2EVirtual` y retorna el código de salida de la prueba.

- [ ] **Step 1: Crear el lanzador seguro**

Crear `e2e_dia_completo_virtual.sh` con este contenido:

```bash
#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
command -v docker >/dev/null || { echo "Falta Docker" >&2; exit 1; }
command -v Xvfb >/dev/null || { echo "Falta Xvfb (apt install xvfb)" >&2; exit 1; }

PROYECTO="restaurante-dia-e2e-$$-$(date +%s)"
COMPOSE=(docker compose -p "$PROYECTO" -f docker-compose.integration.yml)

limpiar() {
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap limpiar EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

export DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER='restaurante_test_app'
export DB_PASSWORD='test_only_app_password'

"${COMPOSE[@]}" up -d --wait
./e2e_virtual.sh Vista.DiaCompletoFiscalE2EVirtual "$@"
```

- [ ] **Step 2: Registrar cómo se ejecuta**

Agregar a la tabla Build / Deploy de `AGENTS.md` una fila con `./e2e_dia_completo_virtual.sh` y la descripción: crea MySQL desechable usando el proyecto Compose exclusivo de la ejecución y ejecuta el día completo en Xvfb `:99` mediante `e2e_virtual.sh`; elimina solo el volumen propio al finalizar.

- [ ] **Step 3: Revisar sintaxis sin iniciar contenedores**

Run: `bash -n e2e_dia_completo_virtual.sh`

Expected: exit code `0`, sin salida. No correr todavía el lanzador.

---

### Task 2: Fixture fiscal determinista y barrera de BD

**Files:**
- Create: `test/Vista/DiaE2EFixture.java`

**Interfaces:**
- Produces: `DiaE2EFixture.preparar()` devuelve sala de prueba, nombres de seis platos, tasa inicial, IVA inicial y `LoginDao`/DAOs para verificaciones.
- Produces: `DiaE2EFixture.exigirBdAislada()` valida las tres propiedades JDBC antes de conectarse.
- Consumes: `Modelo.SalasDao`, `Modelo.PlatosDao`, `Modelo.LoginDao`, `infraestructura.ProveedorConexionJdbc`.

- [ ] **Step 1: Codificar la validación previa de conexión**

Implementar `exigirBdAislada()` para leer `System.getProperty("DB_URL")`, `DB_USER` y `DB_PASSWORD`. Aceptar únicamente el URL fijo indicado en Global Constraints, el usuario `restaurante_test_app` y la contraseña `test_only_app_password`. Si una propiedad falta o no coincide, lanzar `IllegalStateException` antes de crear `ProveedorConexionJdbc`.

La clase principal llamará primero a este método y solo después consultará usuario, configuración o datos.

- [ ] **Step 2: Definir la matriz de carta con precios reproducibles**

Crear un `record ProductoFiscal(String clave, String moneda, BigDecimal precio, boolean aplicaIva, boolean incluyeIva)` y una lista inmutable con estos valores, para tasa inicial `40.0000` e IVA `16.00`:

| Clave | Moneda | Precio primario | Aplica IVA | Incluye IVA |
|---|---|---:|---:|---:|
| `EX_USD` | USD | 3.00 | false | false |
| `EX_VES` | VES | 120.00 | false | false |
| `MAS_USD` | USD | 10.00 | true | false |
| `MAS_VES` | VES | 400.00 | true | false |
| `INC_USD` | USD | 11.60 | true | true |
| `INC_VES` | VES | 464.00 | true | true |

Los importes secundarios serán USD×40 o VES÷40 con escala dos y `HALF_UP`. Los nombres guardados se formarán con `DIA_E2E_<clave>_<sufijo>` para no depender de platos existentes.

- [ ] **Step 3: Crear fixtures con DAOs en la BD aislada**

Implementar `preparar()` para:

1. Leer la configuración semilla y dejar tasa `40.0000` e IVA `16.00` antes de abrir `Sistema`.
2. Registrar una sala con ocho mesas y nombre único `DIA_E2E_SALA_<sufijo>`.
3. Registrar los seis `Platos` con moneda, precio base, referencia secundaria y banderas fiscales de la tabla.
4. Volver a consultar los registros por su prefijo y fallar si la sala o alguno de los seis platos no se persistió con sus banderas.
5. Desactivar la impresión de comandas de cocina mediante `ConfiguracionSistemaDao.guardar(ConfigClaves.IMPRIMIR_COMANDA_COCINA, "0")` para que ninguna acción dependa de una impresora.

- [ ] **Step 4: Compilar las clases de test**

Run: `ant -q compile-test`

Expected: `BUILD SUCCESSFUL`; no se ejecutan tests.

---

### Task 3: Driver Robot y arranque seguro de `Sistema`

**Files:**
- Create: `test/Vista/DiaE2ERobot.java`
- Create: `test/Vista/DiaCompletoFiscalE2EVirtual.java`

**Interfaces:**
- `DiaE2ERobot`: `edt(Callable<T>)`, `campo(Sistema,String)`, `clic(Component)`, `clicCelda(JTable,int,int)`, `escribir(String)`, `reemplazarTexto(Component,String)`, `esperar(BooleanSupplier,String)`.
- `DiaCompletoFiscalE2EVirtual`: `public static void main(String[] args) throws Exception` y `main` delega en fases privadas `abrirSistema`, `primeraRonda`, `segundaRonda`, `pedidosIndividuales`, `pagarPedidos`, `actualizarConfiguracion`, `cancelarCuentaAbierta`, `generarCierreZ`.

- [ ] **Step 1: Implementar `edt` y el acceso de solo lectura a campos**

Copiar el patrón probado de `test/Vista/E2EVirtual.java`: `edt` usa `SwingUtilities.invokeAndWait`, propaga cualquier throwable como `RuntimeException`, y `campo` usa `getDeclaredField`, `setAccessible(true)` y `field.get(sistema)`. No agregar métodos para escribir en la UI por reflexión ni disparar listeners manualmente.

- [ ] **Step 2: Implementar interacciones con `Robot`**

`clic` obtiene ubicación y tamaño dentro de `edt`, mueve el mouse al centro, pulsa y libera `BUTTON1_DOWN_MASK`, y espera la actualización visual. `clicCelda` usa `JTable.getCellRect` y `getLocationOnScreen` para apuntar al centro de una celda. `reemplazarTexto` hace clic en el componente, pulsa Ctrl+A y teclea el valor carácter por carácter. `esperar` consulta una condición cada 100 ms hasta 10 segundos y lanza `AssertionError` con el mensaje recibido si no se cumple.

No usar `JButton.doClick()`, `Robot` fuera del display actual ni llamadas a handlers privados.

- [ ] **Step 3: Abrir la aplicación real solo tras validar la BD**

En `main`, ejecutar en este orden: `DiaE2EFixture.exigirBdAislada()`, `DiaE2EFixture.preparar()`, crear usuario administrador con ID semilla `2`, construir los controladores JDBC reales y `Sistema` en el EDT, crear `Robot`, maximizar y mostrar `Sistema`, esperar a que se carguen sala/mesas/platos.

El bloque `finally` debe cerrar `Sistema` con `dispose()` en EDT y eliminar el directorio temporal de PDFs. Al final imprimir `E2E_OK` o `E2E_FALLO(n)` y retornar código cero o uno respectivamente.

- [ ] **Step 4: Compilar clases de test**

Run: `ant -q compile-test`

Expected: `BUILD SUCCESSFUL`; no se ejecutan tests.

---

### Task 4: Apertura de mesas, matriz combinada y segunda ronda

**Files:**
- Modify: `test/Vista/DiaCompletoFiscalE2EVirtual.java`
- Modify: `test/Vista/DiaE2ERobot.java`

**Interfaces:**
- `DiaE2ERobot.seleccionarMesa(Sistema,String,int)`: navega a Atención Directa, elige la sala visible y hace clic en el botón de mesa indicado, usando `Robot`.
- `DiaCompletoFiscalE2EVirtual.primeraRonda()` crea el pedido de seis perfiles.
- `DiaCompletoFiscalE2EVirtual.segundaRonda()` reabre una mesa ocupada, añade dos líneas y actualiza el pedido.
- `DiaCompletoFiscalE2EVirtual.pedidosIndividuales()` crea seis pedidos separados, uno por cada perfil fiscal.

- [ ] **Step 1: Navegar al salón con mouse y seleccionar mesa libre**

Localizar el botón lateral `btnAtencionDirecta`, clicar con `Robot`, localizar la pestaña visible de sala por el nombre único del fixture y clicar la mesa libre por su etiqueta. Comprobar que la pestaña seleccionada es la de toma de pedido y que `txtTempNumMesa` corresponde al número elegido.

- [ ] **Step 2: Agregar platos usando búsqueda y tabla de menú**

Para cada clave de la carta, reemplazar el texto de `txtBuscarPlato` con el nombre único del plato, esperar hasta que `tblTemPlatos` muestre la fila y clicar la celda del producto. Leer `tableMenu` después de cada clic y comprobar que las seis líneas quedaron en el carrito, con cantidades esperadas y moneda/tasa visibles.

Con tasa `40.0000` e IVA `16.00`, la cuenta de seis líneas debe dar USD `52.40` y VES `2096.00`; el cálculo independiente se basa en exento USD `3.00`, exento VES `120.00`, USD gravado añadido `10.00 + 1.60`, VES gravado añadido `400.00 + 64.00`, USD incluido `11.60 = 10.00 + 1.60` y VES incluido `464.00 = 400.00 + 64.00`.

- [ ] **Step 3: Enviar primera ronda y comprobar persistencia**

Clicar `btnGenerarPedido` con `Robot`, esperar un nuevo pedido `PENDIENTE` de la sala/mesa elegida y consultar por JDBC cabecera y detalles. Comprobar las seis parejas de banderas y monedas, total, subtotal, IVA y tasa congelada. Volver a comprobar que la mesa aparezca ocupada en el panel.

- [ ] **Step 4: Reabrir la cuenta y enviar segunda ronda**

Desde Atención Directa, clicar la misma mesa ocupada, clicar `btnModificarPedido`, añadir `EX_USD` y `MAS_VES` con búsqueda/clics y clicar `btnGenerarPedido` para actualizar. Consultar otra vez el pedido y comprobar que siguen seis líneas, que `EX_USD` y `MAS_VES` pasan a cantidad dos sin duplicación y que el total pasa de USD `52.40` / VES `2096.00` a USD `67.00` / VES `2680.00`.

- [ ] **Step 5: Crear los seis pedidos individuales**

Para cada una de las seis claves fiscales, seleccionar con `Robot` una mesa libre, agregar solo el plato de esa clave, enviar el pedido y consultar cabecera/detalle con JDBC. Verificar una sola línea con la moneda base, condición de IVA, `precio_incluye_iva`, porcentaje guardado, subtotal, IVA y total esperado: `EX_USD` USD `3.00`, `EX_VES` VES `120.00`, `MAS_USD` USD `11.60`, `MAS_VES` VES `464.00`, `INC_USD` USD `11.60` e `INC_VES` VES `464.00`. Dejar las cuentas pendientes para que las usen los escenarios de cobro.

- [ ] **Step 6: Compilar clases de test**

Run: `ant -q compile-test`

Expected: `BUILD SUCCESSFUL`; no se ejecutan tests.

---

### Task 5: Cobros, cambio de condiciones y cancelación de fuga

**Files:**
- Modify: `test/Vista/DiaCompletoFiscalE2EVirtual.java`
- Modify: `test/Vista/DiaE2ERobot.java`
- Modify: `test/Vista/DiaE2EFixture.java`

**Interfaces:**
- `pagarPedido(int,List<PagoUi>)`: abre el pedido desde la mesa, edita la tabla inline de pagos con `Robot`, finaliza y devuelve el ID para aserciones. `PagoUi` contiene `metodo`, `moneda` y `monto` en texto.
- `actualizarConfiguracion(String,String)`: abre `Datos de la Empresa`, reemplaza tasa/IVA, guarda por UI y confirma con `Robot` el diálogo de cambio de tasa.
- `cancelarCuentaAbierta(int)`: selecciona la cuenta pendiente desde UI y ejecuta la acción de fuga/cancelación.

- [ ] **Step 1: Cobrar un pedido solo en VES y otro solo en USD**

Abrir el pedido individual `EX_VES` desde su mesa, dejar el método `EFECTIVO_BS` / moneda `VES`, introducir `120.00` y finalizar mediante `Robot`. Abrir `EX_USD`, escoger `EFECTIVO_USD` / `USD`, introducir `3.00` y finalizar. Comprobar que ambos quedan `FINALIZADO`, cada uno tiene una fila de `pedido_pagos`, monto recibido igual al aplicado, y que la suma aplicada equivale a su total en VES usando la tasa guardada.

- [ ] **Step 2: Rechazar saldo corto y excedente antes de aceptar el cobro**

Abrir `MAS_USD`. En la tabla inline de pagos introducir primero `11.59`, clicar `btnFinalizar` y comprobar que aparece alerta de saldo pendiente, el pedido sigue `PENDIENTE` y no hay filas de pago. Reemplazar el monto por `11.61`, intentar de nuevo y comprobar alerta de excedente con el mismo estado persistente. Finalmente introducir `11.60` y completar el cobro. Todas las ediciones se hacen con clic y teclado sobre `tablaPagosFac`.

- [ ] **Step 3: Cambiar tasa e IVA durante la jornada por la interfaz**

- [ ] **Step 2: Cambiar tasa e IVA durante la jornada por la interfaz**

Dejar una cuenta pendiente abierta y una factura pagada. Navegar con `Robot` al tab `Datos de la Empresa`, cambiar la tasa de `40.0000` a `42.0000` y el IVA global de `16.00` a `10.00`, clicar `btnActualizarConfig` y confirmar el `JOptionPane` con Enter.

Comprobar que cada pedido pendiente incrementó `version_precio`, guarda tasa `42.0000` y recalcula sus montos conservando el IVA `16.00` por línea. Comprobar que las facturas pagadas conservan los valores monetarios y flags de cabecera/detalles. Después, pagar `INC_USD` y `INC_VES` solo en su moneda base con sus totales originales `11.60` y `464.00`.

- [ ] **Step 4: Cobrar una cuenta combinada con dos monedas**

Abrir la cuenta combinada de la primera mesa; tras el cambio de tasa debe guardar tasa `42.0000` y mantener IVA `16.00` por línea. Consultar su `total_bs` desde JDBC; introducir en dos filas de `tablaPagosFac` la mitad del total como `EFECTIVO_BS` / `VES` y la mitad del total dividida entre `42.0000` como `EFECTIVO_USD` / `USD`, ambas a dos decimales. Finalizar desde UI. Verificar estado, dos pagos, método `MIXTO`, importes por moneda y conciliación total.

- [ ] **Step 5: Crear un pedido con el IVA nuevo**

Usar una mesa liberada para crear por UI un pedido combinado con `MAS_USD` e `INC_VES`. Comprobar que la línea exclusiva guarda IVA `10.00`, que la inclusiva totaliza `VES 464.00` (`base VES 421.82` + `IVA VES 42.18`) y que la cabecera concuerda con las dos líneas. Finalizarlo en la interfaz con un pago exacto.

- [ ] **Step 6: Cancelar una cuenta pendiente sin registrar dinero**

Abrir la octava mesa, crear y enviar un pedido pendiente, cargarlo a la pantalla de cobro, clicar `btnAnularFuga`, ingresar motivo `Cliente se retiro sin pagar` en el diálogo y confirmar con teclado/mouse. Consultar su estado, mesa, `pedido_pagos` y liquidaciones: no debe quedar pendiente ni finalizado y no debe crear pago ni venta.

- [ ] **Step 7: Compilar clases de test**

Run: `ant -q compile-test`

Expected: `BUILD SUCCESSFUL`; no se ejecutan tests.

---

### Task 6: Cierre Z, conciliación final y límites del runner

**Files:**
- Modify: `test/Vista/DiaCompletoFiscalE2EVirtual.java`
- Modify: `test/Vista/DiaE2EFixture.java`
- Modify: `AGENTS.md`

**Interfaces:**
- `generarCierreZ()`: abre la pestaña Historial y el formulario con `getBtnCierreTotal()`, confirma la fecha actual y selecciona `Imprimir Directo` para persistir cierre con el adaptador no-op.
- `DiaE2EFixture.cierreZ(LocalDate)`: consulta el cierre persistido y sus desglose por método/sala para validar conciliación.

- [ ] **Step 1: Inyectar salida de cierre que no requiere desktop externo**

 Crear un directorio con `Files.createTempDirectory("dia-e2e-pdf-")`. Construir `CierreCajaServicio` con `new CierreCajaDao()`, `loginDao::datosEmpresa`, `new GeneradorPdfCierre(pdfTemp)`, `archivo -> true` para impresor y `archivo -> true` para visor. Pasarlo al `PedidosControlador` antes de construir `Sistema`. No llamar a `previsualizarCierre`, porque ese flujo no inserta auditoría Z.

- [ ] **Step 2: Generar el cierre mediante UI y verificar la auditoría**

Desde la pestaña Historial, clicar `getBtnCierreTotal()` con `Robot`; en el diálogo dejar la fecha de hoy, campos de efectivo contado vacíos y clicar `Imprimir Directo (80mm)`. Esperar el `SwingWorker` y consultar la tabla `cierres_caja` para la fecha actual y tipo `TOTAL`.

Comprobar que el PDF temporal existe, el cierre contiene solo ventas pagadas del día de esta BD efímera, y que `totalVentasBs` coincide con `desgloseMetodos Bs`, `desgloseSalas Bs` y suma de pagos más ajustes de cambio. Comprobar también subtotal/base imponible, exento e IVA según las cabeceras de pedidos finalizados.

- [ ] **Step 3: Cerrar recursos y documentar la invocación**

En `finally`, cerrar la ventana con `dispose()` en EDT y eliminar todos los archivos del directorio temporal. Añadir a `AGENTS.md` el comando del lanzador, sus prerequisitos Docker/Xvfb/Java 21 y el hecho de que ejecuta únicamente `restaurante_test` en el display `:99`.

- [ ] **Step 4: Revisar el conjunto de cambios y la política de aislamiento**

Run: `git diff --check`

Expected: sin salida. Confirmar manualmente que el lanzador usa proyecto Compose con PID y epoch, el test valida URL/usuario/password, no hay `doClick()` en la clase o helper y ningún comando de esta implementación apunta a `MYSQL_DB=restaurante` o display `:0`.

---

## Revisión de cobertura del plan

| Requisito de la especificación | Tarea |
|---|---|
| Base MySQL aislada y destrucción solo de su proyecto | 1 |
| Rechazo del `.env` y fixtures fiscales USD/VES | 2 |
| Interfaz Swing real, mouse y teclado propios | 3 |
| Seis cuentas individuales, canasta combinada y ronda incremental | 4 |
| Pagos VES/USD/mixtos, bloqueo de saldo incorrecto, cambio tasa/IVA y fuga | 5 |
| Cierre Z persistido, PDF temporal y conciliación | 6 |
| Documentación de comando y restricciones | 1, 6 |

El plan no incluye ejecutar las pruebas. La ejecución de `./e2e_dia_completo_virtual.sh` queda para cuando el usuario solicite verificación.

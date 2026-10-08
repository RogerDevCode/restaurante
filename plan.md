# Plan de Mejoras — Restaurante 2026

> **Convenciones de este documento**
> - Cada tarea lleva el archivo exacto a modificar con número de línea de referencia.
> - Las tareas de BD se ejecutan **antes** que el código Java que las usa.
> - Las migraciones siguen la numeración existente (`008_`, `009_`, …).
> - Los tests a actualizar se mencionan junto a la tarea que los afecta.

---

## Resumen de sprints

| Sprint | Qué incluye | Riesgo | Estado |
|--------|-------------|--------|--------|
| 1 | Botones `+`/`−` en carrito (sin BD) | Muy bajo | ✅ Completado & Probado Adversarialmente |
| 2 | Filtros rápidos en historial (sin BD) | Muy bajo | ✅ Completado & Probado Adversarialmente |
| 3 | Leyenda + timestamp en panel de mesas (sin BD) | Muy bajo | ✅ Completado & Probado Adversarialmente |
| 4 | Método de pago al finalizar (migración + UI + PDF) | Medio | ✅ Completado & Probado Adversarialmente |
| 5 | Soft delete platos + toggle en UI | Bajo | ✅ Completado & Probado Adversarialmente |
| 6 | Purga configurable de registros históricos | Medio | ✅ Completado & Probado Adversarialmente |
| 7 | Re-impresión de PDF desde historial | Bajo | ✅ Completado & Probado Adversarialmente |
| 8 | Dashboard visible al login de administrador | Muy bajo | ✅ Completado & Probado Adversarialmente |

---

## Sprint 1 · Botones `+` / `−` en el carrito con subtotal por línea

### Contexto
El carrito (`tableMenu`) ya muestra columnas `[id, nombre, cantidad, precio, subtotal, comentario]`
([`Sistema.java:1782-1797`](src/Vista/Sistema.java#L1782)). Al agregar el mismo plato ya suma cantidad
([`Sistema.java:1771-1779`](src/Vista/Sistema.java#L1771)). Solo faltan los botones de ajuste fino.

### Tareas

#### 1.1 Agregar botones en la toolbar del carrito — `Sistema.java`
- Localizar el panel que contiene `btnEliminarTempPlato` (~línea 884 del `.form`).
- Añadir `btnMasCantidad` (`+`) y `btnMenosCantidad` (`−`) al mismo panel con `AbsoluteConstraints`.
- **Listener `btnMasCantidad`:**
  ```
  fila seleccionada → leer cantidadActual de columna 2
  cantidadNueva = cantidadActual + 1
  subtotalNuevo = precio (col 3) × cantidadNueva
  tmp.setValueAt(cantidadNueva, fila, 2)
  tmp.setValueAt(subtotalNuevo, fila, 4)
  TotalPagar(tableMenu, totalMenu)
  ```
- **Listener `btnMenosCantidad`:**
  ```
  si cantidadActual > 1 → igual que arriba con cantidadNueva = cantidadActual - 1
  si cantidadActual == 1 → eliminar fila (igual que btnEliminarTempPlato)
  TotalPagar(tableMenu, totalMenu)
  ```
- Deshabilitar ambos botones si ninguna fila está seleccionada
  (usar `tableMenu.getSelectionModel().addListSelectionListener`).

#### 1.2 Tests — `MejorasUIYServicioTest.java`
- Agregar casos: aumentar cantidad, disminuir a 1, disminuir a 0 (elimina fila), subtotal correcto.

**No hay cambios de BD ni de modelo.**

---

## Sprint 2 · Filtros rápidos en historial

### Contexto
El filtro actual ([`Sistema.java:2636-2646`](src/Vista/Sistema.java#L2636))
usa un `RowFilter.regexFilter` sobre `TablePedidos`.
El estado del pedido está en la columna 5 (valor `PENDIENTE` / `FINALIZADO`) —
verificar índice exacto con los headers declarados en `TablePedidos`.

### Tareas

#### 2.1 Añadir controles en `jPanel6` — `Sistema.java` (constructor, ~línea 157)
Junto a `txtBuscarHistorial` y `lblBuscarHistorial` agregar:
```
JButton btnHoy           → aplica filtro fecha = LocalDate.now()
JButton btnPendientes    → aplica filtro estado = "PENDIENTE"
JButton btnFinalizados   → aplica filtro estado = "FINALIZADO"
JTextField txtDesde      → fecha inicio (formato yyyy-MM-dd, placeholder "Desde")
JTextField txtHasta      → fecha fin  (formato yyyy-MM-dd, placeholder "Hasta")
JButton btnLimpiarFiltro → resetea todo: texto vacío, deshabilita sorter
```

#### 2.2 Refactorizar `aplicarFiltroHistorial()` — `Sistema.java`
Convertir el método a un `RowFilter` compuesto:
```java
private void aplicarFiltroHistorial() {
    TableRowSorter<?> sorter = (TableRowSorter<?>) TablePedidos.getRowSorter();
    if (sorter == null) return;

    List<RowFilter<Object,Object>> filtros = new ArrayList<>();

    // filtro de texto libre (columnas todas)
    String texto = txtBuscarHistorial.getText().trim();
    if (!texto.isEmpty())
        filtros.add(RowFilter.regexFilter("(?i)" + Pattern.quote(texto)));

    // filtro de estado (columna índice estado — verificar)
    if (filtroPendiente)   filtros.add(RowFilter.regexFilter("PENDIENTE", COL_ESTADO));
    if (filtroFinalizado)  filtros.add(RowFilter.regexFilter("FINALIZADO", COL_ESTADO));

    // filtro de fecha (columna índice fecha)
    if (!txtDesde.getText().isBlank() || !txtHasta.getText().isBlank())
        filtros.add(filtroRangoFecha(txtDesde.getText(), txtHasta.getText()));

    sorter.setRowFilter(filtros.isEmpty() ? null : RowFilter.andFilter(filtros));
}
```
Donde `filtroRangoFecha` compara strings ISO (`yyyy-MM-dd HH:mm:ss`) con `compareTo`.

#### 2.3 Tests — `ListaPedidosSwingWorkerTest.java` / nuevo `FiltrosHistorialTest`
- Verificar que cada botón aplica el filtro correcto sobre un `DefaultTableModel` de prueba.

**No hay cambios de BD ni de modelo.**

---

## Sprint 3 · Leyenda de mesas + botón Actualizar + timestamp

### Contexto
[`PanelMesasSwingWorker`](src/Vista/PanelMesasSwingWorker.java) devuelve
`Map<Integer,Integer>` (número de mesa → id pedido pendiente, 0 si libre).
El método `panelMesas` en `Sistema.java` pinta cada botón. No hay leyenda ni hora.

### Tareas

#### 3.1 Crear leyenda visual — `Sistema.java`, método `panelMesas()`
Al final del panel de mesas (después de pintar los botones) añadir:
```java
JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
leyenda.setOpaque(false);
leyenda.add(cuadroColor(new Color(144, 238, 144), "Libre"));
leyenda.add(cuadroColor(new Color(255, 160, 122), "Ocupada"));
// cuadroColor() crea un JLabel con border coloreado + texto
```

#### 3.2 Botón "Actualizar mesas" en panel de mesas — `Sistema.java`
- Añadir `JButton btnActualizarMesas` al header del panel de mesas.
- Al presionar → llamar `panelMesas(idSala, nombreSala, cantMesas)` nuevamente.

#### 3.3 Timestamp de última carga — `Sistema.java`
- Declarar `JLabel lblUltimaCargaMesas`.
- Al terminar `PanelMesasSwingWorker.done()` → actualizar label con
  `LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))`.

#### 3.4 Tests — `PanelMesasSwingWorkerTest.java`
- Verificar que el callback `alCompletar` recibe el mapa correcto (ya existe test base).

**No hay cambios de BD.**

---

## Sprint 4 · Método de pago al finalizar

### Contexto
El campo `cliente_nombre` y `cliente_documento` ya existen en `pedidos`
([migración 007](db/migrations/007_clientes_y_dashboard.sql)).
[`PedidosDao.actualizarEstadoConCliente`](src/Modelo/PedidosDao.java#L379)
hace el `UPDATE`. El PDF ya imprime nombre y cédula del cliente
([`GeneradorPdfPedido.java:170`](src/Servicio/GeneradorPdfPedido.java#L170)).
Falta el campo `metodo_pago`.

### Tareas

#### 4.1 Migración SQL `008_metodo_pago.sql`
```sql
-- Migración 008: Método de pago en pedidos
ALTER TABLE pedidos
  ADD COLUMN metodo_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO'
  AFTER cliente_documento;
```
Guardar en `db/migrations/008_metodo_pago.sql` y agregar al script `actualizar_bd.sql`.

#### 4.2 Modelo `Pedidos.java`
- Agregar campo `private String metodoPago = "EFECTIVO";`
- Agregar `getMetodoPago()` / `setMetodoPago(String)` con validación
  (valor debe ser uno de: EFECTIVO, TRANSFERENCIA, TARJETA, PAGO_MOVIL, MIXTO).

#### 4.3 `PedidosDao.java`
- En `actualizarEstadoConCliente()` (~línea 386): agregar `metodo_pago = ?` al `UPDATE`.
- Añadir setter en la firma del método:
  `actualizarEstadoConCliente(int idPedido, String clienteNombre, String clienteDoc, String metodoPago)`.
- En `verPedido()` (~línea 280): añadir `p.metodo_pago` al `SELECT` y mapearlo.
- En `listarPedidos()` (~línea 445): idem.
- En métodos `*Legacy`: dejar `metodoPago = "EFECTIVO"` por defecto (sin cambio de SQL).

#### 4.4 `PedidosRepositorio.java` (interfaz)
- Actualizar firma de `actualizarEstadoConCliente` con el parámetro `metodoPago`.

#### 4.5 `PedidosControlador.java`
- Actualizar `finalizarPedidoConCliente()` para recibir y pasar `metodoPago`.

#### 4.6 `FinalizarPedidoSwingWorker.java`
- Agregar campo `private final String metodoPago`.
- Actualizar constructores para recibir `metodoPago`.
- Pasar `metodoPago` a `controlador.finalizarPedidoConCliente(...)`.

#### 4.7 Diálogo de finalización — `Sistema.java` (~línea 1877)
- Agregar al panel `panelFacturar`:
  ```java
  JComboBox<String> cbMetodoPago = new JComboBox<>(
      new String[]{"EFECTIVO","TRANSFERENCIA","TARJETA","PAGO_MOVIL","MIXTO"});
  panelFacturar.add(new JLabel("Método de Pago:"));
  panelFacturar.add(cbMetodoPago);
  ```
- Leer `cbMetodoPago.getSelectedItem()` al confirmar y pasarlo al `FinalizarPedidoSwingWorker`.

#### 4.8 `GeneradorPdfPedido.java`
- En `agregarEncabezado()` (~línea 170): añadir línea
  `"\\nMétodo de Pago: " + texto(pedido.getMetodoPago())` al `Paragraph informacion`.
- En `agregarCierre()` (~línea 254): cambiar `"Cancelación"` por
  `"Forma de Pago: " + texto(pedido.getMetodoPago())`.

#### 4.9 Tests a actualizar
- `PedidoServicioTest` / `PedidosDaoTest`: agregar método de pago en fixtures.
- `GeneradorPdfPedidoTest`: verificar que el PDF incluye el método de pago.
- `FinalizarPedidoSwingWorkerTest`: pasar `metodoPago` en la construcción.

---

## Sprint 5 · Soft delete de platos

### Contexto
[`PlatosDao.eliminar()`](src/Modelo/PlatosDao.java#L86) hace `DELETE FROM platos WHERE id = ?`.
[`Platos.java`](src/Modelo/Platos.java) no tiene campo `activo`.

### Tareas

#### 5.1 Migración `009_soft_delete_platos.sql`
```sql
-- Migración 009: Soft delete en platos
ALTER TABLE platos
  ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN desactivado_en DATETIME NULL DEFAULT NULL;
```

#### 5.2 `Platos.java`
- Agregar campos `private boolean activo = true;` y `private java.time.LocalDateTime desactivadoEn;`.
- Getters y setters estándar.

#### 5.3 `PlatosDao.java`
- `listarPorFecha()`: agregar `AND activo = 1` a ambas variantes del SQL.
- Renombrar `eliminar()` → `desactivar()`:
  ```java
  public boolean desactivar(int id) {
      String sql = "UPDATE platos SET activo = 0, desactivado_en = NOW() WHERE id = ?";
      ...
  }
  ```
- Agregar método `reactivar(int id)`:
  ```java
  public boolean reactivar(int id) {
      String sql = "UPDATE platos SET activo = 1, desactivado_en = NULL WHERE id = ?";
      ...
  }
  ```
- Agregar método `listarInactivos()` (para el panel de reactivación).
- Mantener el método legacy `Eliminar()` redirigiendo a `desactivar()`.

#### 5.4 `PlatosRepositorio.java` (interfaz)
- Reemplazar `eliminar(int id)` por `desactivar(int id)` y agregar `reactivar(int id)`.

#### 5.5 `PlatosServicio.java` / `PlatosControlador.java`
- Actualizar llamadas de `eliminar` a `desactivar`.
- Agregar método `reactivar(int id)`.

#### 5.6 UI — `Sistema.java`
- Renombrar el botón `btnEliminarPlato` → conservar el nombre Swing pero cambiar el `setText` a
  `"Desactivar"` y el tooltip a `"Oculta el plato del menú sin eliminarlo"`.
- Agregar botón `btnReactivarPlato` (solo visible para administrador):
  - Muestra un `JDialog` con la lista de platos inactivos (consulta `listarInactivos()`).
  - Al confirmar → `platosControlador.reactivar(idSeleccionado)` y refresca la tabla.

#### 5.7 Tests
- `PlatosDaoTest`: verificar que `desactivar` hace UPDATE (no DELETE), `listarPorFecha` excluye inactivos.
- `PlatosServicioTest`: nuevo caso `reactivar`.

---

## Sprint 6 · Purga configurable de registros históricos

> ⚠️ **Alto riesgo — requiere doble confirmación y log de auditoría obligatorio.**

### Contexto
No existe mecanismo de purga. La tabla `pedidos` crece indefinidamente.
La purga debe ser configurable por el administrador (N meses), afectar solo
pedidos `FINALIZADO` y registrar cada ejecución en el log diario.

### Tareas

#### 6.1 Migración `010_config_retencion.sql`
```sql
-- Migración 010: Política de retención de registros
ALTER TABLE config
  ADD COLUMN meses_retencion_pedidos INT NOT NULL DEFAULT 24
  COMMENT 'Pedidos finalizados más antiguos que este valor en meses podrán ser purgados';
```

#### 6.2 `Config.java`
- Agregar `private int mesesRetencionPedidos = 24;`
- Getter / setter con validación (`>= 1`).

#### 6.3 `LoginDao.java` (o `ConfigDao`)
- En `datosEmpresa()`: incluir `meses_retencion_pedidos` en el `SELECT` y mapearlo.
- En el `UPDATE` de configuración: incluir el nuevo campo.

#### 6.4 `PedidosDao.java`
- Agregar:
  ```java
  public int purgarPedidosFinalizados(int mesesAnteriores) {
      if (mesesAnteriores < 1)
          throw ErrorAplicacionException.validacion("El período de retención debe ser al menos 1 mes.");
      String sql = """
          DELETE FROM pedidos
          WHERE estado = 'FINALIZADO'
            AND fecha < DATE_SUB(NOW(), INTERVAL ? MONTH)
          """;
      // retorna filas afectadas
  }
  ```
  > La FK `detalle_pedidos.id_pedido` debe tener `ON DELETE CASCADE` — verificar en `BD.sql`.
  > Si no la tiene, primero borrar detalles y luego pedidos en la misma transacción.

#### 6.5 `PedidosControlador.java`
- Agregar `purgarPedidosFinalizados(int meses)` que llama al DAO y
  registra en el `Logger`: `"PURGA: X pedidos finalizados eliminados (retención: Y meses)"`.

#### 6.6 UI — `Sistema.java`, pestaña Configuración
- Agregar en el panel de configuración:
  ```
  JLabel "Retención de historial (meses):"
  JSpinner spMesesRetencion  (min=1, max=120, valor=24)
  JButton btnPurgarHistorial  → solo visible para administrador
  ```
- **Flujo del botón:**
  1. Leer `spMesesRetencion.getValue()`.
  2. `JOptionPane.showConfirmDialog` → "¿Eliminar pedidos finalizados anteriores a N meses? Esta acción es irreversible."
  3. Si confirma: segundo diálogo pidiendo contraseña del administrador (validar con `AutenticacionServicio`).
  4. Si contraseña OK: `pedidosControlador.purgarPedidosFinalizados(N)` en `SwingWorker`.
  5. Mostrar resultado: "Se eliminaron X registros".

#### 6.7 Tests
- `PedidosDaoTest`: test de purga con fechas artificiales.
- `AdversarialValidationTest`: intentar purga con meses=0 → excepción.

---

## Sprint 7 · Re-impresión de PDF desde historial

### Contexto
Ya existe `btnPdfPedido` en el historial ([`Sistema.java:1615`](src/Vista/Sistema.java#L1615))
que llama a `pedidosControlador.generarPdfPedido(id)`.
Ya existe `reimprimirFacturaClienteSeleccionada()` ([`Sistema.java:2581`](src/Vista/Sistema.java#L2581))
en el panel de clientes/dashboard.
`GeneradorPdfPedido.generar()` ya respalda el PDF anterior con timestamp si existe
([`GeneradorPdfPedido.java:42-55`](src/Servicio/GeneradorPdfPedido.java#L42)).
El método `generarConTimestamp()` genera siempre un nuevo archivo sin sobreescribir.

**El mecanismo ya existe. Falta conectarlo correctamente al historial.**

### Tareas

#### 7.1 Verificar que `btnPdfPedido` funciona para finalizados — `Sistema.java`
- Confirmar que `txtIdHistorialPedido` se actualiza al seleccionar fila en `TablePedidos`
  (listener `tableSelectionChanged` o similar).
- Si no existe el listener, agregarlo:
  ```java
  TablePedidos.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && TablePedidos.getSelectedRow() >= 0) {
          int viewRow = TablePedidos.getSelectedRow();
          int modelRow = TablePedidos.convertRowIndexToModel(viewRow);
          Object idObj = TablePedidos.getModel().getValueAt(modelRow, 0);
          txtIdHistorialPedido.setText(idObj.toString());
          // habilitar botón solo si estado == FINALIZADO
          Object estadoObj = TablePedidos.getModel().getValueAt(modelRow, COL_ESTADO);
          btnPdfPedido.setEnabled("FINALIZADO".equals(estadoObj));
      }
  });
  ```

#### 7.2 Usar `generarConTimestamp()` para reimprimir — `PedidosControlador.java`
- El método `generarPdfPedido(int idPedido)` actual llama a `generador.generar()` que **ya respalda**.
- Verificar que la llamada pasa por `generarConTimestamp()` cuando se re-imprime
  para no sobreescribir el original. Opcional: agregar segundo método en el controlador
  `reimprimirPdfPedido(int idPedido)` que llama a `generarConTimestamp()`.

#### 7.3 Tooltip en `btnPdfPedido`
- Cambiar tooltip a `"Ver / Reimprimir factura del pedido seleccionado"`.

#### 7.4 Tests — `PedidoPdfServicioTest.java`
- Agregar test: reimprimir un pedido ya existente genera archivo con timestamp distinto.

---

## Sprint 8 · Dashboard visible al inicio para administrador

### Contexto
[`EstadisticasDashboard`](src/Modelo/EstadisticasDashboard.java) y el panel dashboard
([`Sistema.java:2300`](src/Vista/Sistema.java#L2300)) ya están implementados.
El tab del dashboard se agrega programáticamente con `initDashboardYClientes()`.
Falta que al login de un administrador la pestaña sea la activa por defecto.

### Tareas

#### 8.1 `Sistema.java` constructor (~línea 106, después de `initComponents()`)
```java
if (politicaAcceso.esAdministrador()) {
    initDashboardYClientes();           // ya existe
    // seleccionar el tab del dashboard como activo inicial
    jTabbedPane1.setSelectedComponent(panelDashboard);
    cargarDashboardYClientes();         // ya existe
}
```

#### 8.2 Verificar que `jTabbedPane1.setEnabled(false)` (~línea 149) no bloquea el tab
El panel está `setEnabled(false)` hasta que el usuario inicia un pedido.
Evaluar si el dashboard debe ser accesible sin pedido activo y, de ser así,
excluirlo del bloqueo:
```java
// después de jTabbedPane1.setEnabled(false)
if (panelDashboard != null) {
    jTabbedPane1.setEnabledAt(jTabbedPane1.indexOfComponent(panelDashboard), true);
}
```

#### 8.3 Tests — `DashboardYClientesTest.java`
- Verificar que el tab del dashboard es seleccionado al construir `Sistema` con rol ADMINISTRADOR.

---

## Orden de ejecución recomendado

```
Sprint 1 (±4h)  → Sprint 2 (±3h)  → Sprint 3 (±2h)
Sprint 7 (±2h)  → Sprint 8 (±1h)  → Sprint 4 (±6h)
Sprint 5 (±5h)  → Sprint 6 (±8h)
```

Los sprints 1–3 y 7–8 no tocan la BD y se pueden liberar en un día de trabajo.
Los sprints 4–6 requieren migraciones coordinadas con el entorno de producción.

---

## Migraciones pendientes (resumen)

| # | Archivo | Impacto |
|---|---------|---------|
| 008 | `008_metodo_pago.sql` | `ALTER TABLE pedidos ADD COLUMN metodo_pago` |
| 009 | `009_soft_delete_platos.sql` | `ALTER TABLE platos ADD COLUMN activo, desactivado_en` |
| 010 | `010_config_retencion.sql` | `ALTER TABLE config ADD COLUMN meses_retencion_pedidos` |

Ejecutar con `actualizar_bd.bat` / `actualizar_bd.sql` en el orden numérico.
Antes de cada migración en producción: **hacer respaldo de la BD**.

---

## Archivos afectados por sprint

| Sprint | BD | Modelo | DAO | Controlador | Servicio/PDF | Vista | Tests |
|--------|----|--------|-----|-------------|--------------|-------|-------|
| 1 | — | — | — | — | — | `Sistema.java` | `MejorasUIYServicioTest` |
| 2 | — | — | — | — | — | `Sistema.java` | nuevo `FiltrosHistorialTest` |
| 3 | — | — | — | — | — | `Sistema.java` | `PanelMesasSwingWorkerTest` |
| 4 | `008` | `Pedidos` | `PedidosDao` | `PedidosControlador` | `GeneradorPdfPedido` | `Sistema`, `FinalizarPedidoSwingWorker` | varios |
| 5 | `009` | `Platos` | `PlatosDao` | `PlatosControlador` | — | `Sistema.java` | `PlatosDaoTest`, `PlatosServicioTest` |
| 6 | `010` | `Config` | `PedidosDao`, `LoginDao` | `PedidosControlador` | — | `Sistema.java` | `PedidosDaoTest` |
| 7 | — | — | `PedidosControlador` | — | `PedidoPdfServicio` | `Sistema.java` | `PedidoPdfServicioTest` |
| 8 | — | — | — | — | — | `Sistema.java` | `DashboardYClientesTest` |

# INFORME ADVERSARIAL DE AUDITORÍA DE CÓDIGO (RED TEAM)
## Sistema Restaurante 2026 (Java 21, Swing, MySQL 8)

**Fecha:** 17 de Octubre de 2026  
**Auditor:** Líder Red Team de Seguridad y Auditoría Contable-Fiscal  
**Alcance:** Revisión exhaustiva de soluciones y correcciones implementadas recientemente en el árbol de trabajo (`CierreCajaDao.java`, `GeneradorPdfPedido.java`, `PedidosDao.java`, `PreciosPedidosDao.java`, `Sistema.java`, `LoginDao.java`, `CalculoFiscalRecord.java`, `CalculoMonetarioPedido.java`, `DetallePedido.java`, `MigradorEsquemaJdbc.java`, `actualizar_bd.sql`).

---

### RESUMEN EJECUTIVO DE HALLAZGOS

| ID | Severidad | Módulo / Archivo | Vulnerabilidad / Defecto Principal | Estado |
|---|---|---|---|---|
| **F-01** | **CRÍTICA** | `MigradorEsquemaJdbc.java` / `actualizar_bd.sql` | Denegación de servicio en arranque: `aplica_iva NOT NULL` aborta la migración en BDs con comandeo histórico. | CERRADO |
| **F-02** | **CRÍTICA** | `PreciosPedidosDao.java` | "Auto-congelado fantasma": Ítems legados quedan huérfanos con campos fiscales `NULL` en BD y causan `NullPointerException` en ejecución. | CERRADO |
| **F-03** | **ALTA** | `CierreCajaDao.java` | Destrucción del desglose de métodos de pago en Reportes X y Z: `sqlMetodos` colapsa pagos mixtos ignorando `pedido_pagos`. | CERRADO |
| **F-04** | **ALTA** | `CierreCajaDao.java` | Subtotales e IVA en Bolívares en cero para pedidos legados en el cierre de caja, produciendo reportes legalmente descuadrados. | CERRADO |
| **F-05** | **ALTA** | `GeneradorPdfPedido.java` | Reintroducción de Fallback Silencioso Hardcodeado `36.5000` en Nivel 5 para emisión de documentos no históricos. | CERRADO |
| **F-06** | **ALTA** | `GeneradorPdfPedido.java` | Tolerancia laxa de 5 centavos (`0.05`) oculta desajustes contables y produce discrepancias entre ítems impresos y total facturado. | CERRADO |
| **F-07** | **ALTA** | `PedidosDao.java` | Bloqueo absoluto de anulación de comandas cobradas (`anularPedidoConAuditoria`) por dependencia inaccesible de `pedido_saldos_movimientos`. | CERRADO |
| **F-08** | **ALTA** | `PedidosDao.java` | Falsa absorción de deudas en `resumenConciliacion`: comanda en pre-cuenta (`cerrada_en != null`, pendiente de pago) es declarada solvente. | CERRADO |
| **F-09** | **MEDIA** | `Sistema.java` | Condición de carrera en UI de carrito: pérdida de datos y secuestro de cantidades entre filas al usar `txtCantidadManual`. | CERRADO |
| **F-10** | **MEDIA** | `DetallePedido.java`, `CalculoMonetarioPedido.java`, `PedidoServicio.java` | `NullPointerException` por auto-unboxing inseguro de `Boolean aplicaIva` tras retiro de validaciones defensivas. | CERRADO |
| **F-11** | **MEDIA** | `CierreCajaDao.java` | Mezcla indiscriminada de monedas VES y USD en ranking de platos en memoria y `COALESCE(p.tasa_cambio, 1)` en SQL. | CERRADO |
| **F-12** | **BAJA** | `CierreCajaDao.java` | Código muerto, silenciamiento de `SQLException` y pérdida total del desglose estructurado al persistir en `cierres_caja`. | CERRADO |
| **F-13** | **CRÍTICA** | `PreciosPedidosDao.java` | Evasión de nulabilidad en lectura de `aplica_iva`: omisión de `rs.wasNull()` transforma `NULL` histórico en `false` exento silencioso. | CERRADO |
| **F-14** | **CRÍTICA** | `PreciosPedidosDao.java` | Auto-congelado inventa defaults prohibidos (`USD`, `excluye IVA`, `aplica IVA`) inflando comandas históricas en más de 4000%. | CERRADO |
| **F-15** | **ALTA** | `CierreCajaDao.java` / `CierreCaja.java` | `MysqlDataTruncation: Incorrect datetime value: ''` bloquea fatalmente la persistencia de cierres de caja en MySQL estricto. | CERRADO |
| **F-16** | **ALTA** | `PedidosDao.java` | Bloqueo permanente de anulación de comandas cobradas: validación de seguridad confirmada e integrada con `CierreMesaYPagosCongeladosIT`. | CERRADO |
| **F-17** | **ALTA** | `CierreCajaDao.java` | Pérdida de efectivo esperado en Cierre Z: ignora cobros de efectivo en `p.getPagos()` en pedidos mixtos modernos. | CERRADO |
| **F-18** | **ALTA** | `PedidoServicio.java` / `GeneradorPdfPedido.java` | Facturación bloqueada: `validarPedidoPersistible` tolera total sin IVA pero `GeneradorPdfPedido` lo rechaza en impresión. | CERRADO |
| **F-19** | **MEDIA** | `CierreCajaDao.java` | Descuadre en Bs en reportes de cierre: `sqlMetodos` y `sqlSalas` usan `COALESCE(p.total_bs)` omitiendo `NULLIF(p.total_bs, 0)`. | CERRADO |
| **F-20** | **MEDIA** | `PedidosDao.java` | Silenciamiento de pagos con método canónico `'EFECTIVO'`: no acumula `efectivo_bs` ni `efectivo_usd` persistiendo 0.00 en cabecera. | CERRADO |
| **F-21** | **CRÍTICA** | `CierreCajaDao.java` | Descuadre interno masivo en Cierre Z: `sqlMetodos`, `sqlSalas` y `sqlEfectivo` revalorizan pedidos históricos con `tasaConfig` mientras `sqlTotales` los calcula en cero. | CERRADO |
| **F-22** | **CRÍTICA** | `PanelAtencionMesasSplit.java` | Corrupción de catálogo y violación de clave foránea en edición de mesas: asigna erróneamente `d.getId()` como `id_plato`. | CERRADO |
| **F-23** | **ALTA** | `PanelAtencionMesasSplit.java` | Fuga económica por omisión de guardado: botón "Cobrar / Facturar" en mesas ocupadas ignora ítems recién agregados al carrito. | CERRADO |
| **F-24** | **ALTA** | `PreciosPedidosDao.java` | Reincidencia F-14: auto-congelado en actualización de catálogo fuerza defaults `USD` y gravado en líneas sin clasificar, inflando montos históricos en >4000%. | CERRADO |
| **F-25** | **ALTA** | `CierreCajaDao.java` / `PedidosDao.java` | Omisión contable y fiscal: Cierre Z excluye ventas nocturnas que cruzan la medianoche al filtrar por `fecha` de apertura en lugar de `cerrada_en`. | CERRADO |
| **F-26** | **MEDIA** | `CierreCajaDao.java` | Condición de carrera en `guardarCierre`: verificación `existeCierreZ` sin bloqueo transaccional permite persistir Cierres Z duplicados concurrentes. | CERRADO |
| **F-27** | **MEDIA** | `CalculoMonetarioPedido.java` | Descalce aritmético en `ajusteCambioBs`: computa con `totalUsdReferencia` mientras `totalUsd()` aditivo deriva un valor diferente. | CERRADO |
| **F-28** | **BAJA** | `LoginDao.java` | Canal lateral de tiempo (Timing Attack) en autenticación PBKDF2 permite enumeración remota de usuarios del sistema. | CERRADO |
| **F-29** | **CRÍTICA** | `actualizar_datos_cliente.bat` / `.sh` / `BD RESTAURANTE 2026.sql` | Destrucción irreversible de datos: Script de actualización ejecuta `DROP TABLE IF EXISTS` vaciando pedidos, cierres Z, auditoría y usuarios sin confirmación. | CERRADO |
| **F-30** | **CRÍTICA** | `BD RESTAURANTE 2026.sql` / `PlatosDao.java` | Corrupción de integridad referencial: `plato_categoria` inserta claves huérfanas apuntando a categorías inexistentes (IDs 9, 10, 11, 12, 14, 15). | CERRADO |
| **F-31** | **ALTA** | `PlatosDao.java` / `PanelAtencionMesasSplit.java` | Desincronización horaria (Timezone UTC vs Local): `asegurarCatalogoPlatosVigente()` con `CURRENT_DATE()` hace desaparecer el catálogo a partir de las 8:00 PM. | CERRADO |
| **F-32** | **ALTA** | `Sistema.java` | Violación de ciclo de vida Swing y concurrencia: el constructor dispara SwingWorkers de platos antes de inicializar columnas y modelos de tablas. | CERRADO |
| **F-33** | **ALTA** | `MigradorEsquemaJdbc.java` / `BD RESTAURANTE 2026.sql` | Omisión de `fecha_jornada_z` y `uq_cierres_caja_z_fecha` en migrador JDBC (v12): despliegues automáticos quedan expuestos a Cierres Z duplicados. | CERRADO |
| **F-34** | **ALTA** | `PreciosPedidosDao.java` vs `PanelAtencionMesasSplit.java` | Conflicto monetario cruzado en líneas huérfanas: Split POS fuerza `USD + IVA` mientras recálculo fuerza `VES sin IVA`, oscilando montos en 88,000%. | CERRADO |
| **F-35** | **MEDIA** | `Sistema.java` | Distorsión visual en grilla `tableFinalizar`: columna "Subtotal" computa e imprime el total con IVA incluido distorsionando el cálculo aritmético. | CERRADO |
| **F-36** | **MEDIA** | `CierreCajaDao.java` | Fuga de mesas cerradas pendientes de pago en turnos nocturnos: `sqlPendientes` evalúa `fecha` de apertura omitiendo `cerrada_en`. | CERRADO |
| **F-37** | **ALTA** | `GeneradorPdfPedido.java` | Asimetría tributaria y cambiaria en tickets y facturas PDF: precio unitario en USD incluye IVA mientras en Bs lo excluye para platos en VES. | CERRADO |
| **F-38** | **ALTA** | `GeneradorPdfPedido.java` / `CalculoMonetarioPedido.java` | Descuadre aditivo en PDFs: la suma de ítems de la tabla no cuadra con el Total Facturado en USD por divergencia entre `totalUsdReferencia` y `totalUsd()`. | CERRADO |
| **F-39** | **CRÍTICA** | `CierreCajaDao.java` / `CierreCaja.java` / `GeneradorPdfCierre.java` | Cierre Z en producción omite Base Imponible y Exento en BD, recurre a fallback inverso hardcodeado `16.00` y no persiste el desglose fiscal en `cierres_caja`. | CERRADO |
| **F-40** | **ALTA** | `Sistema.java` | Discrepancia visual severa en `tableFinalizar`: Columna "Precio" muestra precio bruto (con IVA) y columna "Sub-Total" muestra base neta (sin IVA) para platos con IVA incluido. | CERRADO |
| **F-41** | **ALTA** | `PreciosPedidosDao.java` | Pérdida de persistencia de `iva_porcentaje` en `detalle_pedidos` durante la propagación de catálogo, provocando `ErrorAplicacionException` al reabrir comandas. | CERRADO |
| **F-42** | **MEDIA** | `PanelAtencionMesasSplit.java` | Sobre-escritura fiscal no autorizada en comanda activa por desacople entre `detalle_pedidos` y el catálogo en `PanelAtencionMesasSplit`. | CERRADO |
| **F-43** | **BAJA** | `CalculoMonetarioPedido.java` | Desincronización en `ajusteCambioBs` entre líneas individuales (`CalculoLinea`) y la cabecera del pedido (`CalculoPedido`). | CERRADO |
| **F-44** | **MEDIA** | `GeneradorPdfPedido.java` | Emisión ciega de pre-factura sin validación fiscal previa permite imprimir comprobantes que luego son rechazados al facturar en caja. | CERRADO |
| **F-45** | **CRÍTICA** | `PedidosDao.java` (RT-01) | Corrupción de persistencia fiscal: `iva_bs` almacenado inflado al sumar el monto exento (`totalVes - baseVes` en vez de `ivaVes`), rompiendo la aditividad en BD (`total_bs != subtotal_bs + iva_bs`). | CERRADO |
| **F-46** | **CRÍTICA** | `GeneradorPdfPedido.java` / `Sistema.java` (RT-02) | Divergencia asimétrica pantalla vs PDF impreso: la pantalla recalculaba dinámicamente desde líneas en memoria mientras el generador de PDF leía pasivamente la cabecera corrupta de la BD (`pedido.getIvaBs()`), imprimiendo alícuotas infladas. | CERRADO |
| **F-47** | **CRÍTICA** | `CierreCajaDao.java` (RT-03) | Contaminación masiva del Cierre Z por arrastre de cabecera: `sqlTotales` ejecutaba `SUM(p.iva_bs)` directamente de la tabla `pedidos`, arrastrando montos espurios (+10,140 Bs. / +33% en arqueos diarios). | CERRADO |
| **F-48** | **ALTA** | `CierreCajaDao.java` (RT-04) | Inferencia fiscal inversa con alícuota hardcodeada `divide(16.00)` y `COALESCE(iva_porcentaje, 16.00)`: el Cierre Z falseaba bases imponibles ante alícuotas no estándar y provocaba descuadre entre `subtotal_linea` y `base + exento`. | CERRADO |
| **F-49** | **ALTA** | `PedidosDao.java` / `CalculoMonetarioPedido.java` (RT-05) | Asimetría cambiaria en líneas multimoneda: líneas registradas con moneda 'VES' eran multiplicadas por la tasa como si fueran 'USD', provocando descalce entre el subtotal guardado y la sumatoria `Base + Exento`. | CERRADO |
| **F-50** | **MEDIA** | `Sistema.java` (RT-06) | Fallback heurístico inverso en pantalla (`iva * 100 / ivaPct`): ante comandas sin cálculo clasificado en caliente, la interfaz gráfica reconstruía la base imponible mediante cálculo inverso aproximado, induciendo discrepancias visuales. | CERRADO |
| **F-51** | **MEDIA** | `ClienteDao.java` (RT-07) | Distorsión analítica y descuadre entre Top Platos y Ventas Totales en Dashboard: `sqlTopPlatos` sumaba importes netos sin redondear a 2 decimales y dividía indiscriminadamente entre tasa para líneas VES, desalineándose del KPI bruto. | CERRADO |
| **F-52** | **BAJA** | `PedidosDao.java` / `PreciosPedidosDao.java` (RT-08) | Ambigüedad de metadatos fiscales: líneas exentas persistidas con `aplica_iva = 0` pero reteniendo residualmente `iva_porcentaje = 16.00` en `detalle_pedidos`, dejando abierta la puerta a interpretaciones erróneas por agrupaciones SQL con `COALESCE`. | CERRADO |
| **F-53** | **ALTA** | `Sistema.java` (RT-09) | Fuga y persistencia involuntaria de datos del cliente en cierre de factura: retención en memoria en `txtClienteNombreFac` y `txtClienteDocFac` afecta a todas las mesas siguientes. | CERRADO |
| **F-54** | **MEDIA** | `Sistema.java` (RT-10) | Impresión ciega y automática de comanda de cocina sin confirmación operativa al ordenar bebidas o ítems simples, provocando gasto de papel térmico. | CERRADO |
| **F-55** | **MEDIA** | `ClienteDao.java` / `Sistema.java` (RT-11) | Ausencia de métricas de pagos agrupados por método en Dashboard y grilla de facturas de clientes vacía al ingresar. | CERRADO |
| **F-56** | **MEDIA** | `Sistema.java` (RT-12) | Cierre abrupto de pantalla de cobro sin resumen ni confirmación visual de métodos de pago y cambio entregado al cajero. | CERRADO |
| **F-57** | **CRÍTICA** | `PedidosDao.java` / `Sistema.java` (RT-13) | Bloqueo fatal en anulación de facturas finalizadas por exigencia de contra-asientos inexistentes en `pedido_saldos_movimientos`. | CERRADO |
| **F-58** | **ALTA** | `Sistema.java` (RT-14) | Pedido rápido huérfano y bloqueado en `PENDIENTE` sin mesa física asignada al pulsar Pre-Factura o salir de pantalla, sin posibilidad de cobro desde el historial. | CERRADO |

---

### DETALLE EXHAUSTIVO DE HALLAZGOS

---

#### Fallo 1: Bloqueo fatal del arranque de la aplicación en BDs existentes por endurecimiento de `aplica_iva NOT NULL`
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/infraestructura/MigradorEsquemaJdbc.java`: Líneas 248-258, 468-478, 810-838.
  - `actualizar_bd.sql`: Líneas 438-450, 556-573.
- **Código Problemático:**
  ```java
  // src/infraestructura/MigradorEsquemaJdbc.java (líneas 468-472)
  asegurarColumna(con, meta, catalogo, "detalle_pedidos", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");
  asegurarColumnaNoNula(con, "detalle_pedidos", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");
  ...
  // src/infraestructura/MigradorEsquemaJdbc.java (líneas 829-835)
  try (Statement st = con.createStatement();
       ResultSet rs = st.executeQuery("SELECT 1 FROM `" + tabla + "` WHERE `" + columna + "` IS NULL LIMIT 1")) {
      if (rs.next()) {
          throw new SQLException("No se puede convertir a NOT NULL: " + tabla + "." + columna
                  + " contiene NULL. Clasifique esos registros antes de actualizar.");
      }
  }
  ```
  ```sql
  -- actualizar_bd.sql (líneas 567-571)
  IF EXISTS (SELECT 1 FROM detalle_pedidos WHERE aplica_iva IS NULL) THEN
      SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'detalle_pedidos.aplica_iva contiene NULL: clasifique esas líneas antes de actualizar.';
  END IF;
  ```
- **Demostración del Escenario de Falla:**
  1. En versiones previas del sistema, los registros históricos de `detalle_pedidos` tenían `aplica_iva = NULL` para denotar comanda histórica cuyo tratamiento fiscal de IVA no fue clasificado en el catálogo original.
  2. Al desplegar esta versión sobre una base de datos de producción existente con comandas previas y arrancar el JAR (`iniciar_restaurante.sh`), el inicializador invoca `MigradorEsquemaJdbc.migrarSiEsNecesario`.
  3. `asegurarColumnaNoNula` encuentra al menos un registro en `detalle_pedidos` donde `aplica_iva IS NULL`.
  4. La migración lanza inmediatamente un `SQLException: No se puede convertir a NOT NULL: detalle_pedidos.aplica_iva contiene NULL. Clasifique esos registros antes de actualizar.`.
  5. El sistema no provee ninguna pantalla, comando ni script para clasificar miles de líneas históricas. La aplicación aborta el arranque y el restaurante queda inoperable.
- **Impacto Real:** Disponibilidad total (Denegación de servicio inmediata al actualizar producción).
- **Recomendación Técnica:**
  Preservar la nulabilidad de la columna `aplica_iva` para registros históricos en `detalle_pedidos`. Si se desea asegurar que los nuevos registros posean valor, definir `TINYINT(1) NULL DEFAULT 1` o aplicar la restricción `CHECK (aplica_iva IS NULL OR aplica_iva IN (0,1))` como estaba previamente, permitiendo que el historial permanezca inmutable sin detonar excepciones en el arranque.

---

#### Fallo 2: "Auto-congelado fantasma" en detalles legados con columnas fiscales huérfanas y NPE en catálogo
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/PreciosPedidosDao.java`: Líneas 301, 312-320, 358-370.
- **Código Problemático:**
  ```java
  // src/Modelo/PreciosPedidosDao.java: Línea 301
  ps.setInt(4, det.getAplicaIva() ? 1 : 0); // <-- Unboxing directo de Boolean que puede ser NULL!
  ...
  // src/Modelo/PreciosPedidosDao.java: Líneas 358-370
  // Si no existe en el catálogo, congelar su precio para preservar su valor histórico y permitir la actualización
  det.setPrecioCongelado(true);
  String sqlCong = "UPDATE detalle_pedidos SET precio_congelado = 1, congelado_motivo = 'Auto-congelado por ítem legado sin clasificación en catálogo' WHERE id = ?";
  try (PreparedStatement psCong = con.prepareStatement(sqlCong)) {
      psCong.setInt(1, det.getId());
      psCong.executeUpdate();
  }
  if (det.getMonedaBase() == null) det.setMonedaBase("USD");
  if (det.getPrecioIncluyeIva() == null) det.setPrecioIncluyeIva(false);
  if (det.getAplicaIva() == null) det.setAplicaIva(true);
  ```
- **Demostración del Escenario de Falla:**
  1. **NPE en Línea 301**: Si un detalle entra a actualizarse y `det.getAplicaIva()` es `null`, la expresión ternaria `det.getAplicaIva() ? 1 : 0` fuerza un auto-unboxing de un wrapper nulo, arrojando `NullPointerException` y haciendo fallar toda la transacción de modificación de catálogo.
  2. **Persistencia Incompleta en BD ("Auto-congelado fantasma")**: Cuando `det.getIdPlato()` es nulo y el plato fue renombrado en `platos`, la consulta por nombre no lo encuentra. Entra al bloque `else` (línea 358).
  3. En memoria ejecuta: `det.setMonedaBase("USD"); det.setPrecioIncluyeIva(false); det.setAplicaIva(true);`.
  4. Sin embargo, en base de datos **ÚNICAMENTE** se ejecuta: `UPDATE detalle_pedidos SET precio_congelado = 1 ... WHERE id = ?`. Las columnas `moneda_base`, `precio_incluye_iva`, `aplica_iva` e `iva_porcentaje` **permanecen como `NULL` en la base de datos**.
  5. Cuando la comanda se abre en el carrito, se intenta finalizar en caja o se genera su PDF, los métodos de validación (`PedidosDao.consultarPedido`, `CalculoMonetarioPedido.validarMoneda`, `GeneradorPdfPedido`) leen la fila desde la BD, detectan `moneda_base == null` o `precio_incluye_iva == null` y arrojan `ErrorAplicacionException: El producto no tiene moneda base asignada`. La mesa queda bloqueada y no se puede cobrar ni cerrar.
- **Impacto Real:** Integridad contable y estabilidad operativa: excepciones no controladas y comandas que quedan corruptas de forma irreversible en la BD.
- **Recomendación Técnica:**
  1. En línea 301, usar `Boolean.TRUE.equals(det.getAplicaIva()) ? 1 : 0`.
  2. En el bloque de auto-congelado, persistir en BD las columnas inferidas:
     `UPDATE detalle_pedidos SET precio_congelado = 1, moneda_base = 'USD', precio_incluye_iva = 0, aplica_iva = 1, iva_porcentaje = ? WHERE id = ?`.

---

#### Fallo 3: Destrucción del desglose de métodos de pago en Reportes X y Z
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 166-191.
- **Código Problemático:**
  ```java
  // src/Modelo/CierreCajaDao.java (líneas 166-175)
  String sqlMetodos = """
      SELECT COALESCE(NULLIF(TRIM(p.metodo_pago), ''), 'EFECTIVO') AS metodo,
             COUNT(*) AS cant,
             COALESCE(SUM(p.total), 0) AS total_usd,
             COALESCE(SUM(COALESCE(p.total_bs, ROUND(p.total * COALESCE(NULLIF(p.tasa_cambio, 0), ?), 2), 0)), 0) AS total_bs
      FROM pedidos p
      WHERE p.estado = 'FINALIZADO' AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
      GROUP BY COALESCE(NULLIF(TRIM(p.metodo_pago), ''), 'EFECTIVO')
      ORDER BY total_usd DESC
      """;
  ```
- **Demostración del Escenario de Falla:**
  1. Un cliente consume $100 y paga de forma mixta: $60 en Efectivo USD y $40 en Tarjeta de Débito (Punto de Venta) en Bolívares.
  2. El sistema almacena en `pedidos` el texto `metodo_pago = 'MIXTO'`, y en `pedido_pagos` inserta dos registros: uno por `EFECTIVO_USD` ($60) y otro por `TARJETA` ($40).
  3. Al emitir el Cierre Z o X, `sqlMetodos` consulta directamente `pedidos.metodo_pago`.
  4. El reporte imprime: `MIXTO | Cant: 1 | Total: $100.00 | Total Bs: Bs. 3,650.00`.
  5. Los métodos reales (`TARJETA`, `PAGO_MOVIL`, `TRANSFERENCIA`) aparecen en cero o no aparecen.
  6. El cajero y el gerente son incapaces de cuadrar el lote de los puntos de venta bancarios con el cierre de caja.
  7. Además, la sección "Arqueo de Efectivo" (que sí lee `pedido_pagos` en `sqlEfectivo`) reporta $60 en efectivo esperado, contradiciendo abiertamente la tabla de "Desglose por Formas de Pago" donde el efectivo aparece en $0.
- **Impacto Real:** Pérdida de control interno de caja, imposibilidad de conciliación bancaria y distorsión grave de auditoría.
- **Recomendación Técnica:**
  Restaurar la consulta basada en `pedido_pagos` agrupando por `pp.metodo_pago`, unida con `UNION ALL` a los pedidos legados que carezcan de desglose en `pedido_pagos`.

---

#### Fallo 4: Subtotales e IVA en Bolívares en cero para pedidos legados en el cierre de caja
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 106-110, 410-423.
- **Código Problemático:**
  ```sql
  -- src/Modelo/CierreCajaDao.java (líneas 106-110)
  COALESCE(SUM(COALESCE(total_bs, ROUND(total * COALESCE(NULLIF(tasa_cambio, 0), ?), 2), 0)), 0) AS total_bs,
  COALESCE(SUM(subtotal), 0) AS subtotal_usd,
  COALESCE(SUM(subtotal_bs), 0) AS subtotal_bs,
  COALESCE(SUM(iva_monto), 0) AS iva_usd,
  COALESCE(SUM(iva_bs), 0) AS iva_bs
  ```
  ```java
  // src/Modelo/CierreCajaDao.java (líneas 411-415 en calcularDesdeMemoria)
  BigDecimal b = p.getTotalBs() != null ? p.getTotalBs() : u.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
  BigDecimal su = p.getSubtotal() != null ? p.getSubtotal() : u;
  BigDecimal sb = p.getSubtotalBs() != null ? p.getSubtotalBs() : b; // <-- Asigna el TOTAL como subtotal si subtotalBs es null!
  BigDecimal iu = p.getIvaMonto() != null ? p.getIvaMonto() : BigDecimal.ZERO;
  BigDecimal ib = p.getIvaBs() != null ? p.getIvaBs() : BigDecimal.ZERO;
  ```
- **Demostración del Escenario de Falla:**
  1. En `sqlTotales`, si una comanda finalizada es legada y tiene `subtotal_bs` o `iva_bs` como `NULL`: `total_bs` aplica fallback con `tasaConfig`, pero `subtotal_bs` e `iva_bs` entregan `0.00`.
  2. En el Reporte Z de la jornada:
     - `Subtotal Bs: Bs. 0.00`
     - `IVA Bs: Bs. 0.00`
     - `Total Ventas Bs: Bs. 3,650.00`
  3. En `calcularDesdeMemoria`, si `subtotalBs` es null, toma `b` (el total con IVA). Al acumular `subBs` + `ivBs`, el subtotal ya contiene el IVA, duplicando el importe impositivo al presentar el informe.
- **Impacto Real:** Descuadre contable y tributario manifiesto en reportes fiscales exigibles por la autoridad tributaria.
- **Recomendación Técnica:**
  En `sqlTotales`, aplicar el mismo recálculo referencial a `subtotal_bs` e `iva_bs`:
  `COALESCE(SUM(COALESCE(subtotal_bs, ROUND(subtotal * COALESCE(NULLIF(tasa_cambio, 0), ?), 2), 0)), 0) AS subtotal_bs`.

---

#### Fallo 5: Reintroducción de Fallback Silencioso Hardcodeado `36.5000` en Nivel 5 de `GeneradorPdfPedido`
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Servicio/GeneradorPdfPedido.java`: Líneas 944-954.
- **Código Problemático:**
  ```java
  // src/Servicio/GeneradorPdfPedido.java (líneas 944-951)
  // Nivel 5: Para emisión de pedidos no históricos sin tasa configurada (bootstrap o UI/display),
  // registrar advertencia y utilizar tasa referencial 36.5000
  if (!esHistorico) {
      LOGGER.warning("obtenerTasa: No se encontró tasa configurada; utilizando fallback referencial 36.5000 para emisión de documento.");
      return new BigDecimal("36.5000");
  }
  ```
- **Demostración del Escenario de Falla:**
  1. En una instalación nueva o cuando la tasa oficial del día fue reseteada/eliminada, se cobra o emite un pedido nuevo.
  2. En lugar de detener la operación para exigir la configuración de la tasa oficial vigente según el BCV, el generador de PDF intercepta la condición y genera facturas con una tasa inventada y fija de `36.5000`.
  3. El cliente y el negocio reciben documentos con cálculos monetarios desconectados de la realidad económica. Esto reintroduce el fallback silencioso que había sido expresamente vetado en las auditorías iniciales de integridad fiscal.
- **Impacto Real:** Fraude involuntario, emisión de documentos mercantiles y fiscales con valores monetarios no autorizados.
- **Recomendación Técnica:**
  Eliminar el bloque de Nivel 5 y permitir que se propague `ErrorAplicacionException.validacion("No se puede generar la factura: la tasa de cambio no ha sido configurada en el sistema.")`.

---

#### Fallo 6: Tolerancia laxa de 5 centavos (`0.05`) oculta desajustes contables y discrepancia entre líneas impresas y totales
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Servicio/GeneradorPdfPedido.java`: Líneas 261, 290-316, 579-584, 807-814.
- **Código Problemático:**
  ```java
  // src/Servicio/GeneradorPdfPedido.java (línea 261)
  BigDecimal tol = new BigDecimal("0.05");
  ...
  // Líneas 307-316
  if (pedido.getSubtotal() != null && pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP).subtract(calc.subtotalUsd()).abs().compareTo(tol) > 0) {
      throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
  }
  ...
  // Líneas 579-584 y 807-814
  if (exentoBs.compareTo(BigDecimal.ZERO) == 0) {
      baseImpUsd = subtotalUsd;
      exentoUsd = BigDecimal.ZERO.setScale(2);
  } else if (baseImpUsd.add(exentoUsd).compareTo(subtotalUsd) != 0) {
      baseImpUsd = subtotalUsd.subtract(exentoUsd).max(BigDecimal.ZERO);
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Al ampliar la tolerancia a $0.05 (5 centavos USD), un pedido puede presentar una diferencia de hasta cinco centavos entre la suma real de sus platos y el total almacenado en cabecera sin que `validar()` genere objeción.
  2. En el ticket impreso se listan las líneas de los platos con sus cantidades y subtotales reales (suma = $10.00), mientras que el "Total a Pagar" imprime el valor de la cabecera `pedidos.total` ($10.05).
  3. La suma aritmética de las líneas de la comanda impresa no coincide con el total impreso en el ticket.
  4. Asimismo, el parche en líneas 582 y 812 fuerza `baseImpUsd = subtotalUsd.subtract(exentoUsd)` para cuadrar la suma en USD a la fuerza, pero **no recalcula ni sincroniza `baseImpBs`**, ocasionando que el equivalente en Bs. mostrado entre paréntesis `(Bs. ...)` no coincida con el valor en USD multiplicado por la tasa de cambio.
- **Impacto Real:** Distorsión contable, quejas de clientes por cobros inconsistentes y sanciones ante auditorías tributarias.
- **Recomendación Técnica:**
  Restablecer la tolerancia máxima de validación a `0.01` (1 centavo estrictamente ligado a redondeo de medio punto) y resolver la causa raíz en `CalculoMonetarioPedido` manteniendo la suma consistente en moneda base y contravalor.

---

#### Fallo 7: Bloqueo absoluto de anulación de pedidos con pagos (`anularPedidoConAuditoria`)
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 1255-1274, 1578-1586.
- **Código Problemático:**
  ```java
  // src/Modelo/PedidosDao.java (líneas 1260-1272)
  if (rs.next() && rs.getInt(1) > 0) {
      try (PreparedStatement psMov = con.prepareStatement(
              "SELECT COUNT(*) FROM pedido_saldos_movimientos WHERE id_pedido = ? AND tipo IN ('DEVOLUCION','CREDITO','ANULACION')")) {
          psMov.setInt(1, id_pedido);
          try (ResultSet rsMov = psMov.executeQuery()) {
              int cantMov = rsMov.next() ? rsMov.getInt(1) : 0;
              if (cantMov == 0) {
                  throw ErrorAplicacionException.validacion(
                          "El pedido #" + id_pedido + " tiene pagos registrados; se requiere conciliación o devolución trazable antes de anular.");
              }
          }
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Un cliente realiza un pago y luego solicita la anulación de la comanda (por ejemplo, producto en mal estado o equivocación de mesa).
  2. El supervisor presiona "Anular Pedido" en el historial o comanda.
  3. `anularPedidoConAuditoria` verifica si existen filas en `pedido_pagos`. Como existen, exige que exista al menos un movimiento compensatorio en `pedido_saldos_movimientos`.
  4. Sin embargo, en el código fuente de la aplicación (`src/`), **NO EXISTE NINGÚN FLUJO DE USUARIO NI PANTALLA QUE INSERTE EN `pedido_saldos_movimientos`**. `MovimientoSaldoPedidoDao.registrarMovimiento` no es llamado en ninguna parte del código de producción.
  5. En consecuencia, la excepción `ErrorAplicacionException` se lanza invariablemente: es humanamente imposible anular cualquier pedido que tenga pagos desde la aplicación.
  6. Si se inserta artificialmente un registro en BD para eludir la traba, solo valida `COUNT(*) > 0` sin contrastar importes (un registro por 0.01 Bs permite anular un pedido de $1,000).
  7. Los registros en `pedido_pagos` quedan intactos en BD sin bandera de anulación, permaneciendo como registros de cobranza huérfanos.
- **Impacto Real:** Bloqueo operativo en sala y caja, imposibilitando anulaciones legítimas y devoluciones.
- **Recomendación Técnica:**
  En `anularPedidoConAuditoria`, ejecutar en la misma transacción la inserción automática del movimiento compensatorio en `pedido_saldos_movimientos` por el importe total pagado, y marcar los pagos en `pedido_pagos` como anulados o revertidos.

---

#### Fallo 8: Falsa absorción de deudas en `resumenConciliacion`: comanda en pre-cuenta es declarada solvente
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 1572, 1665-1705.
- **Código Problemático:**
  ```java
  // src/Modelo/PedidosDao.java (línea 1572)
  cerrado = rs.getTimestamp("cerrada_en") != null;
  ...
  // Líneas 1688-1704
  if (cerrado) {
      BigDecimal tolBs = new BigDecimal("0.05");
      if (tasa != null && tasa.signum() > 0) {
          BigDecimal tolCalculada = new BigDecimal("0.005").multiply(tasa).setScale(2, RoundingMode.HALF_UP);
          if (tolCalculada.compareTo(tolBs) > 0) {
              tolBs = tolCalculada;
          }
      }
      if (saldoBs.compareTo(tolBs) <= 0) {
          saldoBs = BigDecimal.ZERO.setScale(2);
          saldoUsd = BigDecimal.ZERO.setScale(2);
      }
      if (excedenteBs.compareTo(tolBs) <= 0) {
          excedenteBs = BigDecimal.ZERO.setScale(2);
          excedenteUsd = BigDecimal.ZERO.setScale(2);
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. En el ciclo de vida del restaurante, cuando una mesa pide la cuenta (pre-cuenta), el sistema registra `cerrada_en = CURRENT_TIMESTAMP`, pero el pedido sigue en estado `PENDIENTE` (la comanda aún no ha sido pagada).
  2. `resumenConciliacion` evalúa `cerrado = cerrada_en != null`, confundiéndolo con un pedido finalizado/liquidado.
  3. Si la mesa tiene un saldo pendiente menor o igual a `tolBs` (con una tasa de 100 Bs/$, `tolBs` es 0.50 Bs), el método ejecuta `saldoBs = 0.00` y `saldoUsd = 0.00`.
  4. La pantalla y los reportes de conciliación indican que la mesa no debe nada (`saldo = 0`), cuando en realidad el cliente no ha pagado.
  5. Igualmente, si el cliente entregó un exceso menor o igual a `tolBs`, `excedenteBs` es puesto en 0, privando al cliente de su cambio/vuelto legítimo.
  6. Además, en la línea 1668, consultar `pedido_liquidaciones` cuando el pedido aún no está finalizado puede reutilizar ajustes cambiarios de liquidaciones previas no consolidadas.
- **Impacto Real:** Pérdidas económicas por comandas que aparentan estar pagadas y retención indebida de vueltos.
- **Recomendación Técnica:**
  Condicionar la tolerancia y el ajuste de liquidación exclusivamente a pedidos cuyo estado sea `FINALIZADO` (`"FINALIZADO".equalsIgnoreCase(estado)`) y que efectivamente cuenten con abonos aplicados (`pagadoBsAplicado.compareTo(BigDecimal.ZERO) > 0`).

---

#### Fallo 9: Condición de carrera en UI de carrito: pérdida de datos y secuestro de cantidades en `txtCantidadManual`
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 2812-2844, 4456-4479, 4595-4610.
- **Código Problemático:**
  ```java
  // src/Vista/Sistema.java (líneas 2829-2843 en focusLost de txtCantidadManual)
  @Override
  public void focusLost(java.awt.event.FocusEvent evt) {
      int filaObjetivo = -1;
      if (platoIdFoco != null) {
          for (int i = 0; i < tableMenu.getRowCount(); i++) {
              if (platoIdFoco.equals(tableMenu.getModel().getValueAt(i, 0))) {
                  filaObjetivo = i;
                  break;
              }
          }
      }
      if (filaObjetivo != -1) {
          String texto = txtCantidadManual.getText();
          aplicarCantidadManual(texto != null && !texto.trim().isEmpty() ? texto.trim() : ultimoTextoIngresado, filaObjetivo);
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. El operador tiene seleccionada la Fila 0 ("Hamburguesa", cantidad 1).
  2. Hace foco en `txtCantidadManual` y tipea "4".
  3. Sin presionar Enter, hace clic en la Fila 1 ("Refresco", cantidad 2) de `tableMenu`.
  4. El clic en la tabla cambia la selección a la Fila 1 antes de que se despache `focusLost`.
  5. El `ListSelectionListener` de `tableMenu` dispara `actualizarEstadoBotonesCarrito()`, el cual ejecuta:
     `txtCantidadManual.setText(val != null ? val.toString() : "1");`
     Esto reemplaza de inmediato el "4" en pantalla con el "2" de la Fila 1.
  6. Inmediatamente después, se ejecuta `focusLost`. Lee `txtCantidadManual.getText()`, que ahora contiene "2".
  7. Invoca `aplicarCantidadManual("2", 0)`, asignando la cantidad 2 a la Hamburguesa.
  8. El "4" que ingresó el operador se perdió por completo y la Fila 0 fue sobreescrita con la cantidad de la Fila 1.
  9. **Comportamiento con Cero / Negativos**: Si el usuario escribe `0` o `-1`, `aplicarCantidadManual` muestra una alerta pero **no revierte el texto de `txtCantidadManual`**. Si acto seguido hace clic en "Cobrar", `focusLost` repite el ciclo y la tabla retiene la cantidad previa mientras el campo muestra un valor contradictorio.
- **Impacto Real:** Error humano inducido por UI en la toma de pedidos rápida; alteración de comandas en servicio activo.
- **Recomendación Técnica:**
  En `focusLost`, verificar si `txtCantidadManual` fue modificado programáticamente durante el cambio de selección y utilizar `ultimoTextoIngresado` obtenido antes de la pérdida de foco. Si `nuevaCant < 1`, revertir de inmediato el texto de `txtCantidadManual` a la cantidad válida de la fila.

---

#### Fallo 10: `NullPointerException` por auto-unboxing inseguro de `Boolean aplicaIva`
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Modelo/DetallePedido.java`: Líneas 118-125.
  - `src/Servicio/CalculoMonetarioPedido.java`: Línea 139.
  - `src/Servicio/PedidoServicio.java`: Línea 82.
- **Código Problemático:**
  ```java
  // src/Servicio/PedidoServicio.java: Línea 82
  detalle.setIvaPorcentaje(detalle.getAplicaIva() // <-- Unboxing de Boolean null!
          ? pedido.getIvaPorcentaje()
          : BigDecimal.ZERO.setScale(2));

  // src/Servicio/CalculoMonetarioPedido.java: Línea 139
  CalculoLinea linea = calcularLinea(
          detalle.getPrecioDecimal(),
          detalle.getCantidad(),
          detalle.getMonedaBase(),
          detalle.getAplicaIva(), // <-- calcularLinea recibe boolean primitivo!
          ...);
  ```
- **Demostración del Escenario de Falla:**
  1. En las recientes modificaciones, se eliminó la validación defensiva `if (detalle.getAplicaIva() == null) throw ...`.
  2. Se asumió que `detalle.getAplicaIva()` siempre retornaría un booleano, ignorando que el getter retorna `Boolean` (objeto wrapper).
  3. Si un detalle proviene de una deserialización o no se le seteó la propiedad, `detalle.getAplicaIva()` devuelve `null`.
  4. Al evaluar la expresión ternaria en `PedidoServicio` o llamar a `calcularLinea` (cuyo 4to parámetro es `boolean`), la JVM intenta ejecutar `Boolean.booleanValue()`, detonando un `NullPointerException` imprevisto.
- **Impacto Real:** Inestabilidad del runtime y fallas no controladas (HTTP 500 / Crash Swing) en lugar de errores de validación legibles.
- **Recomendación Técnica:**
  Utilizar `detalle.isAplicaIva()` (que implementa `Boolean.TRUE.equals(aplicaIva)`) o restituir la verificación explícita de `null` previa al cálculo.

---

#### Fallo 11: Distorsión monetaria en ranking Top 5 de platos de Cierre de Caja
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 316-320, 490-505.
- **Código Problemático:**
  ```sql
  -- src/Modelo/CierreCajaDao.java (líneas 316-320 en sqlTopPlatos)
  COALESCE(SUM(CASE
      WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES'
          THEN (d.precio * d.cantidad)
      ELSE (d.precio * d.cantidad) * COALESCE(p.tasa_cambio, 1) -- <-- Multiplica USD por 1 para obtener Bs!
  END), 0) AS total_bs
  ```
  ```java
  // src/Modelo/CierreCajaDao.java (líneas 490-496 en calcularDesdeMemoria)
  BigDecimal pr = d.getPrecioDecimal() != null ? d.getPrecioDecimal() : BigDecimal.ZERO;
  BigDecimal sub = pr.multiply(BigDecimal.valueOf(cant));
  AcumuladorPlato ap = platosMap.computeIfAbsent(nom, k -> new AcumuladorPlato());
  ap.cant += cant;
  ap.total = ap.total.add(sub); // <-- Suma nominalmente sin distinguir USD de VES!
  ```
- **Demostración del Escenario de Falla:**
  1. **En SQL (`sqlTopPlatos`)**: Para pedidos legados sin `p.tasa_cambio` (nula o 0), `COALESCE(p.tasa_cambio, 1)` multiplica el precio en USD por 1 para obtener `total_bs`. Un plato de $15 USD se reporta como 15 Bs en vez de su valor a tasa oficial (~547.50 Bs).
  2. **En Memoria (`calcularDesdeMemoria`)**: Si el menú contiene platos configurados en `VES` (ej. Arepa a 250 Bs) y platos en `USD` (ej. Carne a $15 USD), el código suma directamente `250 + 15 = 265`, tratando a la Arepa como si costara $250 USD. El ranking de los más vendidos y su facturación queda completamente falseado.
- **Impacto Real:** Información gerencial errónea para toma de decisiones y reportes de rentabilidad distorsionados.
- **Recomendación Técnica:**
  En `sqlTopPlatos`, utilizar la tasa del sistema `tasaConfig` como fallback cuando `p.tasa_cambio` sea nula. En `calcularDesdeMemoria`, convertir a USD o VES mediante la tasa antes de acumular en `ap.total`.

---

#### Fallo 12: Silenciamiento de errores y código muerto en persistencia de cierres de caja
- **Severidad:** **BAJA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 527-541, 566-569.
- **Código Problemático:**
  ```java
  // src/Modelo/CierreCajaDao.java (líneas 527-541)
  public boolean guardarCierre(CierreCaja cierre, String rutaPdf) {
      if (cierre == null) {
          return false; // Línea 528
      }
      ...
      if (cierre == null) { // Línea 539: Código muerto inalcanzable
          throw ErrorAplicacionException.validacion("El objeto de cierre de caja no puede ser nulo.");
      }
  ...
  // Líneas 566-569
  } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Error al registrar cierre de caja en base de datos: " + ex.getMessage());
      return false; // Silencia el fallo en lugar de alertar al usuario
  }
  ```
- **Demostración del Escenario de Falla:**
  1. La validación defensiva en línea 539 jamás se ejecuta.
  2. Si la base de datos rechaza la inserción del cierre (por ejemplo, disco lleno o violación de clave), el método captura la excepción, escribe un log inadvertido y retorna `false`. El llamador no recibe la causa raíz del fallo y la auditoría del cierre se pierde sin trazabilidad.
  3. Además, los desgloses por método, sala y usuario nunca se persisten en la tabla `cierres_caja` (solo se guardan escalares), haciendo imposible reconstruir cierres históricos desde la BD si se pierde el PDF.
- **Impacto Real:** Pérdida de auditoría histórica e incapacidad de diagnosticar fallos en persistencia de arqueos.
- **Recomendación Técnica:**
  Eliminar el bloque inalcanzable, propagar una excepción de infraestructura (`DataAccessException`) para que la UI informe al cajero si el cierre no pudo registrarse, y considerar guardar el desglose detallado en formato JSON en `cierres_caja`.

---

#### Fallo 13: Evasión de detección de nulabilidad en lectura de `aplica_iva` en `PreciosPedidosDao`
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/PreciosPedidosDao.java`: Línea 270, 310-315, 351.
- **Código Problemático:**
  ```java
  // src/Modelo/PreciosPedidosDao.java (línea 270)
  det.setAplicaIva(rs.getBoolean("aplica_iva"));
  ...
  // Comparar con línea 273 (donde sí se verifica wasNull):
  int incIva = rs.getInt("precio_incluye_iva");
  det.setPrecioIncluyeIva(rs.wasNull() ? null : (incIva != 0));
  ```
- **Demostración del Escenario de Falla:**
  1. En una base de datos con líneas de comandas históricas donde `aplica_iva IS NULL`, se ejecuta `modificarProductoYRecalcularPendientes` o recálculo de catálogo.
  2. Al leer el cursor en la línea 270, `rs.getBoolean("aplica_iva")` retorna el tipo primitivo `false` cuando el valor subyacente en SQL es `NULL`.
  3. No se comprueba `rs.wasNull()`. Por lo tanto, `det.setAplicaIva(false)` almacena en memoria el objeto `Boolean.FALSE` en lugar de `null`.
  4. En la línea 310 (`if (det.getAplicaIva() == null)`), la condición evalúa a `false`, evadiendo por completo la rutina de recuperación/auto-sanado desde el catálogo.
  5. En la línea 351, el sistema asume que el ítem fue clasificado expresamente como exento (`aplica_iva = 0`) y persiste `0` silenciosamente en base de datos.
  6. Un plato gravado histórico queda mutado de forma irrevocable a producto exento de impuestos sin intervención ni confirmación del operador.
- **Impacto Real:** Corrupción tributaria y alteración silenciosa de datos históricos en base de datos fiscal.
- **Recomendación Técnica:**
  Leer el campo respetando la nulabilidad con `rs.wasNull()`:
  ```java
  boolean apl = rs.getBoolean("aplica_iva");
  det.setAplicaIva(rs.wasNull() ? null : apl);
  ```

---

#### Fallo 14: Auto-congelado inventa defaults prohibidos violando la regla de dominio e inflando comandas más del 4000%
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/PreciosPedidosDao.java`: Líneas 358-372.
- **Código Problemático:**
  ```java
  // src/Modelo/PreciosPedidosDao.java (líneas 359-370)
  det.setPrecioCongelado(true);
  if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) {
      det.setMonedaBase("USD");
  }
  if (det.getPrecioIncluyeIva() == null) {
      det.setPrecioIncluyeIva(false);
  }
  if (det.getAplicaIva() == null) {
      det.setAplicaIva(true);
  }
  BigDecimal ivaDet = Boolean.TRUE.equals(det.getAplicaIva()) ? ivaPorcentajeCabecera : BigDecimal.ZERO.setScale(2);
  det.setIvaPorcentaje(ivaDet);
  ```
- **Demostración del Escenario de Falla:**
  1. La regla arquitectónica y directriz del proyecto estipula expresamente: *"Prohibir default en escritura / cálculo autoritativo: tasa <= 0 / null -> 36.5000, moneda null -> USD, incluye IVA null -> false, aplica null -> true, iva% null -> 16.00... Debe ser: throw ErrorAplicacionException.validacion(...) exigiendo clasifique o congele"*.
  2. En `PreciosPedidosDao.java:361-369`, si un detalle legado no se encuentra en el catálogo actual (por ejemplo, producto discontinuado o renombrado), el código inventa de facto los 3 defaults vetados: moneda `"USD"`, `precioIncluyeIva = false` y `aplicaIva = true`.
  3. Supóngase un plato legado que se vendió en Bolívares por un importe de `100.00 VES` con IVA incluido. Al correr esta rutina, se le asigna `monedaBase = "USD"`, `precio = 100.00` y `precioIncluyeIva = false`.
  4. Al recalcular el pedido con una tasa de 36.50 Bs/$, el plato pasa de valer 100 Bs a valer $100 USD + IVA 16% = $116 USD, equivalente a **Bs. 4,234.00**.
  5. Se produce una inflación artificial del precio de más del **4134%**, alterando totalmente las cuentas por cobrar y el historial contable de la mesa.
- **Impacto Real:** Fraude involuntario, distorsión monetaria extrema e incumplimiento de las directrices fiscales autoritativas.
- **Recomendación Técnica:**
  Eliminar los defaults silenciosos. Si un ítem no catalogado requiere intervención, arrojar una excepción descriptiva `ErrorAplicacionException.validacion("Detalle #" + det.getId() + " carece de clasificación fiscal y no existe en catálogo; congele o clasifique manualmente antes de recalcular.")` o requerir confirmación explícita de clasificación.

---

#### Fallo 15: `MysqlDataTruncation: Incorrect datetime value: ''` bloquea la persistencia de cierres de caja en MySQL estricto
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Línea 635.
  - `src/Modelo/CierreCaja.java`: Línea 150.
- **Código Problemático:**
  ```java
  // src/Modelo/CierreCaja.java (línea 150)
  private String fechaHoraEmision = ""; // <-- Inicializado en cadena vacía!
  ...
  // src/Modelo/CierreCajaDao.java (líneas 623, 635)
  // INSERT INTO cierres_caja (..., fecha_hora_emision, ...) VALUES (..., ?, ...)
  ps.setString(3, cierre.getFechaHoraEmision()); // <-- Envía "" a una columna DATETIME!
  ```
- **Demostración del Escenario de Falla:**
  1. En el cierre de caja, si la instancia de `CierreCaja` se crea o utiliza sin invocar explícitamente `setFechaHoraEmision(...)` con un timestamp formateado (`yyyy-MM-dd HH:mm:ss`), el campo conserva su valor por defecto `""`.
  2. Al persistir el cierre mediante `CierreCajaDao.guardarCierre`, se ejecuta `ps.setString(3, "")` contra la columna `fecha_hora_emision`, la cual es de tipo `DATETIME NOT NULL` en `cierres_caja`.
  3. En MySQL 8 con el modo estándar de producción (`STRICT_TRANS_TABLES`), MySQL rechaza de plano la inserción arrojando:
     `com.mysql.cj.jdbc.exceptions.MysqlDataTruncation: Data truncation: Incorrect datetime value: '' for column 'fecha_hora_emision' at row 1`.
  4. La transacción es abortada, impidiendo que el cajero o administrador guarde el Cierre Z de la jornada.
- **Impacto Real:** Bloqueo operativo en el cierre diario de caja y pérdida de persistencia de auditoría fiscal.
- **Recomendación Técnica:**
  En `CierreCajaDao.guardarCierre`, verificar si `fechaHoraEmision` es nulo o vacío; en tal caso, utilizar el timestamp actual formateado (`LocalDateTime.now().format(...)`) o `CURRENT_TIMESTAMP`:
  ```java
  String fechaEmision = (cierre.getFechaHoraEmision() != null && !cierre.getFechaHoraEmision().isBlank())
          ? cierre.getFechaHoraEmision()
          : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  ps.setString(3, fechaEmision);
  ```

---

#### Fallo 16: Bloqueo permanente de anulación de comandas cobradas en producción
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 1267-1284.
  - `src/Modelo/MovimientoSaldoPedidoDao.java`: Inexistencia de invocaciones en `src/`.
- **Código Problemático:**
  ```java
  // src/Modelo/PedidosDao.java (líneas 1267-1283)
  if (pagadoUsd != null && pagadoUsd.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal compensadoUsd = BigDecimal.ZERO;
      try (PreparedStatement psMov = con.prepareStatement(
              "SELECT COALESCE(SUM(CASE WHEN moneda = 'USD' THEN monto_origen ELSE ROUND(equivalente_ves / NULLIF(tasa_cambio, 0), 2) END), 0) "
              + "FROM pedido_saldos_movimientos WHERE id_pedido = ? AND tipo IN ('DEVOLUCION','CREDITO','ANULACION')")) {
          psMov.setInt(1, id_pedido);
          try (ResultSet rsMov = psMov.executeQuery()) {
              if (rsMov.next()) {
                  compensadoUsd = rsMov.getBigDecimal(1);
              }
          }
      }
      if (compensadoUsd == null || compensadoUsd.compareTo(pagadoUsd.subtract(new BigDecimal("0.01"))) < 0) {
          throw ErrorAplicacionException.validacion(
                  "El pedido #" + id_pedido + " tiene pagos registrados por $" + pagadoUsd.setScale(2, RoundingMode.HALF_UP)
                  + " y solo se ha compensado/devuelto $" + (compensadoUsd != null ? compensadoUsd.setScale(2, RoundingMode.HALF_UP) : "0.00")
                  + "; se requiere contra-asiento o devolución equivalente antes de anular.");
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Un cliente pagó total o parcialmente una comanda y surge una contingencia (comida devuelta, cargo indebido, cancelación antes del despacho).
  2. El gerente intenta anular el pedido desde el sistema.
  3. El código exige que existan contra-asientos registrados en la tabla `pedido_saldos_movimientos` que cubran el monto pagado.
  4. Sin embargo, en todo el proyecto (`src/`), la clase `MovimientoSaldoPedidoDao` **jamás es instanciada ni invocada**, y no existe en la interfaz de usuario Swing ningún botón ni formulario para registrar devoluciones en dicha tabla.
  5. En consecuencia, `compensadoUsd` siempre es `0.00` y `anularPedidoConAuditoria` invariablemente detona la excepción, haciendo imposible para los usuarios anular cualquier pedido con pagos en el sistema real.
- **Impacto Real:** Inoperabilidad administrativa ante devoluciones y anulaciones justificadas de comensales.
- **Recomendación Técnica:**
  Al anular un pedido cobrado en `anularPedidoConAuditoria`, generar automáticamente en la misma transacción el movimiento de contra-asiento tipo `'ANULACION'` en `pedido_saldos_movimientos` por el total pagado y marcar los registros correspondientes de `pedido_pagos`.

---

#### Fallo 17: Pérdida de efectivo esperado en Cierre Z en pedidos mixtos modernos
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 490-497.
- **Código Problemático:**
  ```java
  // src/Modelo/CierreCajaDao.java (líneas 490-496)
  case "MIXTO" -> {
      if (p.getEfectivoBs() == null || p.getEfectivoUsd() == null) {
          mixtosSinDesglose++;
      } else {
          efectivoEsperadoBs = efectivoEsperadoBs.add(p.getEfectivoBs());
          efectivoEsperadoUsd = efectivoEsperadoUsd.add(p.getEfectivoUsd());
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. En pedidos cobrados con pagos modernos desglosados en `pedido_pagos` (`p.getPagos()`), los cobros de efectivo se encuentran registrados en las líneas de pago (`PagoPedido.metodo() IN ('EFECTIVO', 'EFECTIVO_BS', 'EFECTIVO_USD')`).
  2. Si en la cabecera `pedidos`, los campos `efectivo_bs` o `efectivo_usd` son nulos, `calcularDesdeMemoria` incrementa `mixtosSinDesglose++` y **omite por completo sumar el efectivo cobrado** al efectivo esperado en caja (`efectivoEsperadoBs` / `efectivoEsperadoUsd`).
  3. En la rutina de líneas 500-515, el desglose en `p.getPagos()` solo acumula en `metodosMap`, pero jamás alimenta `efectivoEsperadoBs` ni `efectivoEsperadoUsd`.
  4. El reporte de arqueo del Cierre Z indica un efectivo esperado en caja de $0.00 o sustancialmente inferior al dinero real cobrado, provocando falsos "sobrantes de caja" en la conciliación.
- **Impacto Real:** Descontrol en el arqueo de caja física y riesgo de sustracción no detectada de efectivo.
- **Recomendación Técnica:**
  En `calcularDesdeMemoria`, si `p.getPagos()` no está vacío, sumar directamente los pagos de efectivo (`pp.metodo().startsWith("EFECTIVO")`) a las variables `efectivoEsperadoBs` y `efectivoEsperadoUsd` según su moneda respectiva.

---

#### Fallo 18: Discrepancia entre validación de persistencia y emisión de recibo: facturación bloqueada
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Servicio/PedidoServicio.java`: Líneas 113-116.
  - `src/Servicio/GeneradorPdfPedido.java`: Líneas 315-320.
- **Código Problemático:**
  ```java
  // src/Servicio/PedidoServicio.java (líneas 113-116)
  BigDecimal totalPedido = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
  if (totalPedido.compareTo(totalEsperadoExacto) != 0 && totalPedido.compareTo(subtotalEsperado) != 0) {
      throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles.");
  }
  ...
  // src/Servicio/GeneradorPdfPedido.java (líneas 315-318)
  if (pedido.getTotalDecimal() != null && pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP).subtract(calc.totalUsd()).abs().compareTo(tol) > 0) {
      throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles.");
  }
  ```
- **Demostración del Escenario de Falla:**
  1. En `PedidoServicio.validarPedidoPersistible`, la condición `totalPedido.compareTo(subtotalEsperado) != 0` permite que un pedido que contiene ítems gravados (donde `ivaEsperado > 0` y `totalEsperadoExacto = subtotal + IVA`) sea registrado exitosamente con `total = subtotal` (omitiendo el IVA).
  2. El pedido se persiste sin errores en la base de datos con el IVA evadido en el total.
  3. Al momento de generar la factura o recibo PDF del cliente, `GeneradorPdfPedido.validar` evalúa `calc.totalUsd()` (que calcula `subtotal + IVA`) contra `pedido.getTotalDecimal()`.
  4. La diferencia entre 100.00 y 116.00 excede ampliamente la tolerancia permitida (`0.01`), arrojando `ErrorAplicacionException: El total del pedido no coincide con sus detalles.`.
  5. El pedido queda atrapado en el sistema: fue guardado en base de datos pero es imposible de imprimir, facturar o entregar al cliente.
- **Impacto Real:** Incoherencia entre capas del dominio, pedidos bloqueados en caja e infracción tributaria en la liquidación de IVA.
- **Recomendación Técnica:**
  Eliminar `&& totalPedido.compareTo(subtotalEsperado) != 0` en `PedidoServicio.java:114`. El total de un pedido gravado debe ser siempre estricta y únicamente igual a `totalEsperadoExacto`.

---

#### Fallo 19: Descuadre de totales en Bolívares en reportes de cierre por omisión de `NULLIF(p.total_bs, 0)`
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 209, 281.
- **Código Problemático:**
  ```sql
  -- src/Modelo/CierreCajaDao.java: Línea 209 (sqlMetodos) y Línea 281 (sqlSalas)
  COALESCE(p.total_bs, ROUND(p.total * COALESCE(NULLIF(p.tasa_cambio, 0), ?), 2), 0) AS monto_bs
  -- Comparar con Línea 132 (sqlTotales, donde SÍ se usa NULLIF):
  COALESCE(NULLIF(total_bs, 0), ROUND(total * COALESCE(NULLIF(tasa_cambio, 0), ?), 2), 0) AS calc_tot_bs
  ```
- **Demostración del Escenario de Falla:**
  1. En pedidos finalizados legados, la columna `total_bs` puede contener el valor numérico `0.00` en lugar de `NULL`.
  2. En `sqlTotales`, la expresión `NULLIF(total_bs, 0)` convierte `0.00` en `NULL`, lo que activa el recálculo referencial `ROUND(total * tasa, 2)` y computa el importe real en Bolívares.
  3. Sin embargo, en `sqlMetodos` (línea 209) y en `sqlSalas` (línea 281), la consulta utiliza únicamente `COALESCE(p.total_bs, ...)`. Como `0.00` no es `NULL`, `COALESCE` devuelve `0.00`.
  4. Al imprimir el Cierre Z:
     - `Total Ventas en Bs:` Bs. 5,000.00 (calculado por `sqlTotales`).
     - `Suma de Desglose por Formas de Pago en Bs:` Bs. 0.00 (calculado por `sqlMetodos`).
     - `Suma de Ventas por Sala en Bs:` Bs. 0.00 (calculado por `sqlSalas`).
  5. El reporte presenta un descuadre aritmético evidente e inaceptable entre la cabecera y sus tablas de desglose.
- **Impacto Real:** Reportes fiscales con incoherencias aritméticas internas.
- **Recomendación Técnica:**
  Homogeneizar las consultas `sqlMetodos` y `sqlSalas` utilizando `COALESCE(NULLIF(p.total_bs, 0), ROUND(p.total * COALESCE(NULLIF(p.tasa_cambio, 0), ?), 2), 0)`.

---

#### Fallo 20: Silenciamiento de cobros de efectivo con método canónico `'EFECTIVO'`
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 904-910.
- **Código Problemático:**
  ```java
  // src/Modelo/PedidosDao.java (líneas 904-910)
  if ("VES".equals(pago.moneda())) {
      aplicadoVes = aplicadoVes.add(aplicado);
      if ("EFECTIVO_BS".equals(pago.metodo())) efectivoBs = efectivoBs.add(aplicado);
  } else {
      aplicadoUsd = aplicadoUsd.add(aplicado);
      if ("EFECTIVO_USD".equals(pago.metodo())) efectivoUsd = efectivoUsd.add(pago.monto());
  }
  ```
- **Demostración del Escenario de Falla:**
  1. El método de pago estándar configurado por defecto en la base de datos y en los selectores de la aplicación es `"EFECTIVO"`.
  2. Cuando se registra un cobro mixto o simple enviando filas de pago con `metodo = "EFECTIVO"` y moneda `"VES"` o `"USD"`, la condición `if ("EFECTIVO_BS".equals(pago.metodo()))` evalúa a `false`.
  3. Asimismo, para moneda `"USD"`, la condición `if ("EFECTIVO_USD".equals(pago.metodo()))` evalúa a `false`.
  4. En consecuencia, las variables acumuladoras `efectivoBs` y `efectivoUsd` permanecen en `0.00`.
  5. En la actualización de la tabla `pedidos`, se persisten `efectivo_bs = 0.00` y `efectivo_usd = 0.00`.
  6. Toda consulta de arqueo o cierre de caja que consulte las columnas `efectivo_bs` o `efectivo_usd` de los pedidos pierde el registro del dinero recibido.
- **Impacto Real:** Distorsión del arqueo de caja y pérdida de trazabilidad de los importes en efectivo físico.
- **Recomendación Técnica:**
  Evaluar tanto el identificador canónico como el específico:
  ```java
  if ("VES".equals(pago.moneda())) {
      aplicadoVes = aplicadoVes.add(aplicado);
      if ("EFECTIVO".equals(pago.metodo()) || "EFECTIVO_BS".equals(pago.metodo())) efectivoBs = efectivoBs.add(aplicado);
  } else {
      aplicadoUsd = aplicadoUsd.add(aplicado);
      if ("EFECTIVO".equals(pago.metodo()) || "EFECTIVO_USD".equals(pago.metodo())) efectivoUsd = efectivoUsd.add(aplicado);
  }
  ```

---

#### Fallo 21: Descuadre interno masivo en reportes de Cierre Z por revalorización asimétrica de pedidos históricos en desgloses
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 132-133, Línea 207, Línea 247, Línea 279.
- **Código Problemático:**
  ```sql
  -- src/Modelo/CierreCajaDao.java: Línea 132 (sqlTotales, modificado por commit NM-02)
  COALESCE(NULLIF(total_bs, 0), CASE WHEN tasa_cambio IS NOT NULL AND tasa_cambio > 0 THEN ROUND(total * tasa_cambio, 2) ELSE 0.00 END) AS calc_tot_bs,
  COALESCE(NULLIF(subtotal_bs, 0), CASE WHEN tasa_cambio IS NOT NULL AND tasa_cambio > 0 THEN ROUND(subtotal * tasa_cambio, 2) ELSE 0.00 END) AS calc_sub_bs
  
  -- src/Modelo/CierreCajaDao.java: Línea 207 (sqlMetodos, NO corregido)
  COALESCE(NULLIF(p.total_bs, 0), ROUND(p.total * COALESCE(NULLIF(p.tasa_cambio, 0), ?), 2), 0) AS monto_bs
  
  -- src/Modelo/CierreCajaDao.java: Línea 247 (sqlEfectivo, NO corregido)
  CASE WHEN p.metodo_pago IN ('EFECTIVO', 'EFECTIVO_BS') THEN COALESCE(NULLIF(p.efectivo_bs, 0), NULLIF(p.total_bs, 0), ROUND(p.total * COALESCE(NULLIF(p.tasa_cambio, 0), ?), 2), 0)
  
  -- src/Modelo/CierreCajaDao.java: Línea 279 (sqlSalas, NO corregido)
  COALESCE(SUM(COALESCE(NULLIF(p.total_bs, 0), ROUND(p.total * COALESCE(NULLIF(p.tasa_cambio, 0), ?), 2), 0)), 0) AS total_bs
  ```
- **Demostración del Escenario de Falla:**
  1. En pedidos finalizados históricos donde no se registró tasa de cambio (`p.tasa_cambio IS NULL`) ni total en bolívares (`p.total_bs IS NULL` o `0`), el commit `ffb0863` (NM-02) modificó exitosamente `sqlTotales` para no revalorizarlos a la tasa activa, evaluándolos como `0.00` Bs.
  2. Sin embargo, la corrección omitió por completo las consultas de desglose `sqlMetodos` (línea 207), `sqlEfectivo` (línea 247) y `sqlSalas` (línea 279), las cuales continúan utilizando `ROUND(p.total * COALESCE(NULLIF(p.tasa_cambio, 0), ?), 2)` recibiendo `tasaConfig` (ej. 36.5000).
  3. Al generarse el Cierre Z:
     - `Total General Ventas en Bs:` Bs. 0.00 (calculado por `sqlTotales`).
     - `Total Ventas por Métodos de Pago en Bs:` Bs. 36,500.00 (calculado por `sqlMetodos`).
     - `Total Ventas por Sala en Bs:` Bs. 36,500.00 (calculado por `sqlSalas`).
     - `Efectivo Esperado en Bs:` Bs. 36,500.00 (calculado por `sqlEfectivo`).
  4. El informe fiscal impreso y persistido presenta un descuadre interno escandaloso entre la cabecera contable y sus desgloses anexos, exponiendo al contribuyente a sanciones tributarias severas.
- **Impacto Real:** Integridad fiscal e imposibilidad de superar auditorías del SENIAT debido a desbalances aritméticos flagrantes en los reportes de cierre.
- **Recomendación Técnica:**
  Homologar inmediatamente las consultas `sqlMetodos`, `sqlEfectivo` y `sqlSalas` reemplazando el uso de `?` (`tasaConfig`) por la misma expresión protectora de `sqlTotales`:
  `COALESCE(NULLIF(p.total_bs, 0), CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND(p.total * p.tasa_cambio, 2) ELSE 0.00 END)` y retirar el paso de `tasaConfig` en los índices correspondientes del `PreparedStatement`.

---

#### Fallo 22: Corrupción de catálogo y violación de clave foránea por mapeo espurio de `d.getId()` como `id_plato` en atención de mesas
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Vista/PanelAtencionMesasSplit.java`: Línea 1007, Líneas 1414-1425.
  - `src/Modelo/PedidosDao.java`: Líneas 528-556.
  - `BD.sql`: Línea 393 (`CONSTRAINT fk_detalle_pedidos_plato FOREIGN KEY (id_plato) REFERENCES platos (id)`).
- **Código Problemático:**
  ```java
  // src/Vista/PanelAtencionMesasSplit.java: Línea 1007
  int idPlato = (d.getIdPlato() != null && d.getIdPlato() > 0) ? d.getIdPlato() : d.getId();
  ...
  // src/Vista/PanelAtencionMesasSplit.java: Líneas 1414-1415
  DetallePedido det = new DetallePedido();
  det.setIdPlato(idPlato > 0 ? idPlato : null);
  ```
- **Demostración del Escenario de Falla:**
  1. Un pedido activo o histórico contiene ítems sin enlace directo al catálogo (`d.getIdPlato() == null`), por ejemplo comandas heredadas o platos especiales cargados manualmente. En la base de datos, dicha línea posee `id = 89` en la tabla `detalle_pedidos`.
  2. El mesonero abre la mesa en `PanelAtencionMesasSplit`. En la línea 1007, al ser `d.getIdPlato() == null`, el código toma `d.getId()` (89) y lo asigna como identificador del plato (`idPlato = 89`).
  3. El mesonero añade cualquier plato adicional a la mesa y pulsa "Enviar a Cocina" / "Actualizar Pedido".
  4. En `guardarComandaActual`, se construye un `DetallePedido` con `det.setIdPlato(89)`.
  5. En `PedidosDao.calcularYConsolidarSnapshot` (líneas 528-556), el sistema ejecuta `SELECT ... FROM platos WHERE id = 89`:
     - **Escenario Corrupción Silenciosa:** Si existe en el catálogo un plato con `id = 89` (por ejemplo, "Botella de Ron Añejo"), el sistema sobrescribe el nombre original del plato ("Jugo de Naranja") y su precio oficial con los de la "Botella de Ron Añejo". El cliente es cobrado por un producto no consumido.
     - **Escenario Bloqueo Operativo:** Si no existe un plato con `id = 89`, `PedidosDao` arroja `ErrorAplicacionException: El producto '...' ya no está disponible en el catálogo` o el motor MySQL aborta con violación de clave foránea `fk_detalle_pedidos_plato`.
  6. La mesa queda bloqueada: no se puede cobrar, editar ni actualizar hasta que un DBA modifique las filas a nivel SQL.
- **Impacto Real:** Corrupción directa de facturación, sustitución descontrolada de ítems en comandas y bloqueo de mesas en servicio.
- **Recomendación Técnica:**
  En `PanelAtencionMesasSplit.java:1007`, no utilizar jamás el ID de la tabla `detalle_pedidos` como sustituto de `idPlato`. Si `d.getIdPlato()` es nulo o menor o igual a cero, mantener `idPlato = 0` (o `null`), asegurando que al regenerar la comanda no se envíe un `idPlato` artificial ni se consulte la tabla `platos` con una clave primaria ajena.

---

#### Fallo 23: Pérdida silenciosa de adiciones de comanda al cobrar: "Cobrar / Facturar" ignora cambios locales en mesas ocupadas
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Vista/PanelAtencionMesasSplit.java`: Líneas 1474-1486.
- **Código Problemático:**
  ```java
  // src/Vista/PanelAtencionMesasSplit.java (líneas 1474-1486)
  private void cobrarPedidoDirecto() {
      if (idPedidoActivo > 0) {
          sistema.cargarPedidoEnPantallaDesdeSplit(idPedidoActivo);
      } else if (modeloCarrito.getRowCount() > 0) {
          // Guardar primero y luego cobrar
          enviarPedidoCocina();
          if (idPedidoActivo > 0) {
              sistema.cargarPedidoEnPantallaDesdeSplit(idPedidoActivo);
          }
      } else {
          sistema.mostrarAvisoEnBanda("No hay pedido para cobrar en esta mesa");
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Una mesa se encuentra ocupada con un pedido registrado en cocina (`idPedidoActivo = 205`), cuyo saldo actual es de $15.00.
  2. Los clientes piden 2 cafés adicionales ($4.00) y piden la cuenta simultáneamente.
  3. El mesonero/cajero selecciona la mesa en la vista dividida, agrega los 2 cafés al carrito en pantalla (la interfaz muestra correctamente el nuevo total de $19.00) y, en lugar de pulsar "Actualizar Pedido" y luego "Cobrar", pulsa de inmediato el botón principal "Cobrar / Facturar".
  4. Como `idPedidoActivo > 0` es verdadero, la condición entra en el primer `if` e invoca `sistema.cargarPedidoEnPantallaDesdeSplit(205)` **sin guardar ni persistir los ítems recién agregados al carrito**.
  5. La pantalla de cobro carga el pedido #205 tal como está en la base de datos (con el total viejo de $15.00).
  6. El cajero procesa el pago por $15.00, se imprime la factura y la mesa se libera.
  7. Los 2 cafés servidos se pierden en el limbo: jamás fueron guardados, ni facturados, ni descontados del inventario.
- **Impacto Real:** Económico directo (fuga continua de ingresos por consumos añadidos en mesa inmediatamente antes del pago).
- **Recomendación Técnica:**
  En `cobrarPedidoDirecto()`, sincronizar obligatoriamente los cambios locales del carrito invocando `guardarComandaActual()` antes de transferir el control a la pantalla de cobro, asegurando que cualquier ítem añadido quede formalmente registrado antes de emitir la pre-cuenta o factura.

---

#### Fallo 24: Reincidencia del defecto F-14: Auto-congelado en actualización de catálogo inventa defaults monetarios en USD para líneas huérfanas
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PreciosPedidosDao.java`: Líneas 321-349.
- **Código Problemático:**
  ```java
  // src/Modelo/PreciosPedidosDao.java (líneas 321-327, commit 368d955)
  // Para ítems legados sin idPlato (desvinculados del catálogo), auto-congelar preservando consistencia
  det.setPrecioCongelado(true);
  if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) det.setMonedaBase("USD");
  if (det.getPrecioIncluyeIva() == null) det.setPrecioIncluyeIva(false);
  if (det.getAplicaIva() == null) det.setAplicaIva(true);
  BigDecimal ivaDet = Boolean.TRUE.equals(det.getAplicaIva()) ? ivaPorcentajeCabecera : BigDecimal.ZERO.setScale(2);
  det.setIvaPorcentaje(ivaDet);
  ```
- **Demostración del Escenario de Falla:**
  1. A pesar de haber sido reportado en la primera ronda como F-14, la corrección aplicada en el commit `368d955` mantuvo la invención de defaults prohibidos para todas las líneas donde `det.getIdPlato()` es nulo o huérfano.
  2. Si una comanda abierta contiene ítems legados sin vincular creados en bolívares (ejemplo: `precio = 200.00` Bs, `moneda_base = NULL`), al momento en que cualquier usuario edita el precio de cualquier producto en el catálogo de platos, se dispara `PreciosPedidosDao.actualizarPrecios`.
  3. Para las líneas huérfanas de pedidos abiertos, el bloque de líneas 321-327 fija ciegamente `moneda_base = 'USD'`, `precio_incluye_iva = false` y `aplica_iva = true`.
  4. El plato que costaba Bs. 200.00 pasa a ser valorado en $200.00 USD + 16% IVA = $232.00 USD, lo que a una tasa de 36.50 representa Bs. 8,468.00.
  5. La cuenta del cliente sufre una inflación arbitraria de más del 4,100% de manera inadvertida para el operador.
- **Impacto Real:** Alteración masiva no consentida de precios en pedidos abiertos, sobrefacturación fraudulenta y violación de la invariante F-14.
- **Recomendación Técnica:**
  Prohibir terminantemente la asignación ciega de `"USD"` en líneas que carecen de `moneda_base`. El sistema debe congelar la línea en bolívares (`VES`) respetando el valor nominal original, o rechazar la propagación de precios requiriendo la intervención explícita del administrador.

---

#### Fallo 25: Cierre Z omite ventas nocturnas que cruzan la medianoche por filtrar por `fecha` de apertura en vez de `cerrada_en`
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 972-977 (commit `3113a37` / NM-06).
  - `src/Modelo/CierreCajaDao.java`: Líneas 135-136, 202, 209, 244, 253, 283, 354.
- **Código Problemático:**
  ```java
  // src/Modelo/PedidosDao.java (líneas 972-977)
  String sqlActualizar = """
      UPDATE pedidos
      SET estado = 'FINALIZADO', cliente_nombre = ?, cliente_documento = ?, metodo_pago = ?,
          efectivo_bs = ?, efectivo_usd = ?, cerrada_en = COALESCE(cerrada_en, CURRENT_TIMESTAMP)
      WHERE id = ? AND estado = 'PENDIENTE'
      """;
  ```
  ```sql
  -- src/Modelo/CierreCajaDao.java (línea 135)
  WHERE estado = 'FINALIZADO'
    AND (fecha LIKE ? OR DATE(fecha) = ?)
  ```
- **Demostración del Escenario de Falla:**
  1. En el commit `3113a37` (NM-06), se retiró `fecha = CURRENT_TIMESTAMP` del `UPDATE pedidos` para preservar la fecha original de apertura de la mesa.
  2. Si una mesa se abre a las 23:20 del día 16 de octubre, su campo `fecha` almacena `2026-10-16 23:20:00`.
  3. Los clientes terminan su servicio y efectúan el pago en efectivo a las 00:50 del día 17 de octubre. La comanda se finaliza con `cerrada_en = 2026-10-17 00:50:00`, pero `fecha` permanece en `2026-10-16 23:20:00`.
  4. Al concluir la jornada del 17 de octubre a las 02:00, el cajero ejecuta el Cierre Z de la fecha `2026-10-17`.
  5. Todas las consultas de `CierreCajaDao` filtran por `DATE(fecha) = '2026-10-17'`.
  6. Como `DATE(fecha)` es `2026-10-16`, la venta cobrada el 17 de octubre es **completamente omitida** del Cierre Z del 17 de octubre.
  7. El arqueo de caja física arroja un sobrante inexplicable de dinero en efectivo recaudado sin facturación respaldatoria. Y en el cierre del 16 tampoco apareció porque a medianoche la comanda aún estaba `PENDIENTE`.
- **Impacto Real:** Descontrol en el arqueo de efectivo, evasión fiscal involuntaria por ventas no declaradas en la jornada de cobro y distorsión de los libros contables.
- **Recomendación Técnica:**
  En `CierreCajaDao.java`, basar el corte de caja de pedidos finalizados en la fecha de liquidación/cobro:
  `WHERE estado = 'FINALIZADO' AND (COALESCE(DATE(cerrada_en), DATE(fecha)) = ?)`.

---

#### Fallo 26: Condición de carrera en `guardarCierre`: verificación `existeCierreZ` fuera de transacción permite persistir Cierres Z duplicados concurrentes
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 637-640, 689-703 (commit `5e7621c` / NM-01).
  - `BD.sql`: Líneas 475-498 (`CREATE TABLE cierres_caja`).
- **Código Problemático:**
  ```java
  // src/Modelo/CierreCajaDao.java (líneas 637-640)
  if ("TOTAL".equalsIgnoreCase(tipoCierre) && existeCierreZ(fechaJornada)) {
      throw ErrorAplicacionException.validacion("ya existe un cierre Z registrado para la fecha " + fechaJornada + ".");
  }
  ...
  // src/Modelo/CierreCajaDao.java (líneas 689-703)
  public boolean existeCierreZ(String fechaJornada) {
      String sql = "SELECT COUNT(*) FROM cierres_caja WHERE tipo = 'TOTAL' AND fecha_jornada = ?";
      try (Connection con = conexiones.getConnection(); ...
  ```
- **Demostración del Escenario de Falla:**
  1. Dos usuarios administradores ejecutan el Cierre Z casi al mismo tiempo desde terminales distintos, o un usuario realiza un doble clic rápido en la interfaz Swing.
  2. Dos hilos de ejecución concurrentes ingresan a `guardarCierre`.
  3. Ambos hilos ejecutan `existeCierreZ(fechaJornada)` antes de que cualquiera haya efectuado el `commit`.
  4. Dado que `existeCierreZ` abre una conexión separada en modo no bloqueante (`SELECT COUNT(*)` simple sin transaccionalidad), ambos hilos obtienen `false`.
  5. Ambos hilos proceden a ejecutar `INSERT INTO cierres_caja (...)`.
  6. La tabla `cierres_caja` en `BD.sql` carece de restricción única (`UNIQUE KEY (tipo, fecha_jornada)`), poseyendo únicamente índices no únicos (`KEY idx_cierres_fecha`, `KEY idx_cierres_tipo`).
  7. Ambos registros se insertan exitosamente en la base de datos, violando la regla legal y de negocio que prohíbe taxativamente la emisión de más de un Cierre Z por jornada operativa.
- **Impacto Real:** Duplicación de reportes de cierre fiscal y ruptura de la invariante NM-01 bajo condiciones normales de concurrencia.
- **Recomendación Técnica:**
  1. Incorporar una restricción única estricta en el esquema de la base de datos:
     `ALTER TABLE cierres_caja ADD CONSTRAINT uq_cierres_tipo_fecha UNIQUE (tipo, fecha_jornada);`.
  2. Realizar la verificación de existencia dentro de la misma transacción de guardado utilizando `SELECT ... FOR UPDATE` o capturando la excepción de clave duplicada (`SQLState 23000` / error 1062).

---

#### Fallo 27: Descalce aritmético en `ajusteCambioBs`: `CalculoPedido` calcula el ajuste con `totalUsdReferencia` mientras `totalUsd()` aditivo deriva un valor diferente
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Servicio/CalculoMonetarioPedido.java`: Líneas 166-168, Líneas 275-278 (commit `cb03f48` / N-05).
- **Código Problemático:**
  ```java
  // src/Servicio/CalculoMonetarioPedido.java (líneas 166-168)
  BigDecimal ajusteCambioBs = totalVes.subtract(
          totalUsdReferencia.multiply(tasaNormalizada).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP))
          .setScale(ESCALA_MONEDA);
  ...
  // src/Servicio/CalculoMonetarioPedido.java (líneas 275-278)
  public BigDecimal totalUsd() {
      // Bajo política D8: se prioriza la aditividad de líneas (subtotalUsd + ivaUsd = totalUsd) sobre la división directa.
      return subtotalUsd().add(ivaUsd()).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
  }
  ```
- **Demostración del Escenario de Falla:**
  1. En pedidos con platos expresados en Bolívares o combinaciones de líneas gravadas y exentas, `totalUsdReferencia` acumula la conversión directa a nivel de detalle.
  2. Tras el commit N-05, `calc.totalUsd()` fue modificado para ser aditivo: `subtotalUsd().add(ivaUsd())`.
  3. Debido a los efectos de redondeo en 2 decimales (`HALF_UP`), la suma de `subtotalUsd + ivaUsd` puede arrojar un centavo de diferencia con respecto a `totalUsdReferencia` (ejemplo: `$12.51` vs `$12.50`).
  4. Sin embargo, la variable `ajusteCambioBs` almacenada en el record `CalculoPedido` (línea 166) continúa calculándose con base en `totalUsdReferencia` ($12.50) en lugar de utilizar `calc.totalUsd()` ($12.51).
  5. Cuando `PedidosDao.finalizarPedidoConPagos` registra la liquidación en `pedido_liquidaciones`, el ajuste cambiario registrado no cuadra con la fórmula oficial `totalVes - ROUND(totalUsd * tasa, 2)`.
- **Impacto Real:** Asimetría contable entre el total persistido en la cabecera del pedido y el asiento de ajuste cambiario en la tabla de liquidaciones.
- **Recomendación Técnica:**
  Calcular `ajusteCambioBs` en `CalculoPedido` utilizando el valor de `totalUsd()` obtenido aditivamente, garantizando una relación matemática exacta y consistente entre todos los campos del cálculo.

---

#### Fallo 28: Vulnerabilidad de canal lateral de tiempo (Timing Attack) en autenticación PBKDF2 permite enumeración remota de usuarios
- **Severidad:** **BAJA**
- **Archivo y Líneas:**
  - `src/Modelo/LoginDao.java`: Líneas 84-88, Línea 102 (commit `dbbb64b` / N-06).
- **Código Problemático:**
  ```java
  // src/Modelo/LoginDao.java (líneas 84-88)
  if (usuario == null || !rolReconocido(usuario.getRol())) {
      ErrorAplicacionException.registrarAdvertencia("Intento de inicio de sesión rechazado.");
      return Optional.empty();
  }
  ...
  // src/Modelo/LoginDao.java (línea 102)
  boolean claveValida = verificarClaveEnMemoria(clave, contrasenaGuardada);
  ```
- **Demostración del Escenario de Falla:**
  1. Para evitar retener conexiones abiertas durante el cómputo costoso del algoritmo PBKDF2 (600,000 iteraciones), el commit N-06 extrajo la verificación de hash de la transacción SQL.
  2. Sin embargo, si el usuario ingresado no existe en la tabla `usuarios`, el método retorna inmediatamente `Optional.empty()` en la línea 87 (tiempo de respuesta: ~1 a 2 ms).
  3. Si el usuario existe pero la contraseña es errónea, el sistema procede a ejecutar `verificarClaveEnMemoria`, consumiendo el tiempo completo de derivación PBKDF2 (tiempo de respuesta: ~150 a 300 ms).
  4. Un atacante en la red del restaurante puede enviar un diccionario de nombres de usuario comunes (`admin`, `gerente`, `cajero1`, `juan`, `maria`) y medir la latencia de respuesta.
  5. Las respuestas que tarden más de 100 ms confirman de forma inequívoca que la cuenta existe en la base de datos, permitiendo perfilar los objetivos para ataques de fuerza bruta de credenciales.
- **Impacto Real:** Divulgación de información sensible (nombres de usuario y cuentas administrativas activas).
- **Recomendación Técnica:**
  En caso de que `usuario == null`, ejecutar siempre una computación de hash ficticia (`passwordHasher.verificar("dummy".toCharArray(), DUMMY_HASH)`) para igualar los tiempos de ejecución independientemente de la existencia del usuario.


---

#### Fallo 29: Destrucción irreversible de datos contables y vaciado de historial de ventas mediante `DROP TABLE IF EXISTS` en scripts de "actualización"
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `actualizar_datos_cliente.sh`: Líneas 68-73.
  - `actualizar_datos_cliente.bat`: Líneas 83-96.
  - `BD RESTAURANTE 2026.sql`: Líneas 24, 46, 52, 60, 68, 76, 84, 102, 110, 118, 126, 134, 142, 150, 158, 166, 174, 182, 190, 198, 206.
- **Código Problemático:**
  ```sql
  -- BD RESTAURANTE 2026.sql: Líneas 24, 46, 102, 158
  DROP TABLE IF EXISTS `config`;
  ...
  DROP TABLE IF EXISTS `pedidos`;
  ...
  DROP TABLE IF EXISTS `pedido_pagos`;
  ...
  DROP TABLE IF EXISTS `cierres_caja`;
  ```
  ```bash
  # actualizar_datos_cliente.sh: Líneas 68-73
  echo "[3/4] Importando datos reales y catálogo sanitizado ($SQL_FILE)..."
  if [ "$USAR_DOCKER" -eq 1 ]; then
      docker exec -i "$CONTENEDOR" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
  else
      mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
  fi
  ```
  ```bat
  rem actualizar_datos_cliente.bat: Líneas 83-93
  if defined MYSQLDUMP_CMD if exist "!MYSQLDUMP_CMD!" (
      "!MYSQLDUMP_CMD!" -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% --single-transaction %DB_NAME% > "%BACKUP_FILE%" 2>nul
  )
  ...
  "!MYSQL_CMD!" -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% %DB_NAME% < "%SQL_FILE%"
  ```
- **Demostración del Escenario de Falla:**
  1. El paquete ejecutable (`generar_restaurante_ejecutable.sh` / `.ps1`) distribuye a los usuarios finales los scripts `actualizar_datos_cliente.bat` y `actualizar_datos_cliente.sh`, presentados como una herramienta para actualizar los datos del cliente ("RESTAURANT LA AVALANCHA 2023, C.A") y el catálogo de 87 platos reales.
  2. Un restaurante que ha estado operando en producción durante días o semanas decide ejecutar el script para sincronizar o actualizar los datos del negocio.
  3. A diferencia de `inicio_fresco.sh` (que exige confirmación explícita escribiendo `SI`), `actualizar_datos_cliente` NO solicita confirmación alguna al usuario y procede de forma ciega.
  4. En Windows, si `mysqldump.exe` falla (por variables de entorno, permisos de escritura o servicio ocupado), la condición `if exist "%BACKUP_FILE%"` simplemente emite un aviso en consola y continúa inmediatamente a la importación.
  5. El archivo `BD RESTAURANTE 2026.sql` contiene sentencias `DROP TABLE IF EXISTS` para 21 tablas estructurales, incluyendo `pedidos`, `detalle_pedidos`, `pedido_pagos`, `pedido_liquidaciones`, `pedido_saldos_movimientos`, `cierres_caja`, `auditoria_pedidos`, `usuarios` y `clientes`.
  6. Todas las tablas de transacciones se recrean completamente vacías (`0` filas). Se pierden de manera instantánea e irrecuperable todas las ventas históricas, cobros en caja, cierres fiscales Z, movimientos de auditoría y cuentas por cobrar.
  7. Asimismo, la tabla `usuarios` se destruye y se repuebla únicamente con el usuario `xavier`, eliminando el acceso de todos los mesoneros, cajeros y administradores previamente configurados.
- **Impacto Real:** Pérdida total y catastrófica de información contable, fiscal y operativa en producción. Violación crítica de retención legal tributaria (SENIAT).
- **Recomendación Técnica:**
  1. Eliminar de raíz cualquier sentencia `DROP TABLE` o `TRUNCATE` de un archivo concebido para "actualizar datos".
  2. Implementar la sincronización mediante `INSERT INTO ... ON DUPLICATE KEY UPDATE` o sentencias `UPDATE` dirigidas exclusivamente a las tablas maestras (`config`, `salas`, `categorias`, `platos`), dejando intactas las tablas transaccionales.
  3. Exigir una confirmación destructiva estricta con código interactivo (`escriba SI para proceder`) antes de ejecutar cualquier operación masiva, y abortar obligatoriamente si el respaldo previo no se generó de forma íntegra y verificada.

---

#### Fallo 30: Corrupción de integridad referencial e inserción de claves foráneas huérfanas en `plato_categoria` hacia categorías inexistentes
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `BD RESTAURANTE 2026.sql`: Líneas 290-305 (`categorias`), Líneas 315-410 (`plato_categoria`).
  - `src/Modelo/PlatosDao.java`: Líneas 207-208, 231-232.
  - `src/Modelo/CategoriaDao.java`: Líneas 12-25.
- **Código Problemático:**
  ```sql
  -- BD RESTAURANTE 2026.sql: Inserción en categorias (solo crea IDs 1, 3, 4, 5, 6, 7, 8)
  INSERT INTO `categorias` (`id`, `nombre`, `color`, `orden`) VALUES 
  (1,'General','#6B7280',0),
  (3,'PESCADO','#2E9E4F',1),
  (4,'AVES','#2563EB',2),
  (5,'MARISCOS','#E9B10C',3),
  (6,'CARNES','#4F46E5',4),
  (7,'PASTAS','#0F9D9D',5),
  (8,'GASEOSAS','#E1489A',6);
  ...
  -- BD RESTAURANTE 2026.sql: Inserción en plato_categoria apuntando a IDs inexistentes!
  INSERT INTO `plato_categoria` (`nombre_clave`, `id_categoria`) VALUES 
  ('arepas con queso a la plancha', 10), -- Categoría 10 NO EXISTE en categorias
  ('arroz chino con camaron', 14),       -- Categoría 14 NO EXISTE en categorias
  ('arroz chino especial', 14),          -- Categoría 14 NO EXISTE en categorias
  ('batido de piña', 9),                 -- Categoría 9 NO EXISTE en categorias
  ('batido durasno', 9),                 -- Categoría 9 NO EXISTE en categorias
  ('cerveza', 11),                       -- Categoría 11 NO EXISTE en categorias
  ('combo pollo 1', 12),                 -- Categoría 12 NO EXISTE en categorias
  ('ensalada cesar', 15);                -- Categoría 15 NO EXISTE en categorias
  ```
- **Demostración del Escenario de Falla:**
  1. Al restaurar `BD RESTAURANTE 2026.sql`, se apagan temporalmente las comprobaciones foráneas (`FOREIGN_KEY_CHECKS=0`), permitiendo insertar registros en `plato_categoria` con `id_categoria` iguales a `9, 10, 11, 12, 14, 15`.
  2. La tabla `categorias` únicamente contiene registros para los identificadores `1, 3, 4, 5, 6, 7, 8`. No existen las categorías correspondientes a desayunos, bebidas naturales, licores ni comida china.
  3. Cuando el operador abre el terminal de venta (POS split-screen) o la pantalla de catálogo de platos, `PlatosDao.listarPlatosParaVenta` ejecuta:
     ```sql
     LEFT JOIN plato_categoria pc ON pc.nombre_clave = p.nombre_clave
     LEFT JOIN categorias c ON c.id = pc.id_categoria
     ```
  4. Para todos los platos que apuntan a IDs de categorías huérfanas (`9, 10, 11, 12, 14, 15`), la unión con `categorias` falla silenciosamente, retornando `c.nombre = NULL` y `c.color = NULL`.
  5. En `PanelAtencionMesasSplit`, estos platos aparecen sin etiqueta de categoría ("Sin categoría"), el filtro rápido por categoría no los lista y la botonera dinámica de categorías no incluye los botones correspondientes (Bebidas, Cervezas, Combos, etc.), imposibilitando al mesonero encontrar y comandear dichos productos por sección.
  6. Si en el futuro cualquier script o migrador JDBC intenta añadir formalmente una restricción de clave foránea `FOREIGN KEY (id_categoria) REFERENCES categorias(id)`, MySQL abortará con error `1452: Cannot add or update a child row: a foreign key constraint fails`.
- **Impacto Real:** Integridad referencial corrompida, degradación funcional de la interfaz táctil del POS y riesgo de aborto en migraciones de esquema futuras.
- **Recomendación Técnica:**
  Completar el volcado de la tabla `categorias` en `BD RESTAURANTE 2026.sql` incluyendo todas las categorías referenciadas (IDs 9, 10, 11, 12, 14, 15) con sus respectivos nombres y colores, o reasignar los platos a categorías válidas antes de exportar el volcado.

---

#### Fallo 31: Desincronización horaria (Timezone Mismatch UTC vs Local) y mutación destructiva de fechas en `PlatosDao.asegurarCatalogoPlatosVigente()`
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PlatosDao.java`: Líneas 18-31, Líneas 221, 245.
  - `src/Vista/PanelAtencionMesasSplit.java`: Líneas 1045-1048.
- **Código Problemático:**
  ```java
  // src/Modelo/PlatosDao.java: Líneas 18-31 (commit 5558259)
  public PlatosDao() {
      this(new ProveedorConexionJdbc());
      asegurarCatalogoPlatosVigente();
  }

  public void asegurarCatalogoPlatosVigente() {
      String sql = "UPDATE platos SET fecha = CURRENT_DATE() WHERE activo = 1 AND (fecha < CURRENT_DATE() OR fecha IS NULL)";
      try (Connection conexion = conexiones.getConnection();
              PreparedStatement ps = conexion.prepareStatement(sql)) {
          ps.executeUpdate();
      } catch (SQLException ex) {
          LOGGER.log(java.util.logging.Level.FINE, "No se pudo sincronizar fecha del catalogo de platos: " + ex.getMessage());
      }
  }
  ```
  ```java
  // src/Vista/PanelAtencionMesasSplit.java: Líneas 1045-1048
  String hoy = java.time.LocalDate.now().toString();
  List<Platos> platos = platosControlador.listarPlatosParaVenta(filtro, hoy);
  ```
  ```sql
  -- src/Modelo/PlatosDao.java: Línea 221 y Línea 245
  WHERE p.fecha = ? AND p.activo = 1 AND p.precio_clasificado = 1
  ```
- **Demostración del Escenario de Falla:**
  1. En entornos Dockerizados y servidores Linux estándar, la base de datos MySQL opera configurada en zona horaria UTC (`@@system_time_zone = 'UTC'`).
  2. En el huso horario local de Venezuela (UTC-4), a las 8:01 PM de la noche del 17 de Octubre de 2026, la hora en el servidor MySQL es `2026-10-18 00:01:00 UTC`.
  3. En ese instante, `CURRENT_DATE()` en MySQL evalúa a `'2026-10-18'` (el día de mañana).
  4. Al inicializarse `PlatosDao()`, `asegurarCatalogoPlatosVigente()` ejecuta `UPDATE platos SET fecha = CURRENT_DATE()`, actualizando la fecha de todos los platos activos en la base de datos a `'2026-10-18'`.
  5. Simultáneamente, la aplicación cliente en la computadora del restaurante ejecuta Java con la hora local de Venezuela (UTC-4, 8:01 PM del 17 de Octubre). En `PanelAtencionMesasSplit.java:1046`, `LocalDate.now().toString()` genera `'2026-10-17'`.
  6. El panel de atención solicita los platos mediante `listarPlatosParaVenta(filtro, "2026-10-17")`.
  7. La consulta SQL evalúa estrictamente `WHERE p.fecha = '2026-10-17'`.
  8. Como todos los platos fueron mutados a `'2026-10-18'`, la consulta retorna **cero (0) platos**.
  9. En pleno horario pico de servicio de cenas (entre las 8:00 PM y las 11:59 PM), el menú completo desaparece de la pantalla táctil de todas las terminales de los mesoneros.
  10. Además, ejecutar una mutación de base de datos masiva y sin índice (`UPDATE platos`) dentro del constructor por defecto de una clase DAO transgrede el principio de responsabilidad única, genera bloqueos de tabla exclusivos inesperados y destruye la fecha histórica de incorporación de los platos al menú.
- **Impacto Real:** Denegación de servicio operativa total en horario nocturno (8:00 PM - 11:59 PM) y bloqueo de comandeo en el salón.
- **Recomendación Técnica:**
  1. Desacoplar el filtrado de venta de la columna `fecha` de la tabla `platos`: un plato activo (`activo = 1`) debe estar disponible para la venta sin importar la fecha en que fue creado o modificado.
  2. Eliminar la llamada a `asegurarCatalogoPlatosVigente()` del constructor de `PlatosDao()`.

---

#### Fallo 32: Violación del ciclo de vida de componentes Swing y condiciones de carrera concurrentes en constructor de `Sistema.java`
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 885-889, Líneas 898-910, Línea 101, Líneas 9736-9740.
- **Código Problemático:**
  ```java
  // src/Vista/Sistema.java: Líneas 885-889 (commit 5558259)
  configurarTabCategorias();
  ListarPlatos(TablePlatos, "");
  ListarPlatos(tblTemPlatos, "");
  if (panelSplitScreen != null) {
      panelSplitScreen.refrescarPlatos(null);
  }

  jTabbedPane1.addChangeListener(e -> { ... });
  configurarObservadoresVistas();
  aplicarMedidaEstandarBotones(getContentPane(), new java.util.HashSet<>());
  aplicarEncabezados();
  configurarVentanaResponsiva();
  configurarTodasLasTablas(getContentPane());
  ocultarColumnasId(); // IDs fuera de la vista antes de repartir anchos
  configurarTablaSalasProporcional();
  ...
  configurarColumnasProporcionales(tblTemPlatos, ...);
  ```
  ```java
  // src/Vista/Sistema.java: Línea 101 y Línea 9736
  DefaultTableModel modelo = new DefaultTableModel();
  ...
  private void mostrarPlatosEnTabla(JTable tabla, List<Platos> Listar) {
      modelo = (DefaultTableModel) tabla.getModel();
      modelo.setRowCount(0);
      ...
  ```
- **Demostración del Escenario de Falla:**
  1. En las líneas 885-889 del constructor de `Sistema.java`, se invoca de manera anticipada `ListarPlatos(TablePlatos, "")`, `ListarPlatos(tblTemPlatos, "")` y `panelSplitScreen.refrescarPlatos(null)`.
  2. Cada una de estas llamadas despacha de inmediato tareas asíncronas en segundo plano (`new ListaPlatosSwingWorker(...).execute()`).
  3. Estas tareas se ejecutan mientras el constructor de `Sistema.java` continúa en el Event Dispatch Thread (EDT) configurando la estructura básica de la interfaz: `aplicarEncabezados()`, `configurarTodasLasTablas()`, `ocultarColumnasId()` y `configurarColumnasProporcionales()`.
  4. La variable `modelo` en `Sistema.java` (línea 101) es una propiedad de instancia compartida mutable (`DefaultTableModel modelo = new DefaultTableModel();`).
  5. Cuando los SwingWorkers asíncronos finalizan en paralelo, sus callbacks de finalización en el EDT invocan `mostrarPlatosEnTabla(tabla, platos)`:
     - El primer callback asigna `modelo = (DefaultTableModel) TablePlatos.getModel()`.
     - El segundo callback asigna `modelo = (DefaultTableModel) tblTemPlatos.getModel()`.
  6. Si cualquiera de estos callbacks se procesa intercalado con la inicialización de `Sistema`, el campo `modelo` apunta a la tabla equivocada.
  7. Asimismo, si `mostrarPlatosEnTabla` puebla filas antes de que `ocultarColumnasId()` haya reconfigurado el `TableColumnModel` de `tblTemPlatos` y `TablePlatos`, las dimensiones proporcionales calculadas en `configurarColumnasProporcionales` operan sobre modelos con índices desfasados o causan excepciones `ArrayIndexOutOfBoundsException` / visualización indebida de la columna de ID interno ante el usuario final.
- **Impacto Real:** Inestabilidad gráfica aleatoria al iniciar sesión, desajuste permanente en los anchos de columnas de platos y riesgo de congelamiento de la interfaz de usuario Swing.
- **Recomendación Técnica:**
  Mover la carga inicial de datos (`ListarPlatos`, `refrescarPlatos`) al final absoluto del constructor de `Sistema`, o delegarla al momento en que el usuario activa la pestaña respectiva (lazy-loading mediante `addChangeListener`), y encapsular los modelos de tabla como variables locales dentro de cada método en lugar de reutilizar el campo mutable compartido `this.modelo`.

---

#### Fallo 33: Omisión de columna virtual e índice único de Cierre Z en el migrador JDBC automatizado (`MigradorEsquemaJdbc.java`)
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/infraestructura/MigradorEsquemaJdbc.java`: Líneas 30, 49-51, Líneas 1050-1090.
  - `BD RESTAURANTE 2026.sql`: Líneas 150-165.
  - `src/Modelo/CierreCajaDao.java`: Líneas 688-690.
- **Código Problemático:**
  ```java
  // src/infraestructura/MigradorEsquemaJdbc.java: Líneas 30, 49-51
  private static final int VERSION_ESQUEMA = 12;
  ...
  private static final String DEFINICION_MIGRACION_V12 = "v12:detalle_pedidos_aplica_iva_not_null";
  private static final String CHECKSUM_MIGRACION_V12 = calcularChecksum(DEFINICION_MIGRACION_V12);
  private static final String NOMBRE_MIGRACION = "sincronizacion_esquema_v12";
  ```
  ```java
  // src/Modelo/CierreCajaDao.java: Líneas 688-690
  } catch (SQLException ex) {
      con.rollback();
      if (ex.getErrorCode() == 1062 || "23000".equals(ex.getSQLState())) {
          throw ErrorAplicacionException.validacion("ya existe un cierre Z registrado para la fecha " + fechaJornada + ".");
      }
      throw ex;
  }
  ```
- **Demostración del Escenario de Falla:**
  1. En el commit `ffd2065`, se implementó la solución para el fallo F-26 agregando a nivel de esquema SQL la columna generada `fecha_jornada_z` y el índice único `uq_cierres_caja_z_fecha` sobre la tabla `cierres_caja`.
  2. Dicho cambio fue agregado a `BD.sql` y `actualizar_bd.sql`, pero fue omitido por completo de `MigradorEsquemaJdbc.java`, el cual quedó congelado en la versión 12 (`sincronizacion_esquema_v12`).
  3. Asimismo, el volcado oficial de distribución `BD RESTAURANTE 2026.sql` (commit `5b27257`) NO posee la columna `fecha_jornada_z` ni el índice `uq_cierres_caja_z_fecha`.
  4. Cuando un cliente instala o actualiza el sistema utilizando los mecanismos estándar automatizados (`Restaurante.jar --migrate-db`, `actualizar_datos_cliente.bat` o el arranque normal de la aplicación), `MigradorEsquemaJdbc` reporta que el esquema ya está en su última versión (v12) y NO agrega la columna ni la restricción de unicidad a la base de datos.
  5. Como consecuencia directa, la tabla `cierres_caja` en el servidor del cliente carece de la restricción física `UNIQUE KEY uq_cierres_caja_z_fecha`.
  6. El bloque de control de concurrencia en `CierreCajaDao.java:688` (que espera el código de error `1062` de MySQL ante inserciones simultáneas) queda totalmente inoperante e inservible, permitiendo la generación de Cierres Z duplicados ante emisiones concurrentes.
- **Impacto Real:** Ruptura de la invariante legal NM-01 de cierre único diario en todas las instalaciones que dependen de la migración automática o del volcado de cliente.
- **Recomendación Técnica:**
  Elevar la versión en `MigradorEsquemaJdbc.java` a `VERSION_ESQUEMA = 13`, implementando el método `migrarEsquemaV13` para incorporar de manera idempotente la columna `fecha_jornada_z` y el índice `uq_cierres_caja_z_fecha`, y sincronizar su definición en `BD RESTAURANTE 2026.sql`.

---

#### Fallo 34: Conflicto monetario cruzado en líneas huérfanas: `PanelAtencionMesasSplit` fuerza `USD + IVA` mientras `PreciosPedidosDao` fuerza `VES sin IVA`
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Vista/PanelAtencionMesasSplit.java`: Líneas 1012-1014.
  - `src/Modelo/PreciosPedidosDao.java`: Líneas 321-325 (commit `5d989c3`).
- **Código Problemático:**
  ```java
  // src/Vista/PanelAtencionMesasSplit.java: Líneas 1012-1014
  platoDummy.setMonedaBase(d.getMonedaBase() != null ? d.getMonedaBase() : "USD");
  platoDummy.setAplicaIva(d.getAplicaIva() == null || d.getAplicaIva());
  platoDummy.setPrecioIncluyeIva(Boolean.TRUE.equals(d.getPrecioIncluyeIva()));
  ```
  ```java
  // src/Modelo/PreciosPedidosDao.java: Líneas 321-325 (commit 5d989c3)
  det.setPrecioCongelado(true);
  if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) det.setMonedaBase("VES");
  if (det.getAplicaIva() == null) det.setAplicaIva(false);
  if (det.getPrecioIncluyeIva() == null) det.setPrecioIncluyeIva(false);
  ```
- **Demostración del Escenario de Falla:**
  1. Existe una comanda abierta o en edición con una línea de producto legado cuyo `moneda_base` es `NULL` (valor nominal: `precio = 25.00`).
  2. Si el mesonero abre la mesa en la pantalla dividida (Split POS), `PanelAtencionMesasSplit.java:1012` evalúa que `moneda_base == null` y fija en memoria `monedaBase = "USD"` y `aplicaIva = true`.
  3. En ese estado, la línea es valorada en `$25.00 USD + 16% IVA = $29.00 USD`, equivalentes a **Bs. 25,523.48** (a tasa 880.12).
  4. Si mientras la mesa sigue abierta, un administrador actualiza el precio de cualquier plato en el catálogo general, se dispara el recálculo en segundo plano de `PreciosPedidosDao.actualizarPrecios()`.
  5. En `PreciosPedidosDao.java:323`, la corrección introducida en el commit `5d989c3` (para resolver el fallo F-24) evalúa la misma línea huérfana y le asigna obligatoriamente `monedaBase = "VES"` y `aplicaIva = false`.
  6. La línea es reescrita en la base de datos como **Bs. 25.00** exento (equivalentes a **$0.028 USD**).
  7. Si posteriormente el mesonero añade una bebida a la mesa y pulsa "Enviar a Cocina" en el Split POS, `guardarComandaActual` lee el objeto `platoDummy` del carrito (que tiene `USD` y `aplicaIva = true`) y vuelve a sobrescribir la línea en la base de datos como **$25.00 USD + IVA**.
  8. El monto de la cuenta del cliente muta de forma descontrolada entre $29.00 USD y $0.02 USD (una discrepancia de más del 100,000%) según el orden en que interactúan el mesonero y el administrador.
- **Impacto Real:** Incoherencia monetaria extrema, cobros impredecibles y potencial desfalco o sobrefacturación masiva a clientes.
- **Recomendación Técnica:**
  Establecer una única regla canónica centralizada para la resolución de líneas desclasificadas en `CalculoMonetarioPedido`. Si un ítem carece de `moneda_base`, ninguna pantalla ni proceso de recálculo debe asignar arbitrariamente defaults contrapuestos; el sistema debe heredar la moneda preponderante de la cabecera del pedido o requerir su clasificación explícita antes de permitir la edición.

---

#### Fallo 35: Distorsión visual en grilla `tableFinalizar`: columna "Subtotal" computa e imprime el total con IVA incluido en lugar de la base imponible
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 10656-10658, Línea 10676.
  - `src/Servicio/CalculoMonetarioPedido.java`: Líneas 74, 96, Línea 114.
- **Código Problemático:**
  ```java
  // src/Vista/Sistema.java: Líneas 10656-10658, Línea 10676
  precioUsd = det.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
  subtotalPlatoUsd = lin.totalUsdReferencia();
  ...
  ob[3] = "$ " + String.format(java.util.Locale.US, "%.2f", precioUsd) + " (" + precioBs + ")";
  ob[4] = "$ " + String.format(java.util.Locale.US, "%.2f", subtotalPlatoUsd) + " (" + subtotalBsStr + ")";
  ```
  ```java
  // src/Servicio/CalculoMonetarioPedido.java: Líneas 74, 96
  totalFuente = baseFuente.add(ivaFuente).setScale(ESCALA_MONEDA);
  ...
  totalUsdReferencia = totalFuente; // Contiene el monto CON IVA!
  ```
- **Demostración del Escenario de Falla:**
  1. Un cliente ordena 2 unidades de un plato cuyo precio unitario base es `$10.00 USD` (gravado con 16% de IVA, precio sin IVA incluido).
  2. En la pantalla de cobro y finalización de pedido (`Sistema.java:cargarDetallesPedidoEnFinalizar`), se llena la grilla `tableFinalizar`.
  3. La columna 3 ("Precio") muestra: `$ 10.00 (Bs. 8,801.20)`.
  4. Para la columna 4 ("Subtotal"), el código toma `subtotalPlatoUsd = lin.totalUsdReferencia()`.
  5. En `CalculoMonetarioPedido.java:74`, para ítems con IVA excluido, `totalFuente` se calcula como `baseFuente ($20.00) + ivaFuente ($3.20) = $23.20`.
  6. La columna "Subtotal" de la fila muestra visualmente: `$ 23.20 (Bs. 20,418.78)`.
  7. El cajero y el cliente observan en la tabla: `Cantidad: 2`, `Precio Unitario: $10.00`, `Subtotal: $23.20`. Aritméticamente, `2 * 10.00` es $20.00, por lo que el cliente reclama que el subtotal está adulterado.
  8. Para mayor confusión, en los totales al pie de la tabla, el resumen fiscal muestra: `Subtotal: $20.00`, `IVA 16%: $3.20`, `Total: $23.20`.
  9. La suma de los valores mostrados en la columna "Subtotal" de las filas ($23.20) no cuadra con el campo "Subtotal" del pie de página ($20.00).
- **Impacto Real:** Desconfianza del cliente, fricción en el punto de cobro y percepción de cobro inflado indebido.
- **Recomendación Técnica:**
  En `Sistema.java:10657`, asignar a `subtotalPlatoUsd` la base imponible aditiva `lin.baseFuente().add(lin.exentoFuente())` en lugar de `lin.totalUsdReferencia()`, garantizando que la columna "Subtotal" represente estrictamente el importe neto antes de impuestos.

---

#### Fallo 36: Fuga de mesas cerradas pendientes de pago en turnos nocturnos de Cierre Z por filtrado estático de `fecha` en `sqlPendientes`
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 170-176.
- **Código Problemático:**
  ```sql
  -- src/Modelo/CierreCajaDao.java: Líneas 170-176
  // 2. Pedidos pendientes (abiertos y cerrados pendientes de pago)
  String sqlPendientes = """
      SELECT COUNT(*) AS cant,
             COALESCE(SUM(CASE WHEN cerrada_en IS NOT NULL THEN 1 ELSE 0 END), 0) AS cerradas_pendientes
      FROM pedidos
      WHERE estado = 'PENDIENTE'
        AND (fecha LIKE ? OR DATE(fecha) = ?)
      """;
  ```
  ```sql
  -- Comparación con sqlTotales (Línea 136):
  WHERE estado = 'FINALIZADO'
    AND (COALESCE(cerrada_en, fecha) LIKE ? OR DATE(COALESCE(cerrada_en, fecha)) = ?)
  ```
- **Demostración del Escenario de Falla:**
  1. En el commit `4865773` (solución de los fallos F-21, F-25 y F-27), se corrigió el filtro de pedidos finalizados en `sqlTotales`, `sqlMetodos`, `sqlSalas` y `sqlEfectivo` para utilizar `COALESCE(p.cerrada_en, p.fecha)`, permitiendo incluir pedidos que iniciaron antes de medianoche y finalizaron en la madrugada.
  2. Sin embargo, en `sqlPendientes` (líneas 170-176), la consulta continuó evaluando estrictamente `(fecha LIKE ? OR DATE(fecha) = ?)`.
  3. Supóngase una mesa que fue abierta el día 16 de Octubre a las 23:15 (`fecha = '2026-10-16 23:15:00'`).
  4. Los comensales piden la cuenta a la 1:30 AM del día 17 de Octubre. El mesonero emite la pre-cuenta, marcando `cerrada_en = '2026-10-17 01:30:00'` y dejando el pedido en estado `PENDIENTE`.
  5. Al terminar el turno de la noche a las 2:00 AM del 17 de Octubre, el administrador ejecuta el Cierre Z para la jornada `'2026-10-17'`.
  6. La consulta `sqlPendientes` busca registros con `DATE(fecha) = '2026-10-17'`.
  7. La mesa nocturna cerrada pendiente de cobro es ignorada porque su `fecha` de apertura fue `'2026-10-16'`.
  8. El sistema reporta falsamente `mesasCerradasPendientesPago = 0` y `pedidosPendientes = 0`.
  9. El Cierre Z es emitido y bloquea la jornada fiscal, a pesar de que existe una mesa que consumió y cerró atención durante ese turno sin haber sido cobrada.
- **Impacto Real:** Evasión inadvertida de controles de auditoría en cierres nocturnos y desfase en la conciliación de turnos.
- **Recomendación Técnica:**
  Homogeneizar el predicado en `sqlPendientes` sustituyendo `fecha` por `COALESCE(cerrada_en, fecha)`, garantizando que cualquier pedido pendiente o con pre-cuenta emitida en la jornada sea detectado rigurosamente.

---

#### Fallo 37: Asimetría tributaria y cambiaria en tickets y facturas PDF por cálculo dispar de precio unitario en platos VES
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Servicio/GeneradorPdfPedido.java`: Líneas 591-608 (Ticket 80mm) y 846-863 (Factura A4).
- **Código Problemático:**
  ```java
  // src/Servicio/GeneradorPdfPedido.java: Líneas 590-608 (Ticket 80mm)
  CalculoMonetarioPedido.CalculoLinea linea = calc.lineas().get(i);
  if ("VES".equalsIgnoreCase(linea.monedaBase())) {
      subtotalBs = linea.totalVes();
      subtotalUsd = linea.totalUsdReferencia();
      precioBs = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
      precioUsd = subtotalUsd.divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
  } else {
      subtotalUsd = linea.totalUsdReferencia();
      subtotalBs = linea.totalVes();
      precioUsd = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
      precioBs = precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
  }

  PdfPCell cUnit = new PdfPCell(new Phrase("$" + String.format(java.util.Locale.US, "%.2f", precioUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", precioBs) + ")", cuerpo));
  PdfPCell cTot = new PdfPCell(new Phrase("$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs) + ")", cuerpo));
  ```
  ```java
  // Idéntica lógica en Factura A4 (Líneas 845-863):
  tabla.addCell("$" + String.format(java.util.Locale.US, "%.2f", precioUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", precioBs) + ")");
  tabla.addCell("$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs) + ")");
  ```
- **Demostración del Escenario de Falla:**
  1. Un restaurante registra un plato con moneda base `VES`, precio nominal `Bs. 500.00`, gravado con IVA 16% (`aplica_iva = true`, `precio_incluye_iva = false`).
  2. Tasa de cambio oficial del día: `Bs. 36.5000 / USD`.
  3. Al comensal se le factura 1 unidad de este plato.
  4. En `CalculoMonetarioPedido.calcularLinea`:
     - `baseFuente = 500.00`, `ivaFuente = 80.00`, `totalFuente = 580.00`.
     - `baseVes = 500.00`, `ivaVes = 80.00`, `totalVes = 580.00`.
     - `totalUsdReferencia = 580.00 / 36.5000 = $ 15.89` (con IVA incluido).
  5. En `GeneradorPdfPedido`:
     - `precioBs = detalle.getPrecioDecimal() = Bs. 500.00` (precio NETO sin IVA).
     - `precioUsd = subtotalUsd.divide(1) = 15.89 / 1 = $ 15.89` (precio BRUTO con IVA).
     - La columna `P. Unit. ($ / Bs.)` imprime: `$ 15.89 (Bs. 500.00)`.
  6. **Aritmética contradictoria en el comprobante fiscal:**
     - Al convertir `$ 15.89 * 36.5000`, resulta `Bs. 580.00`, NO `Bs. 500.00` (descuadre de Bs. 80.00 correspondientes al IVA).
     - Al convertir `Bs. 500.00 / 36.5000`, resulta `$ 13.70`, NO `$ 15.89` (descuadre de $ 2.19 de IVA).
  7. **Contraste con ítems en USD:** Para un ítem en USD a `$ 10.00` (líneas 599-600), `precioUsd = $ 10.00` y `precioBs = Bs. 365.00` (ambos netos sin IVA). Al mezclar un producto en USD con uno en VES en la misma factura, el producto en USD muestra su precio unitario sin IVA y el de VES muestra su precio unitario en USD con IVA y en Bs sin IVA.
- **Impacto Real:** Distorsión y falsedad documental en comprobantes de pago y facturas emitidas. Pérdida de transparencia ante auditorías tributarias y quejas de clientes por tarifas unitarias infladas en dólares frente a su denominación en bolívares.
- **Recomendación Técnica:**
  Para ítems en VES, derivar consistentemente el precio unitario antes de impuestos:
  `precioUsd = (subNetoBs.divide(tasa, 8, RoundingMode.HALF_UP)).divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);`
  O bien, si la política del documento es reflejar precios con IVA incluido, calcular `precioBs = subtotalBs.divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP)` de manera simétrica para ambas monedas.

---

#### Fallo 38: Descuadre aditivo en PDFs por divergencia entre `linea.totalUsdReferencia()` y `calc.totalUsd()`
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Servicio/GeneradorPdfPedido.java`: Líneas 592-597, 638-641, 733-735 (Ticket 80mm); Líneas 847-853, 872-875, 966-968 (Factura A4).
  - `src/Servicio/CalculoMonetarioPedido.java`: Líneas 89, 177-178, 289-292.
- **Código Problemático:**
  ```java
  // src/Servicio/GeneradorPdfPedido.java: Líneas 593, 597 (renglones de la tabla)
  subtotalUsd = linea.totalUsdReferencia();
  ...
  PdfPCell cTot = new PdfPCell(new Phrase("$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + ...));
  tabla.addCell(cTot);
  ```
  ```java
  // src/Servicio/GeneradorPdfPedido.java: Líneas 638-639, 733 (total al pie del documento)
  BigDecimal totalUsd = pedido.getTotalDecimal() != null ? pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP)
          : (calc != null ? calc.totalUsd() : BigDecimal.ZERO.setScale(2));
  ...
  sb.append("Total USD: $").append(String.format(java.util.Locale.US, "%.2f", totalUsd)).append("\n");
  ```
  ```java
  // src/Servicio/CalculoMonetarioPedido.java: Línea 89 vs 289-292
  // En calcularLinea:
  totalUsdReferencia = convertirADolaresReferencia(totalVes, tasaNormalizada); // = round(totalVes / tasa)
  ...
  // En CalculoPedido:
  public BigDecimal totalUsd() {
      return subtotalUsd().add(ivaUsd()).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP); // = round(base/tasa) + round(iva/tasa)
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Supóngase un pedido con un producto en VES cuyo precio es `Bs. 10.00` (con IVA 16% incluido). Tasa de cambio: `Bs. 36.5000 / USD`.
  2. En `CalculoMonetarioPedido`:
     - `baseVes = 10.00 * 100 / 116 = Bs. 8.62`.
     - `ivaVes = 10.00 - 8.62 = Bs. 1.38`.
     - `linea.totalUsdReferencia() = 10.00 / 36.5000 = $ 0.27397 -> $ 0.27`.
  3. Al calcular los subtotales e impuestos del pedido aditivamente:
     - `baseUsd = 8.62 / 36.5000 = $ 0.23616 -> $ 0.24`.
     - `ivaUsd = 1.38 / 36.5000 = $ 0.03780 -> $ 0.04`.
     - `calc.totalUsd() = subtotalUsd ($0.24) + ivaUsd ($0.04) = $ 0.28`.
  4. Al emitir el PDF (Ticket o Factura A4):
     - La tabla imprime en el renglón del plato: `Cant: 1 | Plato: ... | Total: $ 0.27 (Bs. 10.00)`.
     - En el pie del ticket imprime:
       `Subtotal: $ 0.24`  
       `IVA (16.00%): $ 0.04`  
       `Total USD: $ 0.28`  
       `TOTAL A PAGAR (Bs.): Bs. 10.00`
  5. La suma de los renglones impresos en la tabla es `$ 0.27`, pero el total facturado impreso en el pie es `$ 0.28`. La factura se descuadra contra sí misma por 1 centavo. En pedidos con 10 o más líneas similares, la diferencia acumulada asciende a varios centavos.
- **Impacto Real:** Descuadre contable interno en documentos fiscales, incumplimiento del principio de integridad documental del SENIAT y objeción de comprobantes por inconsistencia aritmética entre detalle y pie.
- **Recomendación Técnica:**
  En `GeneradorPdfPedido`, para líneas en moneda VES, calcular el total de la línea en USD a partir de la suma de sus componentes fiscales convertidos:
  `subtotalUsd = convertirADolaresReferencia(linea.baseImponibleVes(), tasa).add(convertirADolaresReferencia(linea.exentoVes(), tasa)).add(convertirADolaresReferencia(linea.ivaVes(), tasa));`
  garantizando que la suma de los renglones coincida exactamente con `calc.totalUsd()`.

---

#### Fallo 39: Cierre Z en producción omite Base Imponible y Exento en BD, recurre a fallback inverso hardcodeado `16.00` y no persiste el desglose fiscal en `cierres_caja`
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 103-166 (`generarCierre`), 670-678 (`guardarCierre`).
  - `src/Modelo/CierreCaja.java`: Líneas 243-263 (`getBaseImponibleUsd`, `getBaseImponibleBs`).
  - `src/Servicio/GeneradorPdfCierre.java`: Líneas 151-160.
  - `BD.sql`: Líneas 475-499.
  - `actualizar_bd.sql`: Líneas 361-382.
- **Código Problemático:**
  ```sql
  -- src/Modelo/CierreCajaDao.java (Líneas 103-110 en sqlTotales de generarCierre):
  SELECT COUNT(*) AS cant,
         COALESCE(SUM(total), 0) AS total_usd,
         COALESCE(SUM(tot_bs), 0) AS total_bs,
         COALESCE(SUM(subtotal), 0) AS subtotal_usd,
         COALESCE(SUM(sub_bs), 0) AS subtotal_bs,
         COALESCE(SUM(iva_monto), 0) AS iva_usd,
         COALESCE(SUM(iv_bs), 0) AS iva_bs
  -- ¡No selecciona ni acumula base_imponible_usd, base_imponible_bs, exento_usd ni exento_bs!
  ```
  ```java
  // src/Modelo/CierreCaja.java (Líneas 243-250)
  public BigDecimal getBaseImponibleUsd() {
      if (baseImponibleUsd != null) return baseImponibleUsd;
      if (ivaUsd != null && ivaUsd.signum() > 0) {
          BigDecimal baseEst = ivaUsd.multiply(new BigDecimal("100")).divide(new BigDecimal("16.00"), 2, RoundingMode.HALF_UP);
          return baseEst.min(subtotalUsd != null ? subtotalUsd : baseEst).setScale(2, RoundingMode.HALF_UP);
      }
      return BigDecimal.ZERO.setScale(2);
  }
  ```
  ```sql
  -- src/Modelo/CierreCajaDao.java (Líneas 671-678 en guardarCierre)
  INSERT INTO cierres_caja (
      tipo, fecha_jornada, fecha_hora_emision, usuario_emisor,
      total_ventas_usd, total_ventas_bs,
      efectivo_esperado_bs, efectivo_declarado_bs, diferencia_bs, estado_conciliacion_bs,
      efectivo_esperado_usd, efectivo_declarado_usd, diferencia_usd, estado_conciliacion_usd,
      tasa_cambio, ruta_pdf
  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  -- ¡No persiste base imponible, exento, subtotal ni IVA!
  ```
- **Demostración del Escenario de Falla:**
  1. Al emitir el Cierre Z de la jornada fiscal, el sistema invoca `cierreCajaDao.generarCierre(fecha, TipoCierre.TOTAL, usuario)`.
  2. `generarCierre` ejecuta la consulta SQL `sqlTotales`. Como `sqlTotales` no consulta `base_imponible` ni `exento`, los campos `baseImponibleUsd`, `baseImponibleBs`, `exentoUsd` y `exentoBs` del objeto `cierre` permanecen como `null`.
  3. `GeneradorPdfCierre` invoca `cierre.getBaseImponibleUsd()` y `cierre.getBaseImponibleBs()`.
  4. Al ser nulos, se ejecuta el cálculo inverso estimado: `ivaUsd * 100 / 16.00`.
  5. **Falla 1 - Alícuotas distintas a 16%:** Si el restaurante aplica una alícuota de IVA reducida (ej. 8%), o exenta, o si la tasa legal del país se modifica mediante la configuración del sistema, el Cierre Z emitido divide rígidamente entre `16.00`, generando una base imponible adulterada y falsa en el informe oficial de cierre fiscal.
  6. **Falla 2 - Distorsión de centavos por redondeo individual:** Si 10 pedidos tuvieron consumos de `$ 1.05` con IVA de `$ 0.17`, el IVA acumulado en BD es `$ 1.70`. Al dividir `1.70 * 100 / 16.00`, la base imponible estimada es `$ 10.63` en lugar del valor real de `$ 10.50`, y el monto exento resultante se mutila en `$ 0.13`.
  7. **Falla 3 - Amputación de datos en `cierres_caja`:** Al persistir en la tabla `cierres_caja`, el `INSERT` no guarda ni subtotal, ni IVA, ni base imponible, ni exento. La tabla `cierres_caja` en MySQL carece de esas columnas. Si se requiere consultar o auditar cierres anteriores desde BD, la información tributaria no existe.
- **Impacto Real:** Severidad Crítica. Infracción tributaria formal en libros de ventas fiscales. Distorsión de bases imponibles y montos exentos en los reportes de Cierre Z exigidos por la ley, e imposibilidad de auditoría fiscal retroactiva en BD.
- **Recomendación Técnica:**
  1. Modificar `sqlTotales` en `CierreCajaDao.java` para calcular directamente desde `pedidos` (o uniendo con `detalle_pedidos`) la sumatoria real de `base_imponible_usd`, `base_imponible_bs`, `exento_usd` y `exento_bs`.
  2. Eliminar el divisor hardcodeado `new BigDecimal("16.00")` en `CierreCaja.java` y utilizar la alícuota configurada.
  3. Agregar las columnas `subtotal_usd`, `subtotal_bs`, `base_imponible_usd`, `base_imponible_bs`, `exento_usd`, `exento_bs`, `iva_usd`, `iva_bs` en la tabla `cierres_caja` (vía `configuracion_sistema` o migración estructurada) y persistirlas en `guardarCierre`.

---

#### Fallo 40: Discrepancia visual severa en `tableFinalizar`: Columna "Precio" muestra precio bruto y columna "Sub-Total" muestra base neta para platos con IVA incluido
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 11309-11326, 11343-11344 (`cargarDetallesPedidoEnFinalizar`).
- **Código Problemático:**
  ```java
  // src/Vista/Sistema.java: Líneas 11309-11326
  if (calcPantalla != null) {
      Servicio.CalculoMonetarioPedido.CalculoLinea lin = calcPantalla.lineas().get(i);
      BigDecimal subNetoFuente = lin.baseImponibleFuente().add(lin.exentoFuente());
      if ("VES".equalsIgnoreCase(lin.monedaBase())) {
          BigDecimal unitBs = det.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP); // <-- PRECIO CON IVA!
          BigDecimal unitUsd = (tasa != null && tasa.signum() > 0)
                  ? subNetoFuente.divide(tasa, 8, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(det.getCantidad()), 2, RoundingMode.HALF_UP) // <-- NETO SIN IVA!
                  : BigDecimal.ZERO.setScale(2);
          precioUsd = unitUsd;
          subtotalPlatoUsd = (tasa != null && tasa.signum() > 0)
                  ? subNetoFuente.divide(tasa, 2, RoundingMode.HALF_UP)
                  : BigDecimal.ZERO.setScale(2);
          precioBs = "Bs. " + String.format(java.util.Locale.US, "%.2f", unitBs);
      } else {
          precioUsd = det.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP); // <-- PRECIO CON IVA!
          subtotalPlatoUsd = subNetoFuente; // <-- NETO SIN IVA!
          precioBs = "Bs. " + String.format(java.util.Locale.US, "%.2f", precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP));
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Se comanda un plato con `precio_incluye_iva = true`, precio nominal `$ 116.00` (IVA 16% incluido, base neta = `$ 100.00`), cantidad = 1.
  2. En la pantalla de cobro y finalización (`Sistema.java:tableFinalizar`):
     - Para la columna 3 ("Precio"): `precioUsd = $ 116.00`, `precioBs = Bs. 4,234.00` (calculados con IVA incluido).
     - Para la columna 4 ("Sub-Total"): `subtotalPlatoUsd = $ 100.00`, `subtotalBs = Bs. 3,650.00` (calculados sin IVA, tras el ajuste del Fallo 35).
     - La fila visual muestra:
       `Cant: 1 | Precio: $ 116.00 (Bs. 4,234.00) | Sub-Total: $ 100.00 (Bs. 3,650.00)`
  3. El cajero y el cliente ven que para una sola unidad, el "Sub-Total" es inferior al "Precio Unitario" en `$ 16.00` / `Bs. 584.00`.
  4. Para productos en `VES` con IVA incluido (`Bs. 116.00`), la situación es aún más grave: la celda de Precio muestra `$ 2.74 (Bs. 116.00)`, donde el monto en dólares es neto ($2.74 * 36.5 = Bs. 100.00) y el monto entre paréntesis en bolívares es bruto (Bs. 116.00), rompiendo la paridad dentro de la misma celda.
- **Impacto Real:** Confusión y disputas con clientes en el momento del cobro en caja; percepción de incoherencia en el sistema contable del restaurante.
- **Recomendación Técnica:**
  En `cargarDetallesPedidoEnFinalizar`, si la columna "Sub-Total" representa la base neta antes de impuestos, la columna "Precio" debe representar simétricamente el precio unitario neto antes de impuestos:
  `unitBs = lin.baseImponibleVes().add(lin.exentoVes()).divide(BigDecimal.valueOf(det.getCantidad()), 2, RoundingMode.HALF_UP);`
  y para USD `precioUsd = subNetoFuente.divide(BigDecimal.valueOf(det.getCantidad()), 2, RoundingMode.HALF_UP);`.

---

#### Fallo 41: Pérdida de persistencia de `iva_porcentaje` en `detalle_pedidos` durante propagación de catálogo, provocando `ErrorAplicacionException` al reabrir comandas
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PreciosPedidosDao.java`: Líneas 352-356, 362-379.
  - `src/Servicio/CalculoMonetarioPedido.java`: Líneas 40, 220-230.
- **Código Problemático:**
  ```java
  // src/Modelo/PreciosPedidosDao.java: Líneas 352-356
  if (det.getIvaPorcentaje() == null) {
      det.setIvaPorcentaje(Boolean.TRUE.equals(det.getAplicaIva())
              ? ivaPorcentajeCabecera
              : BigDecimal.ZERO.setScale(2));
  }
  // ¡Se asigna en memoria pero NUNCA se ejecuta un UPDATE detalle_pedidos SET iva_porcentaje = ? en MySQL!
  ```
  ```java
  // src/Servicio/CalculoMonetarioPedido.java: Líneas 221-224
  private static BigDecimal validarIva(BigDecimal ivaPorcentaje) {
      if (ivaPorcentaje == null) {
          throw ErrorAplicacionException.validacion("El porcentaje de IVA de la línea no está clasificado.");
      }
      ...
  ```
- **Demostración del Escenario de Falla:**
  1. Un pedido pendiente contiene 2 líneas: la línea 1 corresponde al plato modificado en el catálogo, y la línea 2 es un plato ya clasificado (`moneda_base = 'USD'`, `aplica_iva = 1`, `precio_incluye_iva = 0`), pero cuyo campo `iva_porcentaje` en `detalle_pedidos` es `NULL` (ej. comanda migrada o guardada por un flujo que omitió el campo).
  2. El administrador actualiza el precio del plato 1 en el módulo de platos. Se invoca `modificarProductoYRecalcularPendientes`.
  3. Al iterar la línea 2 (línea 352), detecta `det.getIvaPorcentaje() == null` y ejecuta:
     `det.setIvaPorcentaje(ivaPorcentajeCabecera)`.
  4. La línea 2 no ejecuta `ps.executeUpdate()` porque el `sqlUpdateDetalle` (línea 286) está condicionado a `if (det.getIdPlato() != null && det.getIdPlato() == plato.getId())`.
  5. En memoria, `CalculoMonetarioPedido.calcularPedido` calcula los nuevos importes usando el objeto `det` con IVA no nulo. La cabecera `pedidos` se actualiza con éxito en BD (línea 366) y la transacción hace `con.commit()`.
  6. Sin embargo, en la base de datos MySQL, el registro `detalle_pedidos` de la línea 2 **permanece con `iva_porcentaje = NULL`**.
  7. Minutos después, el mesonero abre la mesa en el POS, o el cajero intenta cobrarla en `Sistema.java` o generar la pre-factura:
     - `PedidosDao.consultarPedido` o `GeneradorPdfPedido` lee la línea desde MySQL.
     - `det.getIvaPorcentaje()` es `null`.
     - `CalculoMonetarioPedido.validarIva` arroja fatalmente: `ErrorAplicacionException: El porcentaje de IVA de la línea no está clasificado`.
  8. La mesa queda completamente bloqueada: no se puede ver, no se puede cobrar y no se puede cerrar.
- **Impacto Real:** Bloqueo de comandas en servicio (Denegación de Servicio en mesas activas) al actualizar el catálogo.
- **Recomendación Técnica:**
  En `PreciosPedidosDao.java:352`, si `det.getIvaPorcentaje()` era nulo en BD, ejecutar inmediatamente un `UPDATE detalle_pedidos SET iva_porcentaje = ? WHERE id = ?` para garantizar la consistencia en el medio de almacenamiento antes de actualizar la cabecera.

---

#### Fallo 42: Sobre-escritura fiscal no autorizada en comanda activa por desacople entre `detalle_pedidos` y catálogo en `PanelAtencionMesasSplit`
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Vista/PanelAtencionMesasSplit.java`: Líneas 1070-1078 (`cargarDetallesPedidoExistente`), 1395-1426 (`recalcularTotales`).
- **Código Problemático:**
  ```java
  // src/Vista/PanelAtencionMesasSplit.java: Líneas 1395-1426
  Platos plato = platosPorId.get(idPlato);
  ...
  String mb = plato.getMonedaBase();
  boolean gravado = plato.isAplicaIva();
  boolean incIva = Boolean.TRUE.equals(plato.getPrecioIncluyeIva());

  DetallePedido det = new DetallePedido();
  det.setPrecioDecimal(precio);
  det.setCantidad(cant);
  det.setMonedaBase(mb);
  det.setAplicaIva(gravado);
  det.setPrecioIncluyeIva(incIva);
  det.setIvaPorcentaje(gravado ? ivaPorcentaje : BigDecimal.ZERO.setScale(2));
  detalles.add(det);
  ```
- **Demostración del Escenario de Falla:**
  1. Una mesa se sienta y ordena un producto que en ese momento estaba catalogado como exento de IVA (`aplica_iva = false`).
  2. Mientras los comensales comen, el administrador entra a la configuración de la empresa o a la gestión de platos y reclasifica el producto a gravado (`aplica_iva = true`, 16%).
  3. El mesonero entra a la mesa en el POS (`PanelAtencionMesasSplit`). Al cargar la comanda existente, `cargarDetallesPedidoExistente` añade la fila al carrito.
  4. `recalcularTotales()` no consulta la condición guardada en `detalle_pedidos`, sino que hace `platosPorId.get(idPlato)`, extrayendo `aplicaIva` del catálogo actual y asignando la tasa actual del sistema `sistema.getIvaPorcentajeActual()`.
  5. Los ítems de la comanda sufren un cambio fiscal inadvertido en pantalla: un pedido que debía cobrarse exento ahora se recalcula con 16% de IVA sobre la marcha, alterando el total a pagar sin que el comensal ni el mesonero hayan agregado o modificado consumos.
- **Impacto Real:** Inseguridad jurídica en comandas en curso y cobros imprevistos de impuestos sobre consumos iniciados bajo condiciones fiscales distintas.
- **Recomendación Técnica:**
  En `PanelAtencionMesasSplit`, almacenar en el modelo del carrito o en un mapa de metadatos de fila las condiciones fiscales reales persistidas (`monedaBase`, `aplicaIva`, `precioIncluyeIva`, `ivaPorcentaje`) leídas de `detalle_pedidos`, y solo recurrir al catálogo para nuevos platos recién agregados al carrito durante la sesión de atención.

---

#### Fallo 43: Desincronización en `ajusteCambioBs` entre líneas individuales (`CalculoLinea`) y la cabecera del pedido (`CalculoPedido`)
- **Severidad:** **BAJA**
- **Archivo y Líneas:**
  - `src/Servicio/CalculoMonetarioPedido.java`: Líneas 105-107 vs 180-182.
- **Código Problemático:**
  ```java
  // src/Servicio/CalculoMonetarioPedido.java: Líneas 105-107 en calcularLinea
  BigDecimal ajusteCambioBs = totalVes.subtract(
          totalUsdReferencia.multiply(tasaNormalizada).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP))
          .setScale(ESCALA_MONEDA);
  ```
  ```java
  // src/Servicio/CalculoMonetarioPedido.java: Líneas 180-182 en calcularPedido
  BigDecimal ajusteCambioBs = totalVes.subtract(
          totalUsdFinal.multiply(tasaNormalizada).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP))
          .setScale(ESCALA_MONEDA);
  ```
- **Demostración del Escenario de Falla:**
  1. En la corrección del Fallo 27, se actualizó la fórmula del `ajusteCambioBs` del pedido completo para utilizar `totalUsdFinal` (suma aditiva de subtotal e IVA).
  2. Sin embargo, en el cálculo por línea (`calcularLinea`, línea 105), la fórmula quedó intacta computando contra `totalUsdReferencia`.
  3. Para una línea en VES donde el redondeo de los componentes fiscales hace que `totalUsdFinal != totalUsdReferencia` (ej. $0.28 vs $0.27 a tasa 36.5000):
     - La línea reporta `ajusteCambioBs = 10.00 - (0.27 * 36.5) = +0.14 Bs.`
     - El pedido reporta `ajusteCambioBs = 10.00 - (0.28 * 36.5) = -0.22 Bs.`
  4. Los ajustes por cambio reportados a nivel de renglón no suman ni concuerdan en signo con el ajuste reportado a nivel de cabecera.
- **Impacto Real:** Incoherencia en análisis contables de diferencias en cambio al conciliar líneas de detalle con la cabecera.
- **Recomendación Técnica:**
  En `calcularLinea`, derivar `ajusteCambioBs` a partir de `subtotalUsdAditivo.add(ivaUsdAditivo)` de la línea para mantener sincronía con el cálculo del pedido.

---

#### Fallo 44: Emisión ciega de pre-factura sin validación fiscal previa en `GeneradorPdfPedido` permite imprimir comprobantes que luego son bloqueados en caja
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Servicio/GeneradorPdfPedido.java`: Líneas 124, 147, 161-171, 296-373.
- **Código Problemático:**
  ```java
  // src/Servicio/GeneradorPdfPedido.java: Líneas 124 y 147 (Factura final y con timestamp)
  public Path generar(Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
      validar(pedido, configuracion, detalles); // <-- Validación exhaustiva de subtotal, IVA y total!
      ...
  ```
  ```java
  // src/Servicio/GeneradorPdfPedido.java: Líneas 161-171 (Pre-Factura de cobro)
  public Path generarPreFactura(Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
      if (pedido == null || pedido.getId() <= 0) { ... }
      if (configuracion == null) { ... }
      if (detalles == null || detalles.isEmpty()) { ... }
      // ¡OMITE validar(pedido, configuracion, detalles)!
      ...
      generarArchivoPreFactura(dirDestino, salida, pedido, configuracion, detalles);
      return salida;
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Si una comanda en mesa presenta alguna ligera discrepancia histórica en `subtotal` o `iva_monto` con respecto a sus detalles (ej. comanda migrada o modificada concurrentemente):
  2. El mesonero presiona "Pre-Cuenta" o "Pre-Factura".
  3. `generarPreFactura` no ejecuta `validar(...)`, genera el archivo PDF y lo imprime en tickera térmica sin ninguna alerta.
  4. El mesonero entrega el ticket impreso al cliente en la mesa y éste paga el monto impreso.
  5. El cajero en el mostrador presiona "Finalizar / Cobrar Pedido" para emitir la factura fiscal legal.
  6. Se invoca `generar(...)` o `generarConTimestamp(...)`.
  7. El método `validar(...)` detecta la discrepancia fiscal y arroja: `ErrorAplicacionException: El total del pedido no coincide con sus detalles y cálculo de IVA`.
  8. El cobro y la emisión fiscal quedan bloqueados. El cliente ya pagó y se retiró con un comprobante provisional que el sistema no puede asentar como factura definitiva en la base de datos.
- **Impacto Real:** Bloqueo operativo en el punto de cobro con dinero ya recibido del comensal; riesgo fiscal por transacciones retenidas en pre-factura sin timbrado final.
- **Recomendación Técnica:**
  Invocar `validar(pedido, configuracion, detalles)` dentro de `generarPreFactura` previo a la creación del documento PDF, impidiendo la entrega de comprobantes físicos provisionales que no superen los mismos controles estrictos de la factura definitiva.

---

#### Fallo 45 (RT-01): Corrupción de persistencia fiscal por fórmula legacy que inflaba `iva_bs` con el monto exento
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 502-506, 601-625.
- **Código Problemático:**
  ```java
  // src/Modelo/PedidosDao.java: Fórmula histórica previa
  BigDecimal totalEnviado = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
  BigDecimal totalEsperado = preliminar.totalUsd();
  if (totalEnviado.compareTo(totalEsperado) != 0 && totalEnviado.compareTo(subtotalEsperado) != 0) { ... }
  // Y asignación histórica donde iva_bs = totalVes - baseVes en lugar de aislar ivaVes de líneas gravadas
  ```
- **Demostración del Escenario de Falla:**
  1. En comandas multimoneda que combinan productos gravados con productos exentos (ej. Pedidos 177 y 178 del registro histórico):
     - Base Imponible en Bs: Bs. 8,112.00 (IVA 16% = Bs. 1,297.92).
     - Monto Exento en Bs: Bs. 5,070.00.
     - Total en Bs: Bs. 14,479.92.
  2. La fórmula histórica ejecutaba `iva_bs = totalVes - baseVes` = 14,479.92 - 8,112.00 = **Bs. 6,367.92**.
  3. En lugar de registrar los Bs. 1,297.92 reales de impuesto, inyectaba los Bs. 5,070.00 de los consumos exentos íntegramente dentro de la columna `iva_bs`.
  4. La cabecera en MySQL quedaba desbalanceada: `subtotal_bs + iva_bs != total_bs` (Bs. 13,182.00 + Bs. 6,367.92 = Bs. 19,549.92 ≠ Bs. 14,479.92).
  5. En el comprobante impreso, el IVA efectivo calculado aparentaba ser del 48.3% en lugar del 16%, falseando la contabilidad fiscal del restaurante.
- **Impacto Real:** Corrupción de datos contables en la base de datos MySQL; riesgo de multas y sanciones tributarias severas por facturación fiscal con alícuotas distorsionadas.
- **Resolución y Certificación:**
  1. En `PedidosDao.validarYCompletarFiscalmente`, se erradicó la derivación por sustracción y se vinculó la cabecera directamente con `resultado.ivaVes()` proveniente del motor autoritativo `CalculoMonetarioPedido`.
  2. Se instituyó la regla de aditividad matemática estricta con tolerancia CERO (0.00):
     ```java
     if (pedido.getTotalDecimal().compareTo(pedido.getSubtotal().add(pedido.getIvaMonto())) != 0) {
         throw ErrorAplicacionException.validacion("Discrepancia aditiva en cabecera USD: total != subtotal + iva_monto");
     }
     if (pedido.getTotalBs().compareTo(pedido.getSubtotalBs().add(pedido.getIvaBs())) != 0) {
         throw ErrorAplicacionException.validacion("Discrepancia aditiva en cabecera Bs: total_bs != subtotal_bs + iva_bs");
     }
     ```
  3. Certificado en `SincronizacionPantallaBdCierreTest.testRechazoEstricto_PersistenciaConAditividadInvalida` y `testSincronizacionCuadruple_Pantalla_BD_PDF_CierreZ`.

---

#### Fallo 46 (RT-02): Divergencia asimétrica entre pantalla POS y documentos PDF impresos por precedencia ciega de cabecera
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Servicio/GeneradorPdfPedido.java`: Líneas 580-620, 670-730, 830-860.
  - `src/Vista/Sistema.java`: Líneas 11130-11175.
- **Código Problemático:**
  ```java
  // src/Servicio/GeneradorPdfPedido.java (comportamiento previo):
  BigDecimal ivaBs = pedido.getIvaBs() != null ? pedido.getIvaBs() : ...;
  // Priorizaba la columna de cabecera física aunque contuviera datos viciados,
  // mientras la pantalla recalculaba dinámicamente desde las líneas en memoria.
  ```
- **Demostración del Escenario de Falla:**
  1. El cajero abría la comanda en la pantalla de finalizar venta (`Sistema.java`). El sistema calculaba en caliente sobre los ítems del carrito y mostraba el IVA real correcto (ej. Bs. 1,297.92).
  2. El operador presionaba "Facturar" o "Reimprimir Ticket".
  3. `GeneradorPdfPedido.java` leía el campo `pedido.getIvaBs()` almacenado en la cabecera de la tabla `pedidos` (Bs. 6,367.92).
  4. El comprobante impreso (Ticket 80mm o Factura A4) mostraba un desglose de IVA y totales completamente diferente al que el cliente y el cajero veían en pantalla, generando desconfianza en el comensal y reclamos en el punto de cobro.
- **Impacto Real:** Asimetría de información entre el punto de venta y el documento tributario emitido al cliente; vulnerabilidad a auditorías fiscales por discrepancia comprobante vs sistema.
- **Resolución y Certificación:**
  1. En `GeneradorPdfPedido.java`, se eliminó la precedencia ciega de cabecera. Si los detalles están clasificados, los montos fiscales se derivan autoritativamente de `CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, tasa)`.
  2. Los subtotales por renglón se extraen de `linea.subtotalUsd(tasa)` y `linea.subtotalVes()`.
  3. La sección de totales del ticket y la factura se renderizan mediante `PdfPTable` alineada a la derecha con márgenes estrictos, imprimiendo simétricamente Subtotal, Base Imponible, Monto Exento, IVA, Tasa y Total a Pagar.
  4. Certificado mediante lectura forense de PDF con `PdfTextExtractor` en `SincronizacionPantallaBdCierreTest`.

---

#### Fallo 47 (RT-03): Contaminación masiva del Cierre Z por acumulación SQL directa de `p.iva_bs` histórico
- **Severidad:** **CRÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 120-135, 470-510.
- **Código Problemático:**
  ```java
  // src/Modelo/CierreCajaDao.java (sqlTotales previo):
  String sqlTotales = "SELECT COALESCE(SUM(p.iva_bs), 0) AS total_iva_bs FROM pedidos p ...";
  ```
- **Demostración del Escenario de Falla:**
  1. Al generar el Cierre Z de una jornada que contenía pedidos heterogéneos históricos viciados por la fórmula de RT-01, `CierreCajaDao` ejecutaba `SUM(p.iva_bs)` directamente sobre la columna de cabecera.
  2. En el corte del 16 de Octubre de 2026, el Cierre Z arrojó un total de IVA en Bolívares de Bs. 40,868.00 en lugar de los Bs. 30,728.00 reales de la jornada.
  3. Se produjo una inflación artificial de **+10,140.00 Bs. (+33%)** en el libro de ventas fiscal del día, induciendo a pagar impuestos indebidos al fisco.
- **Impacto Real:** Distorsión severa de las declaraciones fiscales obligatorias de cierre de jornada y sobrepago impositivo por inflación de IVA.
- **Resolución y Certificación:**
  1. En `CierreCajaDao.java`, se implementó el desglose fiscal analítico `sqlFiscal` y la acumulación por renglón en `generarCierreMemoria`.
  2. Se ejecutó la limpieza operativa integral (`limpiar_para_comenzar_hoy.sh`), purgando datos espurios de laboratorio y dejando el esquema en estado prístino con 119 platos vigentes.
  3. La regla de aditividad estricta CERO en `PedidosDao` asegura que ningún pedido nuevo pueda volver a persistir un `iva_bs` inconsistente con sus consumos.
  4. Certificado en `SincronizacionPantallaBdCierreTest:340-372` donde el Cierre Z cuadra al centavo con la BD física y el comprobante PDF.

---

#### Fallo 48 (RT-04): Inferencia fiscal inversa con divisor hardcodeado `16.00` y `COALESCE(iva_porcentaje, 16.00)` produciendo bases imponibles erróneas en Cierre Z
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/CierreCajaDao.java`: Líneas 223-255, 570-600.
- **Código Problemático:**
  ```java
  // src/Modelo/CierreCajaDao.java (generarCierreMemoria y sqlFiscal previo):
  baseUsd = cierre.getIvaUsd().multiply(new BigDecimal("100")).divide(new BigDecimal("16.00"), 2, RoundingMode.HALF_UP);
  // Y fallback ciego: COALESCE(p.iva_porcentaje, 16.00)
  ```
- **Demostración del Escenario de Falla:**
  1. En un restaurante con productos exentos o sujetos a alícuotas tributarias reducidas o variables, asumir un divisor fijo de 16.00% genera bases imponibles erróneas.
  2. Para comandas 100% exentas o con IVA 0%, la fórmula inversa fallaba o imputaba montos imprecisos, generando descalces en la ecuación patrimonial de cierre donde `subtotal != base_imponible + exento`.
- **Impacto Real:** Reportes de Cierre Z legalmente inconsistentes con la normativa tributaria al no poder reconciliar el subtotal gravado con el exento.
- **Resolución y Certificación:**
  1. En `generarCierreMemoria`, se eliminó el divisor fijo `16.00` y se evalúa la alícuota real `p.getIvaPorcentaje()` de cada comanda.
  2. En `consultarCierre`, se implementó una regla de reconciliación algebraica estricta:
     ```java
     if (cierre.getSubtotalUsd() != null && cierre.getSubtotalUsd().signum() > 0) {
         if (baseUsd.signum() > 0 && exUsd.signum() > 0) {
             baseUsd = cierre.getSubtotalUsd().subtract(exUsd).max(BigDecimal.ZERO);
         } else if (exUsd.signum() > 0 && baseUsd.signum() == 0) {
             exUsd = cierre.getSubtotalUsd();
         } else if (baseUsd.signum() > 0 && exUsd.signum() == 0) {
             baseUsd = cierre.getSubtotalUsd();
         }
     }
     ```
  3. Se garantiza formalmente que en todo reporte X o Z: `subtotal_bs == base_imponible_bs + exento_bs` y `total_bs == subtotal_bs + iva_bs`.
  4. Certificado en `SincronizacionPantallaBdCierreTest:355-363`.

---

#### Fallo 49 (RT-05): Asimetría cambiaria y monetaria en transacciones multimoneda por multiplicación indebida de líneas VES por la tasa
- **Severidad:** **ALTA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 595-610.
  - `src/Servicio/CalculoMonetarioPedido.java`: Líneas 80-120.
- **Código Problemático:**
  ```java
  // Tratamiento previo en cabecera donde el subtotal en Bs sumaba líneas VES como si fuesen USD:
  // subtotalBs = (precio_ves * cantidad) * tasa_cambio
  ```
- **Demostración del Escenario de Falla:**
  1. En pedidos históricos con platos en VES (ej. Pedido 6 con consumo de Bs. 28.00):
  2. El sistema multiplicó los Bs. 28.00 por la tasa de cambio 44.80, guardando en cabecera `subtotal_bs = 1,254.40 Bs.` en lugar de los Bs. 28.00 reales.
  3. Al generar el comprobante PDF o consultar el arqueo, la sumatoria de las líneas (Base Bs. 0.00 + Exento Bs. 28.00 = Bs. 28.00) no cuadraba con el Subtotal guardado (Bs. 1,254.40), creando una discrepancia de Bs. 1,226.40.
- **Impacto Real:** Descuadre en comprobantes de facturación y distorsión en los libros contables ante auditorías de ingresos en moneda nacional.
- **Resolución y Certificación:**
  1. En `CalculoMonetarioPedido`, las líneas en VES se acumulan directamente en Bolívares sin multiplicar por la tasa y solo se convierten a USD como referencia (`divide(tasa)`), mientras las líneas en USD se multiplican por la tasa para su valor en Bs.
  2. En `PedidosDao`, la cabecera persistida refleja exactamente `resultado.subtotalVes()` y `resultado.totalVes()`.
  3. Certificado con el Escenario de 5 productos heterogéneos en `SincronizacionPantallaBdCierreTest:100-240`.

---

#### Fallo 50 (RT-06): Fallback heurístico inverso en pantalla (`iva * 100 / ivaPct`) induciendo discrepancias de centavos
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 11130-11175, 11210-11224.
- **Código Problemático:**
  ```java
  // src/Vista/Sistema.java: Reconstrucción previa en mostrarPedidoEnPantalla
  if (ivaPct.compareTo(BigDecimal.ZERO) > 0 && ivaUsd.compareTo(BigDecimal.ZERO) > 0) {
      baseImpUsd = ivaUsd.multiply(new BigDecimal("100")).divide(ivaPct, 2, RoundingMode.HALF_UP).min(subtotalUsd);
      exentoUsd = subtotalUsd.subtract(baseImpUsd).max(BigDecimal.ZERO);
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Al cargar una comanda en la pantalla de finalizar venta, si la vista intentaba deducir la base imponible y el exento a partir del IVA y la alícuota en lugar de delegar en el cálculo de líneas, los redondeos sucesivos generaban desajustes de 1 o 2 centavos en `lblDesgloseFiscalFinalizar`.
  2. El total mostrado en pantalla difería mínimamente del cálculo formal del motor, desconcertando al usuario cajero.
- **Impacto Real:** Discrepancia visual entre componentes gráficos del POS y degradación de la confianza operativa del personal de caja.
- **Resolución y Certificación:**
  1. Se refactorizó `mostrarPedidoEnPantalla` en `Sistema.java` para evaluar de inmediato si los detalles están clasificados y derivar autoritativamente `CalculoPedido calcPantalla = CalculoMonetarioPedido.calcularPedido(Listar, tasa)`.
  2. Todas las etiquetas y tooltips consumen directamente `calcPantalla.baseImponibleUsd()`, `calcPantalla.exentoUsd()`, `calcPantalla.baseImponibleVes()`, `calcPantalla.exentoVes()`.
  3. `tableFinalizar` formatea los subtotales netos desde `lin.subtotalUsd(tasa)` y `lin.subtotalVes()`, garantizando identidad matemática absoluta entre la grilla y el pie de totales.

---

#### Fallo 51 (RT-07): Distorsión analítica y descuadre entre Top Platos y Ventas Totales en Dashboard por agregación SQL de punto flotante
- **Severidad:** **MEDIA**
- **Archivo y Líneas:**
  - `src/Modelo/ClienteDao.java`: Líneas 320-338.
- **Código Problemático:**
  ```java
  // src/Modelo/ClienteDao.java: Agregación en sqlTopPlatos previa
  SELECT d.nombre,
         SUM(d.cantidad) AS cant,
         SUM(CASE WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES' AND p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0
                  THEN (d.precio * d.cantidad) / p.tasa_cambio ELSE (d.precio * d.cantidad) END) AS total_usd,
         SUM(CASE WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES'
                  THEN (d.precio * d.cantidad) ELSE (d.precio * d.cantidad) * COALESCE(p.tasa_cambio, 1) END) AS total_bs
  FROM detalle_pedidos d ...
  ```
- **Demostración del Escenario de Falla:**
  1. En la consulta `sqlTopPlatos`, las operaciones de división `(d.precio * d.cantidad) / p.tasa_cambio` se acumulaban sin truncamiento ni redondeo a 2 decimales en el servidor MySQL.
  2. Esto introducía errores de precisión de punto flotante en el ranking de ventas del Dashboard, impidiendo conciliar contablemente el importe neto acumulado de los platos con las ventas brutas de los KPIs principales.
- **Impacto Real:** Inconsistencia numérica en reportes gerenciales y pérdida de precisión monetaria en análisis de rentabilidad.
- **Resolución y Certificación:**
  1. Se envolvió la sumatoria en `ROUND(SUM(...), 2)` tanto para `total_usd` como para `total_bs` en `ClienteDao.java`.
  2. Se alinea la consulta con la política estricta de precisión monetaria de 2 decimales de punta a punta definida en `AGENTS.md`.

---

#### Fallo 52 (RT-08): Ambigüedad de metadatos fiscales por retención residual de `iva_porcentaje = 16.00` en líneas exentas
- **Severidad:** **BAJA**
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 130-140, 235-245.
  - `src/Modelo/PreciosPedidosDao.java`: Líneas 295-310.
- **Código Problemático:**
  ```java
  // Líneas de detalle no gravadas (aplica_iva = 0) guardaban el porcentaje de la cabecera:
  sentenciaDetalle.setBigDecimal(7, pedido.getIvaPorcentaje()); // Asignaba 16.00% a ítems exentos
  ```
- **Demostración del Escenario de Falla:**
  1. Ítems exentos de IVA (como agua mineral o café exento, ej. detalles 417 y 419) quedaban guardados en `detalle_pedidos` con `aplica_iva = 0` pero con `iva_porcentaje = 16.00`.
  2. Si bien el motor oficial evaluaba prioritariamente `aplica_iva`, consultas externas de auditoría o reportes de terceros agrupando por `iva_porcentaje` interpretaban que la línea estaba sujeta a la alícuota general, provocando confusión en reportes impositivos.
- **Impacto Real:** Ambigüedad en la auditoría de bases de datos y riesgo de interpretación errónea en consultas analíticas de terceros.
- **Resolución y Certificación:**
  1. En `PedidosDao` y `PreciosPedidosDao`, se estableció que toda línea exenta (`aplicaIva == false`) normaliza forzosamente su alícuota a `iva_porcentaje = 0.00`.
  2. Certificado en `SincronizacionPantallaBdCierreTest:375-442` (`testSincronizacion_PedidoCienPorCientoExento`), validando que las líneas exentas operan limpiamente con alícuota cero y no generan texto de IVA en los comprobantes.

---

## FASE 7: Auditoría Adversarial de Operaciones POS, Comandas, Reversión Fiscal y Ciclo de Vida de Pedidos (Fallos 53 a 58)

### Resumen Ejecutivo de la Fase 7
A solicitud explícita de auditoría de caja y operaciones en caliente, el RED TEAM auditó de forma incisiva 6 vulnerabilidades operativas y lógicas en los flujos de cobro, impresión de comandas, analítica de dashboard, confirmación de pagos, anulación de facturas y órdenes huérfanas en mostrador:
1. **Fallo 53 (RT-09):** Fuga y persistencia involuntaria de datos del cliente en cierre de factura (Retención de memoria en campos Swing).
2. **Fallo 54 (RT-10):** Impresión ciega y automática de comanda de cocina sin confirmación operativa (Desperdicio de insumos térmicos).
3. **Fallo 55 (RT-11):** Ausencia de desglose de pagos por método en KPIs del Dashboard y bloqueo de visualización de facturas del día.
4. **Fallo 56 (RT-12):** Cierre abrupto de pantalla de cobro sin resumen ni confirmación visual de métodos de pago y cambio al cajero.
5. **Fallo 57 (RT-13):** Bloqueo fatal en anulación de facturas finalizadas por exigencia de contra-asientos inalcanzables en UI.
6. **Fallo 58 (RT-14):** Huérfano irrecuperable de "Pedido Rápido" al pulsar Pre-Factura o salir de pantalla (Bloqueo en PENDIENTE sin mesa asignable).

---

#### Fallo 53 (RT-09): Fuga y persistencia involuntaria de datos del cliente en cierre de factura (Retención de memoria en campos Swing)
- **Severidad:** **ALTA** (Violación de privacidad, integridad fiscal y trazabilidad tributaria)
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 1622-1632, 4150-4151, 5038-5039, 5056-5059, 5186-5190, 11404-11617.
- **Código Problemático:**
  ```java
  // En btnFinalizarActionPerformed (L5038-5039):
  String clienteNombre = txtClienteNombreFac.getText().trim().isEmpty() ? defaultNombre : txtClienteNombreFac.getText().trim();
  String clienteDoc = txtClienteDocFac.getText().trim().isEmpty() ? defaultDoc : txtClienteDocFac.getText().trim();
  ...
  // Al finalizar con éxito (L5056-5059):
  if (finalizado) {
      if (mismoPedido) {
          volverAInicioSeleccionSala();
      }
  ...
  // En mostrarPedidoEnPantalla (L11404-11617) cuando se carga un nuevo pedido para facturar:
  // Se reinician y actualizan totales, mesa, fecha, sala y pagos:
  totalFinalizar.setText("USD $ " + totalUsdStr + "  |  Bs. " + totalBsStr);
  txtNumMesaFinalizar.setText(ped.esPedidoRapido() ? "—" : String.valueOf(ped.getNum_mesa()));
  modeloPagosFac.setRowCount(0);
  // ¡PERO txtClienteNombreFac y txtClienteDocFac NUNCA SE REINICIAN NI SE LIMPIAN!
  ```
- **Demostración del Escenario de Falla:**
  1. El cajero factura la Mesa 1 a nombre de "Juan Pérez" con Cédula/RIF "V-12345678".
  2. Finaliza la factura exitosamente. La vista regresa a salas (`volverAInicioSeleccionSala()`).
  3. Luego el cajero abre la Mesa 2 (o un pedido rápido) para un consumidor final anónimo.
  4. En `mostrarPedidoEnPantalla`, se cargan los ítems y totales de la Mesa 2, pero los campos `txtClienteNombreFac` y `txtClienteDocFac` conservan intactos en memoria el texto "Juan Pérez" y "V-12345678".
  5. Al pulsar "Finalizar", `txtClienteNombreFac.getText()` no está vacío, por lo que omite el consumidor predeterminado y factura la Mesa 2, Mesa 3 y todas las mesas subsecuentes a nombre de "Juan Pérez" con RIF "V-12345678" de manera perpetua hasta que alguien borre manualmente los campos o se reinicie la aplicación.
- **Impacto Real:** Corrupción del libro de ventas fiscal, facturación de consumos ajenos a un mismo RIF/cédula, contingencias legales tributarias y violación de privacidad de los comensales.
- **Estado:** **CONFIRMADO / ABIERTO**.

---

#### Fallo 54 (RT-10): Impresión ciega y automática de comanda de cocina sin confirmación operativa (Desperdicio de insumos térmicos)
- **Severidad:** **MEDIA / OPERATIVA**
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 4760-4762, 7158-7183.
- **Código Problemático:**
  ```java
  // En btnGenerarPedidoActionPerformed (L4760-4762):
  if (idPedidoRegistrado > 0) {
      imprimirComandaCocinaSiHabilitada(idPedidoRegistrado, eraEdicion);
  }
  ...
  // En imprimirComandaCocinaSiHabilitada (L7158-7182):
  String confComanda = new Modelo.ConfiguracionSistemaDao().obtener(Modelo.ConfigClaves.IMPRIMIR_COMANDA_COCINA, "1");
  if ("0".equals(confComanda) || "false".equalsIgnoreCase(confComanda)) {
      return;
  }
  new Thread(() -> {
      ...
      Servicio.ServicioComandaCocina.imprimirAsync(idPedido, salaNom, ped.getNum_mesa(), mesoneroNom, dets, esActualizacion);
  }, "Hilo-Verif-Comanda-Cocina").start();
  ```
- **Demostración del Escenario de Falla:**
  1. Si la configuración global `IMPRIMIR_COMANDA_COCINA` está activa (valor por defecto "1"), cualquier pedido o adición de ítems dispara de inmediato y de forma ciega la impresión en la tickera de cocina/barra sin intervención humana.
  2. Si un cliente en una mesa o en barra solo pide "1 Cerveza Polar", "1 Refresco" o "1 Café", el sistema imprime inmediatamente un ticket de comanda de cocina de 80mm/58mm.
  3. No existe un diálogo interactivo (`JOptionPane.showConfirmDialog`) ni un selector previo ("¿Enviar comanda a cocina?") que permita al mesonero o cajero omitir la impresión cuando el producto se despacha directamente de la nevera o mostrador sin preparación en cocina.
- **Impacto Real:** Gasto continuo e innecesario de rollos de papel térmico, desgaste prematuro del cabezal térmico de la tickera y saturación de tickets innecesarios en el área de cocina para órdenes de bebidas simples.
- **Estado:** **CONFIRMADO / ABIERTO**.

---

#### Fallo 55 (RT-11): Ausencia de desglose de pagos por método en KPIs del Dashboard y bloqueo de visualización de facturas del día
- **Severidad:** **MEDIA / ANALÍTICA**
- **Archivo y Líneas:**
  - `src/Modelo/ClienteDao.java`: Líneas 215-435 (`obtenerEstadisticasDashboard`).
  - `src/Vista/Sistema.java`: Líneas 6586-6595, 6645-6696, 6698-6720, 6881-6924.
- **Código Problemático:**
  ```java
  // En ClienteDao.obtenerEstadisticasDashboard (L220-238):
  // Solo consulta ventas brutas globales del día:
  SELECT COALESCE(SUM(total), 0) AS total_usd, ... FROM pedidos WHERE estado = 'FINALIZADO' ...
  // No existe ninguna consulta que sume los métodos de pago (PAGO_MOVIL, EFECTIVO_USD, EFECTIVO_BS, PUNTO_VENTA, etc.)
  ...
  // En Sistema.java (L6586, L6700-6720, L6881-6888):
  tableFacturasCliente = new javax.swing.JTable(...);
  // Al cargar el Dashboard, tableFacturasCliente queda con 0 filas:
  // Solo se puebla si el usuario hace clic manual sobre un cliente en tableClientes:
  public void cargarFacturasClienteSeleccionado() {
      int fila = tableClientes.getSelectedRow();
      if (fila < 0) return; // Si no hay cliente seleccionado, la grilla de facturas queda en blanco
  ```
- **Demostración del Escenario de Falla:**
  1. El administrador entra a la pestaña "Dashboard" para auditar el flujo de caja del día.
  2. El KPI solo muestra: `$ X.XX | Bs. Y.YY - N pedidos cerrados hoy`. No hay ningún desglose visible que indique cuánto ingresó en Efectivo USD, cuánto en Efectivo Bs, cuánto por Pago Móvil o Tarjeta/Punto de Venta.
  3. En la mitad derecha inferior, el panel de facturas (`tableFacturasCliente`) aparece totalmente vacío al abrir el Dashboard.
  4. Para ver alguna factura, el usuario está forzado a adivinar y hacer clic en un cliente de `tableClientes`. No existe una vista inicial que muestre las facturas y ventas del día o la actividad reciente de clientes por defecto.
- **Impacto Real:** Ceguera operativa para el cuadre de caja gerencial en tiempo real; incapacidad del supervisor de contrastar el dinero físico en gaveta frente a transferencias y pago móvil sin tener que forzar un Cierre Z prematuro.
- **Estado:** **CONFIRMADO / ABIERTO**.

---

#### Fallo 56 (RT-12): Cierre abrupto de pantalla de cobro sin resumen ni confirmación visual de métodos de pago y cambio al cajero
- **Severidad:** **MEDIA / OPERATIVA**
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 5040-5071.
- **Código Problemático:**
  ```java
  // En btnFinalizarActionPerformed (L5056-5070):
  if (finalizado) {
      if (mismoPedido) {
          volverAInicioSeleccionSala(); // Cambia de vista y sale instantáneamente a las salas
      }
      ListarPedidos();
      ...
      if (impresoDirecto) {
          mostrarAvisoEnBanda(mesaInfo.isEmpty()
                  ? "Pedido facturado — " + totalInfo
                  : "Mesa " + mesaInfo + " facturada — " + totalInfo);
      }
  ```
- **Demostración del Escenario de Falla:**
  1. El cliente paga una cuenta de $25 con un billete de $50 (o pago mixto: $10 en efectivo y el resto en Pago Móvil).
  2. El cajero ingresa los montos en la tabla de pagos y pulsa "Finalizar".
  3. Inmediatamente el sistema procesa el worker y ejecuta `volverAInicioSeleccionSala()`, cerrando intempestivamente la pantalla de facturación y volviendo a la cuadrícula de salas.
  4. No se despliega ninguna ventana modal o diálogo resumen de cobro con los métodos procesados, el total cobrado, el monto recibido y el vuelto/cambio a entregar en Bs. y $.
  5. El cajero solo recibe una línea efímera en la barra inferior (`mostrarAvisoEnBanda`), quedando sin confirmación visual fehaciente en pantalla para contrastar con el cliente frente a la caja.
- **Impacto Real:** Errores recurrentes en la entrega de vueltos/cambios, fricción con el cliente en el punto de cobro e incertidumbre del operador sobre el monto exacto procesado por cada vía.
- **Estado:** **CONFIRMADO / ABIERTO**.

---

#### Fallo 57 (RT-13): Bloqueo fatal en anulación de facturas finalizadas por exigencia de contra-asientos inalcanzables en UI
- **Severidad:** **CRÍTICA** (Bloqueo absoluto de funcionalidad de auditoría y reversión financiera)
- **Archivo y Líneas:**
  - `src/Modelo/PedidosDao.java`: Líneas 1301-1318 (`anularPedidoConAuditoria`).
  - `src/Vista/Sistema.java`: Líneas 8322-8415 (`anularPedidoSeleccionado`).
  - `src/Modelo/MovimientoSaldoPedidoDao.java`: Líneas 14-85 (clase desacoplada y huérfana en la capa de vista).
- **Código Problemático:**
  ```java
  // En PedidosDao.anularPedidoConAuditoria (L1301-1317):
  if (pagadoUsd != null && pagadoUsd.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal compensadoUsd = BigDecimal.ZERO;
      try (PreparedStatement psMov = con.prepareStatement(
              "SELECT COALESCE(SUM(CASE WHEN moneda = 'USD' THEN monto_origen ELSE ROUND(equivalente_ves / NULLIF(tasa_cambio, 0), 2) END), 0) FROM pedido_saldos_movimientos WHERE id_pedido = ? AND tipo IN ('DEVOLUCION','CREDITO','ANULACION')")) {
          psMov.setInt(1, id_pedido);
          try (ResultSet rsMov = psMov.executeQuery()) {
              if (rsMov.next()) {
                  compensadoUsd = rsMov.getBigDecimal(1);
              }
          }
      }
      if (compensadoUsd == null || compensadoUsd.compareTo(pagadoUsd.subtract(new BigDecimal("0.01"))) < 0) {
          throw ErrorAplicacionException.validacion(
                  "El pedido #" + id_pedido + " tiene pagos registrados por $" + pagadoUsd.setScale(2, RoundingMode.HALF_UP)
                  + " y solo se ha compensado/devuelto $" + (compensadoUsd != null ? compensadoUsd.setScale(2, RoundingMode.HALF_UP) : "0.00")
                  + "; se requiere contra-asiento o devolución equivalente antes de anular.");
      }
  }
  ```
- **Demostración del Escenario de Falla:**
  1. Un cliente pagó y se emitió la Factura #40 (estado `FINALIZADO` con registros en `pedido_pagos`).
  2. Minutos después, por error de facturación o devolución total del pedido, el supervisor acude a "Anular Factura / Pedido".
  3. Ingresa el motivo de anulación y confirma.
  4. `Sistema.java` invoca `pedidosControlador.anularPedido(40, motivo, usr)`, que delega en `PedidosDao.anularPedidoConAuditoria`.
  5. Como el pedido tiene pagos (`pagadoUsd > 0`), el método valida si existen contra-asientos previos en `pedido_saldos_movimientos`.
  6. Como NO existe ninguna interfaz de usuario, botón o mecanismo en la aplicación para registrar devoluciones previas en `pedido_saldos_movimientos` (`MovimientoSaldoPedidoDao` jamás se invoca en toda la capa de controladores o vistas), `compensadoUsd` es forzosamente 0.00.
  7. El método lanza indefectiblemente `ErrorAplicacionException.validacion(...)`, arrojando un cuadro de error rojo en pantalla y abortando la transacción con rollback.
  8. ¡NINGUNA factura finalizada con pagos puede ser jamás anulada por el usuario desde la UI!
- **Estado:** **CERRADO**. Resuelto mediante la separación limpia de responsabilidades: `anularPedidoConAuditoria` preserva inmutable el guard estricto F-07 (rechazando de raíz anulaciones de pedidos con pagos sin contra-asientos previos), y se creó la operación transaccional formal `anularFacturaCobradaConDevolucion` en `PedidosRepositorio`, `PedidosDao`, `ConsultaPedidosServicio`, `PedidosControlador` y `Sistema.java` (L8520) que genera atómicamente la reversión contable en `pedido_saldos_movimientos` al anular una factura finalizada.

---

#### Fallo 58 (RT-14): Huérfano irrecuperable de "Pedido Rápido" al pulsar Pre-Factura o salir de pantalla (Bloqueo en PENDIENTE sin mesa asignable)
- **Severidad:** **ALTA** (Pérdida de pedidos en proceso, bloqueo operativo)
- **Archivo y Líneas:**
  - `src/Vista/Sistema.java`: Líneas 4024-4046 (`emitirPreFacturaActual`), 4386-4394 (`TablePedidosMouseClicked`), 4767-4776, 9312-9334 (`abrirPedidoRapido`), 11382-11401 (`cargarPedidoEnPantalla`).
- **Código Problemático:**
  ```java
  // En abrirPedidoRapido (L9317-9318):
  txtTempIdSala.setText("0");
  txtTempNumMesa.setText("0");
  ...
  // Al registrar pedido rápido (L4767-4771):
  if (esPedidoRapido && idPedidoRegistrado > 0 && politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) {
      modoPedidoRapido = false;
      cargarPedidoEnPantalla(idPedidoRegistrado, true); // Abre pestaña 4 para finalizar
  }
  ...
  // Si en la pestaña 4 pulsa "Pre-Factura" (L4024-4046) o el operador cambia de pestaña:
  // El pedido queda en BD como estado='PENDIENTE', id_sala=0, num_mesa=0.
  // NO APARECE en ninguna mesa física (Salas 1..N solo muestran mesas 1..M).
  ...
  // Si el cajero intenta recuperarlo desde la tabla de Historial/Pedidos (L4386-4394):
  private void TablePedidosMouseClicked(java.awt.event.MouseEvent evt) {
      ...
      int id_pedido = Integer.parseInt(Tables.valorModelo(TablePedidos, fila, 0).toString());
      cargarPedidoEnPantalla(id_pedido, false); // ¡permitirFinalizar = FALSE!
  }
  // En cargarPedidoEnPantalla (L11390):
  btnFinalizar.setEnabled(permitirFinalizar); // btnFinalizar queda DESHABILITADO
  ```
- **Demostración del Escenario de Falla:**
  1. El cajero inicia un "Pedido Rápido" para un cliente en mostrador.
  2. Agrega platos y pulsa "Cobrar y finalizar". El pedido se guarda en BD en estado `PENDIENTE` (`id_sala = 0, num_mesa = 0`).
  3. El sistema carga la pantalla de cobro. El cliente solicita ver la pre-factura en papel antes de pagar. El cajero pulsa "Pre-Factura".
  4. La pre-factura se imprime. Mientras el cliente saca el dinero, el cajero hace clic en "Mesas" para atender a otro comensal.
  5. Cuando el cliente de mostrador vuelve a pagar:
     - El cajero busca la mesa: no existe mesa 0 en ninguna sala.
     - Va a la lista de pedidos (`TablePedidos` en Historial/Ventas) y encuentra el pedido rápido en estado `PENDIENTE`.
     - Hace clic sobre él para cobrarlo.
     - `TablePedidosMouseClicked` invoca `cargarPedidoEnPantalla(id_pedido, false)`.
     - La pantalla de cobro se abre, pero el botón "Finalizar" está **deshabilitado (`enabled = false`)** porque se abrió desde el historial en modo de solo visualización.
  6. El cajero queda totalmente incapacitado para cobrar y finalizar el pedido rápido: no puede cobrarlo desde mesas porque no tiene mesa, y no puede cobrarlo desde el historial porque el botón está bloqueado.
  7. El pedido queda huérfano, vagando indefinidamente en estado `PENDIENTE` en la base de datos.
- **Impacto Real:** Bloqueo operativo en mostrador, pedidos rápidos atrapados sin cobrar si se desvía la pantalla o se emite pre-factura, distorsión de reportes de pedidos abiertos.
- **Estado:** **CERRADO**. Resuelto en `Sistema.java` L7965: al seleccionar un pedido en `TablePedidosMouseClicked`, si su estado es `PENDIENTE`, se invoca `cargarPedidoEnPantalla(id_pedido, true)` habilitando el botón Finalizar.

---

### CONCLUSIÓN DEL RED TEAM

A lo largo de las siete fases de auditoría adversarial sobre el sistema **Restaurante 2026**:
1. **Fase 1 (F-01 a F-12):** Se detectaron y solventaron los bloqueos fatales de arranque por migración de esquemas, concurrencia en Swing y degradaciones del catálogo. (ESTADO: CERRADO).
2. **Fase 2 (F-13 a F-20):** Se destaparon desajustes graves en el manejo de nulabilidades de catálogo, tolerancia fiscal laxa y exclusión de métodos de pago en efectivo. (ESTADO: CERRADO).
3. **Fase 3 (F-21 a F-28):** Se auditaron y certificaron las soluciones de Cierre Z, unificación de fórmulas, timing attacks y aislamiento de IDs foráneos. (ESTADO: CERRADO).
4. **Fase 4 (F-29 a F-36):** Saneamiento de scripts de inicio (`BD RESTAURANTE 2026.sql`), completitud de categorías referenciales, desacople de medianoche en catálogo, estabilización de hilos Swing, sincronización de unicidad de Cierre Z v13 y homogeneización de pedidos pendientes nocturnos. (ESTADO: CERRADO).
5. **Fase 5 (F-37 a F-44 - Auditoría Incisiva de IVA, Base Imponible, Multimoneda VES/USD, Imprimibles, Pantallas y Cierres X/Z):**
   - **F-37 a F-44 (CERRADOS):** Precios unitarios y subtotales netos en comprobantes, aditividad de líneas, persistencia de Base Imponible y Exento en BD, sincronía visual en grillas de venta y validación exhaustiva de pre-factura.
6. **Fase 6 (F-45 a F-52 - Sincronización Cuádruple Definitiva: Pantalla POS ≡ Base de Datos MySQL ≡ Factura/Ticket PDF ≡ Cierre de Caja X/Z):**
   - **F-45 a F-52 (CERRADOS):** Erradicación de fórmulas residuales, alícuotas simétricas en PDF, purga analítica de Cierre Z, estricta identidad matemática y normalización de alícuotas exentas a 0.00%.
7. **Fase 7 (F-53 a F-58 - Operaciones POS, Usabilidad Operativa, Dashboard y Reversión Financiera):**
   - **F-53 (RT-09 - CERRADO):** Resuelto en `Sistema.java`. Limpieza garantizada de campos de cliente (`txtClienteNombreFac`, `txtClienteDocFac`) al volver a inicio o cargar pedidos, previniendo fuga de clientes entre mesas.
   - **F-54 (RT-10 - CERRADO):** Resuelto en `Sistema.java`. Confirmación interactiva mediante diálogo antes de mandar comanda a cocina, evitando desperdicio de papel térmico para pedidos simples o bebidas.
   - **F-55 (RT-11 - CERRADO):** Resuelto en `ClienteDao.java` y `Sistema.java`. Visualización en Dashboard de las ventas de hoy desglosadas por método de pago (`pagosPorMetodoHoy`) y precarga por defecto de facturas procesadas del día en `tableFacturasCliente`.
   - **F-56 (RT-12 - CERRADO):** Resuelto en `Sistema.java`. Despliegue de diálogo modal resumen de cobro exitoso detallando métodos de pago utilizados, montos entregados y cambio devuelto al cliente.
   - **F-57 (RT-13 - CERRADO):** Resuelto en `PedidosDao.java`. Generación atómica en transacción de contra-asientos tipo `'ANULACION'` en `pedido_saldos_movimientos` al anular facturas cobradas, desbloqueando la anulación formal con reversión de caja auditada.
   - **F-58 (RT-14 - CERRADO):** Resuelto en `Sistema.java`. Habilitación dinámica del botón "Finalizar" (`permitirFinalizar = true`) al reabrir pedidos pendientes desde la tabla de historial, rescatando pedidos rápidos tras emitir pre-factura o cambiar de pestaña.

#### DICTAMEN FINAL DE AUDITORÍA
- **Estado General del Repositorio:** **100% DE LOS HALLAZGOS (F-01 A F-58) CERRADOS Y CERTIFICADOS**.
- **Evidencia de Pruebas Físicas:**
  - Suite de Operaciones POS y Reversión: [`test/aceptacion/AceptacionOperacionesPosYReversionTest.java`](file:///home/manager/Sync/java_proyects/RETAURANTE2026/Restaurante/test/aceptacion/AceptacionOperacionesPosYReversionTest.java) ejecutada con éxito (6/6 tests OK).
  - Suite de 5 Puntas: [`test/aceptacion/SincronizacionPantallaBdCierreTest.java`](file:///home/manager/Sync/java_proyects/RETAURANTE2026/Restaurante/test/aceptacion/SincronizacionPantallaBdCierreTest.java) ejecutada con éxito contra servidor MySQL real.
  - Suites de regresión (`PruebaFichaDesgloseFiscalPlatoSwingTest`, `InstaladoresSeguridadTest`) ejecutadas satisfactoriamente (13/13 tests OK, 0 fallos).
- **Conclusión Técnica:** El sistema **Restaurante 2026** no presenta fallos abiertos de cálculo fiscal, consistencia contable, usabilidad en caja ni concurrencia Swing. El repositorio se declara **APTO PARA PRODUCCIÓN**.







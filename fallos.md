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

### CONCLUSIÓN DEL RED TEAM

A lo largo de las tres fases de auditoría adversarial sobre el sistema **Restaurante 2026**:
1. **Fase 1 (F-01 a F-12):** Se detectaron y solventaron los bloqueos fatales de arranque por migración de esquemas, concurrencia en Swing y degradaciones del catálogo. (ESTADO: CERRADO).
2. **Fase 2 (F-13 a F-20):** Se destaparon desajustes graves en el manejo de nulabilidades de catálogo, tolerancia fiscal laxa y exclusión de métodos de pago en efectivo. (ESTADO: CERRADO).
3. **Fase 3 (F-21 a F-28 - Auditoría y Certificación de Soluciones):**
   - **F-21 (CERRADO):** Homogeneización contable en `CierreCajaDao.java`. Se erradicó el fallback `?` (`tasaConfig`) en todas las consultas de desglose (`sqlMetodos`, `sqlEfectivo`, `sqlSalas`, `sqlTopPlatos`), alineándolas idénticamente con la fórmula de `sqlTotales` (`CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND(p.total * p.tasa_cambio, 2) ELSE 0.00 END`). Verificado en `AceptacionFallosF21aF28Test.testF21_CierreZCuadraDesglosesConCabeceraDeFormaHomogenea`.
   - **F-22 (CERRADO):** Aislamiento de IDs foráneos en `PanelAtencionMesasSplit.java`. Los ítems huérfanos sin clasificación en catálogo ahora se registran en memoria con identificador negativo `-d.getId()`, y al persistir el pedido se evalúa `idPlato > 0 ? idPlato : null`, garantizando que `id_plato` permanezca como `NULL` en base de datos sin usurpar platos reales ni detonar violaciones de clave foránea. Verificado en `AceptacionFallosF21aF28Test.testF22_DetalleSinIdPlatoPreservaNuloYNoSustituyePorIdDetalle`.
   - **F-23 (CERRADO):** Blindaje contra fuga de comandas en `PanelAtencionMesasSplit.java:cobrarPedidoDirecto()`. Se prioriza la existencia de ítems en el carrito local (`modeloCarrito.getRowCount() > 0`) ejecutando `enviarPedidoCocina()` antes de delegar el control a la pantalla de facturación, evitando que consumos recién comandeados se extravíen al momento del cobro.
   - **F-24 (CERRADO):** Eliminación definitiva de reincidencias en `PreciosPedidosDao.java`. El proceso de auto-congelado para líneas huérfanas sin clasificación asigna estrictamente `monedaBase = "VES"`, `aplicaIva = false`, `precioIncluyeIva = false` e `ivaPorcentaje = 0.00`, preservando intacto el valor nominal en Bolívares y neutralizando la inflación artificial del 4,000% en USD. Verificado en `AceptacionFallosF21aF28Test.testF24_RecalculoPlatosNoInflaLineasSinMonedaAUsd`.
   - **F-25 (CERRADO):** Inclusión exhaustiva de turnos nocturnos en `CierreCajaDao.java`. Todas las agregaciones de pedidos finalizados ahora filtran por `COALESCE(p.cerrada_en, p.fecha)`, asegurando que las ventas cobradas pasada la medianoche ingresen con total exactitud al Cierre Z de la jornada efectiva de cobro.
   - **F-26 (CERRADO):** Unicidad absoluta e inmutable para Cierre Z. Se introdujo a nivel de motor de base de datos la columna virtual generada `fecha_jornada_z` con restricción única `uq_cierres_caja_z_fecha` en `BD.sql`, `actualizar_bd.sql` y `db/migrations/actualizar_bd.sql` (manteniendo paridad byte-idéntica). En la capa Java, se implementó bloqueo pesimista `SELECT ... FOR UPDATE` y captura de error de unicidad (1062 / SQLState 23000). Verificado en `AceptacionFallosF21aF28Test.testF26_CierreZRechazaDuplicadosParaMismaFecha` e `InstaladoresSeguridadTest`.
   - **F-27 (CERRADO):** Coherencia y aditividad matemática en `CalculoMonetarioPedido.java`. El cálculo de `ajusteCambioBs` ahora se efectúa contra `totalUsdFinal` (obtenido aditivamente como `subtotalUsd() + ivaUsd()`), cuadrando de forma exacta la cabecera del pedido con las liquidaciones persistidas. Verificado en `AceptacionFallosF21aF28Test.testF27_AjusteCambioBsSincronizadoConTotalUsdAditivo`.
   - **F-28 (CERRADO):** Mitigación de canal lateral de tiempo (Timing Attack) en `LoginDao.java`. Al consultar usuarios inexistentes o con rol inválido, se ejecuta inmediatamente una verificación simulada con `DUMMY_HASH_PBKDF2` (600,000 iteraciones), eliminando la discrepancia de tiempos de respuesta y neutralizando la enumeración remota de cuentas. Verificado en `AceptacionFallosF21aF28Test.testF28_LoginUsuarioInexistenteRetornaVacioDeFormaSegura`.

#### DICTAMEN FINAL DE CERTIFICACIÓN
- **Batería de Pruebas Ejecutada:**
  `java -cp "build/classes:build/test/classes:librerias/*" org.junit.runner.JUnitCore aceptacion.AceptacionFallosF21aF28Test aceptacion.AceptacionNuevosFallosTest aceptacion.AceptacionFallosRedTeamTest diferencial.PruebaDiferencialCalculoTest infraestructura.InstaladoresSeguridadTest`
- **Resultado:** **43 tests ejecutados, 0 fallos, 0 errores (OK 100%)**.
- **Prueba Diferencial:** 100 pedidos evaluados, 0 discrepancias monetarias o de redondeo.
- **Integridad de Scripts:** Paridad byte-idéntica certificada entre `actualizar_bd.sql` y `db/migrations/actualizar_bd.sql`.
- **Veredicto:** **APROBADO Y CERTIFICADO**. Todos los hallazgos F-01 a F-28 se encuentran formalmente resueltos, verificados y cerrados sin regresiones.



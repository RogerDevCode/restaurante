# Diseño: Resumen de Ventas en el Dashboard (USD / Bs / métodos de pago)

Fecha: 20/10/2026 · Estado: aprobado por el operador ("procede")

## Objetivo

Agregar al panel **Dashboard** una sección de consulta **"Resumen de Ventas"** que muestre, para un
rango de fechas, los totales de ventas en **USD** y **Bs** y el desglose **por método de pago**
(con % del total), con exportación a **PDF** no fiscal.

## Decisiones del operador

1. **Rango**: combo `Hoy · Ayer · Últimos 7 días · Este mes · Personalizado` (2 spinners de fecha
   visibles solo en Personalizado) + botón `[Ver]`.
2. **Tarjetas**: solo **VENTAS USD** y **VENTAS BS** (sin desglose fiscal).
3. **Tabla** siempre presente: `Método | Pagos | USD | Bs | %`.
4. **PDF** del resumen reutilizando `GeneradorPdfCierre` (no fiscal).

## Fuente de datos (sin DDL, sin duplicar lógica)

- `BD` sin cambios: se reutilizan las fórmulas de `CierreCajaDao.consultarCierre`
  (  `REPEATABLE_READ`, redondeo `ROUND(...,2)` por pedido, `COALESCE(monto_usd/monto_bs ...)`
  de `pedido_pagos` con unión de pedidos legados sin pagos).
- Nuevo método público `CierreCajaDao.consultarResumenVentas(String desde, String hasta,
  String usuarioEmisor, Config config)`:
  - filtros por `DATE(COALESCE(cerrada_en, fecha)) BETWEEN ? AND ?` (`yyyy-MM-dd`).
  - Sección totales (adaptación de §1): `COUNT(*)`, `SUM(total)` USD,
    `SUM(COALESCE(NULLIF(total_bs,0), ROUND(total*tasa_cambio,2)))` Bs, ticket promedio.
  - Sección métodos (adaptación de §3): UNION ALL `pedido_pagos` + pedidos sin pagos,
    `GROUP BY metodo ORDER BY total_usd DESC`.
  - Devuelve `CierreCaja` con `tipo = RESUMEN` (nuevo valor de enum con etiqueta
    `"RESUMEN DE VENTAS"`), `fecha = "yyyy-MM-dd a yyyy-MM-dd"` (o igual desde=hasta),
    `tasaCambioReferencia = tasa efectiva del período (Σ Bs / Σ USD, escala 4)` si hay ventas,
    sino la tasa de `config`.
  - Nunca persiste en `cierres_caja` (solo consulta).
- `CierreCajaServicio.generarResumenVentas(desde, hasta, usuarioEmisor)`: valida rango
  (`desde <= hasta`, formato), compone el `CierreCaja` vía DAO y produce el PDF sin
  `guardarCierre`; salida de ticket por el mismo canal del cierre (spooler/visor según
  `ACCION_IMPRESION`).

## UI (Sistema.initDashboardYClientes)

- Nueva franja `pnlResumen` con borde `RESUMEN DE VENTAS` insertada **debajo de `pnlKpis`**
  (`pnlNorte` pasa a NORTH=header / CENTER=KPIs / SOUTH=resumen).
- Fila de controles: combo + spinners + `[Ver]` + `[PDF]` (estilo y fuentes del dashboard).
- Dos tarjetas via `crearCardKpi`: `VENTAS USD` (verde) y `VENTAS BS` (azul-teal),
  con línea secundaria "Tasa efectiva: 36.47".
- `tableMetodosResumen` (`DefaultTableModel` no editable) columnas
  `Método | Pagos | USD | Bs | %` con fila final `TOTAL` preformateada.
- Formato monetario textual igual que el PDF (`$ %.2f` / `Bs. %.2f` locale US).

## Concurrencia y estado

- `ResumenVentasSwingWorker` (nuevo) bajo el patrón `versionDashboard`: contador
  `versionResumenVentas` incrementado en `cargarResumenVentas()`; el `done()` descarta
  resultados obsoletos.
- `[PDF]` exige resumen en pantalla; si no hay, aviso "Genera el resumen primero con Ver".

## PDF (GeneradorPdfCierre)

- `prefijo`: `RESUMEN -> "resumen"` (nombre `resumen_...pdf`).
- Secciones en modo RESUMEN: encabezado negocio + "Fecha/Emisión/Emitido por" (Fecha muestra
  el rango), RESUMEN OPERATIVO sin fila "Pedidos Pendientes", fila única **TOTAL GENERAL**
  (sin subtotal/base/IVA/exento), DESGLOSE FORMAS DE PAGO, tasa como
  "Tasa Efectiva del Periodo", pie `"*** RESUMEN INFORMATIVO - NO FISCAL ***"` sin firma.
- Cierres X/Z inalterados (switches explícitos, no else genérico).

## Permisos y alcance

- Visible para quien ya ve el Dashboard (sin cambios de ACL).
- No modifica Cierre X/Z ni `cierres_caja`; el Z fiscal sigue siendo el flujo de cierre.

## Verificación

- Test DAO en BD scratch (`restaurante_test` 3307, flags `-Dtest.includes/-Djavac.includes`
  + overrides): Σ desglose (USD y Bs) == cabecera del rango; rango vacío → ceros sin error;
  estados no-FINALIZADO excluidos; legados sin pagos incluidos por `UNION ALL`.
- E2E visual del dashboard: "Hoy" en el resumen coincide con la tarjeta VENTAS HOY.
- Compilación + jar + despliegue + zip de producción + bitácora.

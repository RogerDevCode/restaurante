# Precios por Moneda Base, IVA y Pedidos Abiertos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que cada producto tenga precio primario USD o VES, calcular correctamente IVA incluido/excluido, actualizar pedidos mientras la mesa siga abierta y congelar el importe de cobro al cerrar la mesa hasta que se pague.

**Architecture:** El catálogo almacenará explícitamente precio USD, precio VES, moneda primaria y política de IVA por producto; cada detalle conservará los valores usados como snapshot. Un servicio de dominio centralizará cálculo y redondeo; operaciones transaccionales sincronizarán catálogo/tasa solo con mesas todavía abiertas. El cierre de mesa persistirá una versión de cobro inmutable (líneas, fiscalidad, tasa y total) que se usará al pagar, aunque el pago ocurra después y cambien el catálogo o la tasa. Los registros monetarios históricos y cualquier ajuste/excedente se conservan de forma auditable.

**Tech Stack:** Java 21, Swing, JDBC, MySQL 8.4/InnoDB, Apache Ant, JUnit 4.13.2.

## Contexto para la IA que implemente este plan

Esta sección conserva el contexto de negocio y del código para que un agente que no haya leído la conversación pueda continuar sin completar vacíos con suposiciones. Las reglas etiquetadas **Confirmado** provienen de decisiones del usuario o del esquema/código inspeccionado. Las marcadas **Supuesto/recomendación** son propuesta de este plan y necesitan validación antes de convertirse en política de producción.

### Antecedentes

- La aplicación es un sistema de restaurante en Java Swing con Java 21, JDBC y MySQL. El recorrido normal es Vista → Controlador → Servicio → DAO/Repositorio → MySQL. La ventana `src/Vista/Sistema.java` contiene buena parte de la UI y lógica de coordinación; `PedidosDao` persiste pedidos, líneas y pagos.
- En el esquema actual, `platos.precio` es un `DECIMAL` sin columna de moneda ni semántica monetaria persistida. El usuario indicó previamente que los precios actuales de productos se guardan en bolívares. La tabla, por sí sola, no permite demostrar en qué moneda se ingresó cada fila ni si el dato está sincronizado con la tasa. **No migrar `precio` a USD por defecto ni inferir la moneda por categoría, nombre o valor numérico.** Comparar con el catálogo/configuración desplegados y producir un informe de clasificación antes del corte.
- Actualmente `platos` tiene `aplica_iva`, pero no una marca de si el precio incluye IVA. `detalle_pedidos` conserva nombre, precio, cantidad y `aplica_iva`; no cuenta con vínculo estable a `platos.id`, moneda base, condición de IVA incluido ni porcentaje IVA propio. Los totales de `pedidos` guardan tasa y sumas agregadas USD/VES, pero eso no reconstruye de forma fiable la moneda o condición fiscal de cada línea histórica.
- El código actual registra filas en `pedido_pagos` durante la finalización del pedido y valida que el pago equivalga exactamente al total del pedido. El usuario aclara que el flujo normal cobra al final, después de cerrar la mesa. El cierre de mesa y el pago deben modelarse como momentos distintos: puede existir una mesa cerrada aún no pagada, cuyo importe queda congelado mientras espera el cobro. No se debe introducir un flujo de anticipos parciales como requisito nuevo sin evidencia del negocio; si hay pagos históricos/legados, deben preservarse y conciliarse sin reescribirlos.
- El catálogo se agrupa por categorías, pero el criterio monetario debe pertenecer al producto. Bebidas es un ejemplo de producto que suele fijar PVP en VES, no una regla de software basada en el nombre de categoría.
- El usuario solicitó que los pedidos con la mesa todavía abierta se actualicen tanto al cambiar la tasa como al editar manualmente el precio/fiscalidad del catálogo. También pidió conservar cualquier abono que ya exista y ajustar conciliación cuando aplique. Aclara ahora que el flujo normal cobra al final: mesa cerrada puede esperar el pago con los precios congelados; pedidos pagados y facturas emitidas conservan su historia.

### Decisiones confirmadas por el usuario

1. **Moneda por producto:** cada producto/plato indica si su moneda primaria es USD o VES. El sistema no usa categoría para decidirla.
2. **Cambio de tasa:** al cambiar la tasa, los precios primarios no cambian: los productos USD conservan su monto USD y actualizan la referencia VES; los VES conservan su monto VES y actualizan la referencia USD.
3. **Edición de catálogo:** editar precio o condición fiscal de un producto actualiza sus líneas asociadas en pedidos abiertos y refresca la interfaz.
4. **Pedidos abiertos:** al cambiar la tasa se recalculan sus importes. Los pedidos guardados aún abiertos deben poder llevarse a valores del catálogo actual una vez que la línea esté vinculada al producto correcto.
5. **Cierre y pago:** el pago normal se realiza al final, una vez cerrada la mesa. Mientras la mesa está abierta, los cambios permitidos de tasa/catálogo pueden recalcular sus líneas; al cerrarla se fija el precio, la condición fiscal, la tasa y el total de cobro. La cotización cerrada no cambia mientras espera el pago, aunque cambie la tasa o el catálogo. El pago liquida esa cotización congelada.
6. **Pagos anteriores/legados:** cualquier importe ya registrado se conserva y nunca se modifica o borra silenciosamente para cuadrar el total. El flujo normal nuevo no requiere anticipos parciales. Para registros legados con pagos, mantener una conciliación explícita basada en los equivalentes guardados al momento del pago; no revalorizar con la tasa actual.
7. **Factura:** el usuario indicó que la factura no debe cambiar de formato; lo que cambia son los cálculos. Al cerrar mesa se congela el snapshot que alimenta el cobro/factura; tras emitirse, factura y pedido finalizado quedan inmutables. El ajuste de redondeo/cambio pertenece a conciliación de pago/caja, no al total fiscal del documento.
8. **IVA incluido:** una condición separada debe indicar si el precio contiene IVA; se aplica a líneas gravadas. El usuario señaló que ciertos precios de bebidas en VES se pactan IVA incluido. No generalizar esa regla a todos los productos VES: clasificar producto por producto.

### Nuevas condiciones que amplían el alcance

- Cambiar tasa no modifica manualmente el precio fuente; actualiza referencias de catálogo y los totales/snapshots monetarios solo de mesas abiertas en la misma transacción.
- Editar manualmente precio, moneda base, `aplica_iva` o `precio_incluye_iva` del catálogo propaga la condición nueva a líneas ligadas solo de mesas abiertas y recalcula subtotal, desglose IVA y total.
- Debe existir un estado distinguible de **mesa abierta**, **mesa cerrada pendiente de pago** y **pedido pagado/finalizado**. No asumir que `estado='PENDIENTE'` equivale a “abierto para recálculo”: la mesa cerrada puede seguir pendiente en BD mientras espera pago.
- El cierre de mesa debe congelar, en una sola transacción, snapshot de líneas, política/porcentaje IVA, tasa, total fiscal y valores de liquidación. El proceso de pago posterior lee solo ese snapshot y no consulta precios actuales para recalcularlo.
- Un total recalculado mientras la mesa está abierta puede afectar registros legados que ya tengan pagos; en ese caso los eventos de pago no se editan y la conciliación/excedente debe ser trazable. No ampliar por defecto el flujo nuevo a anticipos parciales, ya que el usuario aclaró que el pago ocurre tras cerrar la mesa.
- Las líneas de pedido deben persistir vínculo a producto, precio y moneda fuente, aplicabilidad IVA, si estaba incluido, porcentaje IVA y tasa de pedido. Los pedidos cerrados conservan todos esos valores disponibles al cierre.
- El catálogo debe poder quedar temporalmente sin clasificar durante el corte; artículos sin precio primario/moneda/condición IVA confirmados no pueden añadirse a pedidos.
- Guardar precio USD y VES con semántica explícita; una sola moneda es autoritativa según `moneda_base`; el valor de la otra moneda es referencia recalculada. El programa no usa nombre de categoría como regla monetaria.

### Supuestos/recomendaciones de este plan que requieren atención

- **Pagos legados en otra moneda:** si existen pagos asociados a pedidos aún abiertos, conservar moneda, importe, tasa y equivalente VES ya registrados. El pago normal ocurre tras cierre; cualquier excepción/legado debe conciliarse sin revalorizar el evento ni convertirlo usando la tasa actual.
- **IVA general de Configuración:** por defecto, cambiar el porcentaje global de IVA afecta pedidos nuevos; las líneas de pedidos abiertos retienen su porcentaje snapshot. Una edición de `aplica_iva`/`precio_incluye_iva` del producto sí propaga el cambio a abiertos, según la regla de edición de catálogo. Aplicar una nueva tasa general a pedidos abiertos es una decisión distinta todavía no confirmada.
- **Precio heredado:** mantenerlo intacto durante migración, generar inventario por fila y dejar producto sin clasificar/no vendible hasta confirmar moneda, precio primario y condición IVA. La conversación previa sugiere que los precios actuales están en VES; úsese como hipótesis para preparar el reporte, no como autorización para un `UPDATE` masivo sin comparar datos reales.
- **Diferencia por pagar en USD:** la fuente contractual de saldo es VES, conservando importes VES pactados. Si el saldo no puede expresarse exactamente con centavos USD, permitir únicamente el residuo fijado por la fórmula de este plan, registrándolo en `pedido_liquidaciones`. El límite se calcula una vez sobre el agregado USD del lote final y nunca crece por dividirlo en varias filas.
- **Excedente en caja:** distinguir un pago recibido mayor que el total congelado (por ejemplo, efectivo entregado y cambio devuelto) de un abono previo. Registrar monto recibido y cambio/devolución para que el importe aplicado al pedido sea exactamente el total congelado. Si un medio no admite cambio/devolución inmediata, bloquear cierre contable o exigir resolución identificada y auditable; no crear crédito anónimo ni autoaplicarlo a otra venta. La política contable final requiere aprobación local.
- **Pedidos abiertos heredados:** al no tener `id_plato`, presentar candidatos y exigir vínculo/clasificación explícitos antes de recalcular. No escoger automáticamente un producto por coincidencia de nombre. Los cerrados no se vinculan ni se recomputan.

### Instrucciones para el agente ejecutor

- **Prerequisito (P1-B):** Antes de iniciar Task 1, el árbol de trabajo debe estar limpio (`git status` sin archivos modificados ni sin seguimiento relevantes). Si existen cambios locales en archivos que este plan modifica (`BD.sql`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `MigradorEsquemaJdbc.java`, `PedidosDao.java`, `Sistema.java`, `GeneradorPdfPedido.java`, etc.), hay que commitearlos o stashearlos con una etiqueta descriptiva antes de continuar. No usar reset/revert.
- Antes de tocar código, inspecciona `AGENTS.md`, `CONTEXT.md`, los cambios locales de Git, el esquema desplegado y las rutas de cálculo/pago actuales. El repositorio puede contener trabajo local ajeno a esta funcionalidad; no uses reset/revert ni sobrescribas cambios existentes.
- Trata `precio_usd`/`precio_ves`, snapshots de línea, `pedidos.tasa_cambio` y los eventos de pago como datos financieros autoritativos según las invariantes del plan. Los valores que llegan desde Swing son entrada no confiable: vuelve a leer catálogo/estado dentro de la transacción.
- Modela explícitamente el cierre de mesa independiente del pago. Solo mesas realmente abiertas pueden entrar en recálculos por tasa o catálogo (`estado pendiente` por sí solo no basta); al cerrar se congela la cotización y cualquier pago posterior liquida exactamente esa versión.
- No mezcles actualización de tasa, edición de producto y cobro en escrituras parciales. Debe ocurrir todo en una transacción InnoDB con locks en orden consistente, cálculo común y auditoría antes del commit; refresca UI solo después del commit.
- No alteres importes ni reimpresiones históricas al instalar la migración. Si un snapshot histórico no puede reconstruirse, conserva la representación legacy y reporta la incertidumbre.
- Sigue las restricciones operativas de `AGENTS.md`: migraciones SQL duplicadas byte-idénticas, tests Swing E2E solo mediante `./e2e_virtual.sh` con Xvfb `:99`, nada de `ant test` contra la BD real, y no reiniciar la aplicación desplegada durante el servicio.

## Global Constraints

- Conservar la arquitectura Vista → Controlador → Servicio → DAO/Repositorio → MySQL.
- La moneda base se decide por producto y nunca por categoría o texto del nombre.
- `aplica_iva` (gravado/exento) y `precio_incluye_iva` (precio final/base) son decisiones distintas; la segunda solo aplica cuando `aplica_iva = 1`.
- Para producto VES, el importe VES es la fuente de verdad y no se modifica al cambiar la tasa; para USD, el importe USD es la fuente de verdad.
- Cambio de tasa y edición de precio/fiscalidad del catálogo actualizan únicamente pedidos/mesas que sigan abiertos, de forma atómica. Una mesa cerrada y pendiente de pago tiene cotización congelada, aunque `estado` de pedido siga siendo PENDIENTE en el esquema actual; FINALIZADO/ANULADO, facturas emitidas y pagos registrados tampoco se reescriben.
- Los pagos posteriores al cierre liquidan el total snapshot de cierre, sin consultar la tasa/catálogo actual. Los montos de pagos legados se mantienen en moneda, monto, tasa y medio originales; no se revalorizan. El saldo nunca se representa con valor negativo.
- No inferir moneda desde categoría. No inferir vínculo de líneas históricas a productos cuando el nombre no sea inequívoco.
- Cambios SQL de `actualizar_bd.sql` y `db/migrations/actualizar_bd.sql` deben quedar byte-idénticos, según `AGENTS.md`; `BD.sql` debe representar el esquema nuevo.
- No ejecutar `ant test` en una BD real. Las pruebas E2E Swing van solamente por `./e2e_virtual.sh [clase]`; no reiniciar el proceso desplegado durante servicio.
- Los documentos fiscales ya emitidos conservan su representación histórica; cualquier ajuste fiscal de factura emitida se revisa con contabilidad antes de implementar documentos rectificativos.
- **(P1-A)** El método `validarPedidoPersistible` en `PedidosDao` asume precio excluido de IVA y rompe con `precio_incluye_iva=true`. Task 4 debe reemplazarlo íntegramente por una llamada a `CalculoMonetarioPedido`; no puede coexistir con él ni mantenerse como fallback. No debe quedar ningún camino en producción que recalcule totales de pedido fuera de `CalculoMonetarioPedido`.


---

## Decisiones monetarias y fiscales

1. `platos.moneda_base` será `USD` o `VES`; `platos.precio_usd` y `platos.precio_ves` son campos explícitos. `moneda_base` decide cuál es fuente de verdad y el otro es referencia convertida. La columna `platos.precio` se conserva temporalmente como compatibilidad y no es fuente de verdad. El formulario etiqueta y modifica solo el precio de la moneda primaria. Sin tasa positiva, no se inventa el equivalente: queda NULL hasta configurar una tasa; un producto VES publicado exige `precio_ves` positivo.
2. `precio_incluye_iva` será nullable durante transición: NULL significa política heredada sin clasificar. Nuevos productos y productos modificados deben quedar clasificados; `aplica_iva` (gravado/exento) y `precio_incluye_iva` (precio final/base) son distintos. Para exento, la UI deshabilita y persiste `false`. Si gravado e incluido, redondear la base a 2 decimales y calcular IVA como `bruto - base` para que `base + IVA = precio pactado`; si gravado y excluido, el valor fuente es base y suma IVA redondeado al centavo.
3. Cada línea conserva `id_plato` nullable, nombre/precio fuente, moneda base, `aplica_iva`, `precio_incluye_iva` e `iva_porcentaje`. En pedidos abiertos snapshots se actualizan desde catálogo; en cerrados quedan congelados. El servidor relee catálogo al escribir y no confía en datos monetarios/fiscales de Swing. `pedidos.tasa_cambio` es tasa snapshot.
4. El total de liquidación en VES respeta exactamente precios VES; USD a dos decimales es referencia, no una segunda fuente exacta. Líneas se extienden/redondean con `HALF_UP`; cálculos intermedios usan escala 8. Al liquidar, primero se suman todos los componentes USD del lote final (no se redondea cada fila para medir el límite) y los VES aplicados por separado. Si el saldo restante en VES dividido por la tasa de cierre difiere del total USD agregado por no más de `0,005 USD`, se acepta el lote y registra `ajuste_cambio_bs = total_bs_congelado - monto_bs_aplicado_agregado`; por tanto `monto_bs_aplicado_agregado + ajuste_cambio_bs = total_bs_congelado`. El pago conserva su equivalente real a tasa de cierre; el ajuste FX es una partida separada de conciliación, nunca se suma al cobro aplicado ni al vuelto. El tope fijo no crece al dividir un pago en más filas. Si el residuo excede el límite, se rechaza. Pago VES completa saldo VES exacto. Se conserva diseño e importes fiscales de factura/ticket; ajuste queda en conciliación/caja, no cambia la factura.
5. `saldo_bs = max(total_bs_actual - pagado_bs_aplicado, 0)`; `excedente_bs = max(pagado_bs_aplicado - total_bs_actual, 0)`. Pagos conservan moneda, importe, tasa y medio originales. Excedente se asigna a pagos origen por id descendente, sin superar monto pagado, y se devuelve en moneda/medio original si el medio es reversible; otro caso requiere crédito de cliente identificado o resolución manual. Persistir cada asignación y el residuo auditable; nunca aplicar crédito automáticamente. Consumidor Final no puede dejar crédito anónimo. Finalizar o anular exige saldo liquidado y excedente resuelto. Contabilidad aprueba tolerancia y comprobantes antes de producción.

**Cambio de IVA global (decisión todavía no confirmada):** la implementación base mantiene `iva_porcentaje` snapshot del pedido abierto aunque se cambie el valor general en Configuración; el cambio de IVA global aplica a pedidos nuevos. Una modificación directa del IVA incluido/exento en la ficha de producto sí actualiza pendientes como pidió el usuario. Si el negocio/contador decide que una nueva tasa fiscal debe afectar pedidos abiertos, aprobarlo antes de implementación: transacción explícita como el cambio de divisa, con precio bruto VES incluido fijo (60,00 sigue total 60,00) y nuevo desglose base/IVA; nunca reutilizar `precio_incluye_iva=false` por defecto.

## Mapa de archivos

- `src/Modelo/Platos.java`, `src/Modelo/DetallePedido.java`, `src/Modelo/Pedidos.java`: propiedades de moneda, snapshots y estado de mesa cerrada pendiente de cobro.
- `src/Servicio/CalculoMonetarioPedido.java` (nuevo): cálculo puro USD/VES e IVA, independiente de Swing/JDBC.
- `src/Modelo/PlatosDao.java`, `src/Servicio/PlatosServicio.java`, `src/Vista/Sistema.java`: persistencia/validación y formulario de catálogo.
- `src/Modelo/PedidosDao.java`, `src/Modelo/PedidosRepositorio.java`, `src/Servicio/PedidoServicio.java`, `src/Controlador/PedidosControlador.java`: snapshots, recálculo transaccional, cierre de mesa, cobro posterior y concurrencia.
- `src/Modelo/AuditoriaPreciosDao.java` (nuevo): insertar auditoría transaccional sin abrir una conexión distinta.
- `src/Modelo/LoginDao.java`, `src/Servicio/ConfiguracionServicio.java`, `src/Vista/Sistema.java`, `src/Vista/PanelAtencionMesasSplit.java`: cambio de tasa, visibilidad de montos y actualización Swing.
- `src/Servicio/GeneradorPdfPedido.java`, `src/Servicio/ServicioImpresionTicket.java` y pruebas PDF/ticket: salida de importes coherente.
- `BD.sql`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `src/infraestructura/MigradorEsquemaJdbc.java`: migración idempotente y esquema de instalación.
- Pruebas focalizadas en `test/Modelo`, `test/Servicio`, `test/Vista`, `test/infraestructura`, además de ITs MySQL bajo `test/integracion`.

## Plan de implementación

### Task 1: Acordar la semántica de importes y crear el cálculo de dominio

**Files:**
- Create: `src/Servicio/CalculoMonetarioPedido.java`
- Create: `test/Servicio/CalculoMonetarioPedidoTest.java`
- Modify: `src/Modelo/Platos.java`
- Modify: `src/Modelo/DetallePedido.java`

**Interfaces:**
- `CalculoMonetarioPedido.calcularLinea(BigDecimal precioBase, int cantidad, String monedaBase, boolean aplicaIva, Boolean precioIncluyeIva, BigDecimal ivaPorcentaje, BigDecimal tasa): CalculoLinea`; si `precioIncluyeIva == null`, no calcula y devuelve error de clasificación pendiente.
- `CalculoMonetarioPedido.calcularPedido(List<DetallePedido> detalles, BigDecimal tasa): CalculoPedido`
- Producir records inmutables `CalculoLinea` y `CalculoPedido`: por línea incluir base/exento/IVA/total en moneda fuente y base/exento/IVA/total VES, total USD referencial y ajuste; por pedido agregar totales VES, suma de las referencias USD de línea, tasa, ajuste y una copia inmutable de las líneas calculadas. El precio fuente es autoritativo; el USD convertido desde VES no sustituye el importe VES.
- `Platos` proveerá `getMonedaBase()/setMonedaBase()`, precios explícitos USD/VES y `Boolean precioIncluyeIva` nullable durante clasificación.
- `DetallePedido` proveerá `idPlato`, moneda base, `Boolean precioIncluyeIva`, porcentaje IVA snapshot nullable y su precio fuente.

- [x] Añadir pruebas que fijen los casos: 60,00 VES con IVA 16% incluido → base 51,72 VES, IVA 8,28 VES, total 60,00; USD 10,00 gravado con IVA 16% excluido → base 10,00 USD, IVA 1,60 USD, USD 11,60 referencial; exento → IVA 0; 3 × 60,00 VES → total VES 180,00 exacto; 60,03 VES / tasa 40 → referencia USD 1,50 y residuo de liquidación 0,03 VES. Para cantidades mayores a 1, extender primero precio × cantidad y desglosar IVA una sola vez por línea.
- [x] Añadir pruebas de tasa cero/negativa, moneda desconocida, null, cantidades inválidas, máximo de escala y redondeos de división; cada entrada inválida produce error de validación, no `ArithmeticException` filtrada.
- [x] Ejecutar `ant -q compile` y el test dirigido `ant -q -Djavac.includes=Servicio/CalculoMonetarioPedidoTest.java -Dtest.includes=Servicio/CalculoMonetarioPedidoTest.java test-single`. El target NetBeans requiere ambos parámetros; solo `test.includes` falla antes de ejecutar pruebas.
- [x] Implementar la clase pura con `BigDecimal`, `RoundingMode.HALF_UP`, escala intermedia 8 y resultados en escala 2; extender `precio × cantidad` primero y desglosar IVA una sola vez por línea; validar montos contra precisión `DECIMAL(14,2)`, tasas contra `DECIMAL(12,4)`, rechazar tasas no positivas y no inferir moneda/fiscalidad ausente.
- [x] Repetir las pruebas dirigidas y confirmar total VES contractual, USD referencial redondeado y residuo explícito. También pasan `PlatosTest` y `DetallePedidoTest` dirigidos.

### Task 2: Ampliar esquema y migración sin reescribir ventas históricas

**Files:**
- Modify: `BD.sql`
- Modify: `actualizar_bd.sql`
- Modify: `db/migrations/actualizar_bd.sql`
- Modify: `src/infraestructura/MigradorEsquemaJdbc.java`
- Create: `src/Modelo/AuditoriaPreciosDao.java`
- Modify: `test/infraestructura/MigradorEsquemaJdbcTest.java`
- Create: `test/Modelo/AuditoriaPreciosDaoTest.java`
- Modify: `test/integracion/MySqlIntegrationIT.java`

**Interfaces:**
- `platos`: `moneda_base CHAR(3) NULL`, `precio_usd DECIMAL(14,2) NULL`, `precio_ves DECIMAL(14,2) NULL`, `precio_clasificado TINYINT(1) NOT NULL DEFAULT 0`, `precio_incluye_iva TINYINT(1) NULL`; producto no clasificado queda fuera de pedidos nuevos. CHECK permite `precio_clasificado=0` como legado/borrador; si es 1 exige moneda USD/VES, importe primario positivo y política IVA explícita. **(P2-F)** Añadir también `CHECK (aplica_iva = 1 OR precio_incluye_iva IS NULL OR precio_incluye_iva = 0)` para garantizar la invariante fiscal incluso contra migraciones manuales o bugs futuros.
- `detalle_pedidos`: `id_plato INT NULL`, `precio DECIMAL(14,2)` para precio fuente nuevo, `moneda_base CHAR(3) NULL` para desconocido heredado (requerida por DAO en escrituras nuevas), `precio_incluye_iva TINYINT(1) NULL`, `iva_porcentaje DECIMAL(5,2) NULL` y estado auditable de precio congelado para líneas heredadas sin producto (por ejemplo `precio_congelado`, `congelado_motivo`, `congelado_por`, `congelado_en`); FK nullable `ON DELETE SET NULL` preserva snapshot si se borra físicamente el producto. Líneas congeladas no se actualizan por tasa/catálogo. **(P2-F)** Añadir el mismo CHECK de coherencia IVA sobre `detalle_pedidos`.
- `pedidos.subtotal`, `pedidos.iva_monto` y `pedidos.total` hoy son `DECIMAL(10,2)`, mientras los importes fuente VES y `subtotal_bs`/`iva_bs`/`total_bs` admiten `DECIMAL(14,2)`. Ampliar los tres campos USD a `DECIMAL(14,2)` en la misma migración, preservando escala y valor numérico existente. Esta opción mantiene un rango consistente con `platos.precio_usd`/`precio_ves` y `detalle_pedidos.precio`; no inferir ni recalcular importes históricos al ampliar.
- `pedidos`: añadir una marca persistida de cierre de mesa (por ejemplo `cerrada_en DATETIME NULL`) o estado equivalente, separada de `estado='FINALIZADO'` (pedido pagado). Cierre congela snapshot de líneas, tasa y total; un pedido cerrado y pendiente de pago no participa en cambios posteriores de catálogo/tasa. **Recomendación por defecto:** cerrar servicio libera el número físico de mesa; cambiar columnas generadas/índice para reservarla solo para pedidos abiertos (`cerrada_en IS NULL`). Todo lector/cargador/selector por mesa debe entonces buscar explícitamente `estado='PENDIENTE' AND cerrada_en IS NULL`; cobro de una mesa ya cerrada selecciona el pedido por `id_pedido` y versión congelada, nunca por número de mesa. Cubrir el caso con una mesa cerrada sin pagar y otro pedido abierto posterior en el mismo número. Si el comportamiento actual de negocio reserva la mesa hasta pagar, documentarlo y conservar esa exclusión de forma explícita. No marcar ventas históricas como cerradas por inferencia.
- `platos.precio` heredado se conserva temporalmente sin copiarlo automáticamente a `precio_usd` ni cambiar su moneda supuesta. La migración genera un reporte por producto/línea, sin inferir que moneda heredada sea USD/VES por nombre o categoría. La operación de corte debe confirmar con el negocio si cada valor heredado se clasificará VES (según la descripción actual del sistema) o USD antes de poblar los nuevos campos. Precios no confirmados quedan `precio_clasificado=0` e invisibles a venta. **(P2-A)** `platos.precio` es `NOT NULL` en la BD desplegada; la migración debe hacer la columna `NULL DEFAULT NULL` o el DAO debe escribir en ella el precio primario como alias mientras siga existiendo la columna (elegir y documentar). No es posible insertar un producto nuevo sin darle valor a `precio` hasta que se elimine la columna o se haga nullable.
- Crear aquí, antes de Tasks 5–7, `auditoria_cambios_precio`, ledger de crédito/devolución, `pedido_liquidaciones` para liquidaciones finales y `pedidos.version_precio`; `pedido_liquidaciones` persiste `ajuste_cambio_bs` (solo residuo de conversión FX, no vuelto), tasa de cierre, importes aplicados agregados USD/VES, residuo exacto, versión de regla y actor. En `pedido_pagos`, persistir por evento/medio/moneda `monto_recibido`, `monto_aplicado`, `cambio_devuelto`, moneda, tasa y equivalente VES aplicado (`monto_bs`); definir `monto_bs` como importe aplicado al pedido, no efectivo bruto recibido. Validar `monto_recibido = monto_aplicado + cambio_devuelto` por fila en moneda de origen. Caja informa ventas por aplicado y movimientos de caja por recibido/vuelto. Cambiar FK de `pedido_pagos` a `ON DELETE RESTRICT`. Pedidos con eventos monetarios se anulan/liquidan, nunca se borran. **(P2-E)** `AuditoriaPreciosDao` es una clase DAO distinta de la ya existente `AuditoriaPedidosDao`; no extender esta última. `AuditoriaPreciosDao` escribe en `auditoria_cambios_precio` (nueva tabla), mientras `AuditoriaPedidosDao` escribe en `auditoria_pedidos` (ya existente). El agente ejecutor no debe confundir ambas ni consolidarlas.
- La migración sigue la versión/convención actual del migrador y sus postcondiciones verifican columnas, checks, índices y FK.

- [x] Escribir pruebas de migración sobre BD scratch para: esquema viejo, ejecución repetida, precio heredado conservado sin asignarle moneda, producto `precio_clasificado=0` excluido de catálogo de venta, CHECK que rechaza un producto marcado como clasificado sin moneda/precio/política IVA, CHECK que rechaza `aplica_iva=0 AND precio_incluye_iva=1` tanto en `platos` como en `detalle_pedidos`, ampliación a `DECIMAL(14,2)` de `pedidos.subtotal`/`iva_monto`/`total` sin alterar valores, FK nullable y `ON DELETE RESTRICT`, estado auditable de congelamiento de líneas, marca de cierre nullable sin inferir cierres históricos y semántica del índice único de mesa; verificar que pedidos finalizados, pagos, totales y reimpresiones históricas no cambian.
- [x] Ejecutar `ant integration-test` únicamente contra el target oficial aislado `restaurante_test` que crea y elimina el compose; esperado: falla inicialmente por columnas/postcondiciones ausentes.
- [x] Conservar `platos.precio` heredado y generar informe de clasificación. No copiarlo automáticamente a USD ni VES. En la ventana de corte, el usuario responsable confirma la moneda base de cada producto (la indicación previa del negocio es que los precios existentes están en bolívares, pero validar contra catálogo y configuración reales antes de cargar); calcular solo la referencia de la otra moneda con tasa positiva. Dejar `precio_clasificado=0` y ocultar artículo hasta confirmación; no deducir IVA incluido/excluido para cerveza ni otras bebidas. **(P2-A)** La migración debe también alterar `platos.precio` a `NULL DEFAULT NULL`; documentar que el DAO de Task 3 ya no escribe en esa columna para ningún flujo nuevo.
- [x] En FINALIZADO/ANULADO dejar `id_plato`, `precio_incluye_iva` e `iva_porcentaje` NULL; no recomputar sus totales, desglose ni reimpresión desde catálogo. Mantener la representación histórica usando los acumulados guardados y el camino legacy cuando el snapshot fiscal sea desconocido.
- [x] En PENDIENTE conservar `moneda_base=NULL` para líneas sin snapshot; no asignar USD por DEFAULT ni enlazar por `nombre_clave`. Producir informe de candidatos y permitir reprecificación al catálogo actual solo tras confirmación explícita de producto, moneda e IVA; líneas no confirmadas no se reprecifican ni se cobran hasta su resolución. Ampliar `detalle_pedidos.precio` de DECIMAL(10,2) a DECIMAL(14,2) sin cambiar sus valores.
- [x] Crear tablas/constraints `auditoria_cambios_precio`, ledger de saldo y `pedido_liquidaciones`; esta última guarda por cierre `saldo_restante_ves`, montos aplicados agregados USD/VES, `tasa_cambio`, `ajuste_cambio_bs` (residuo FX únicamente), `diferencia_usd`, `version_regla`, actor y timestamp, y enlaza las filas de pago finales por `id_liquidacion`. Añadir a `pedido_pagos` recibido/aplicado/vuelto con moneda/tasa y CHECK o validación transaccional `recibido = aplicado + vuelto`; `monto_bs` representa aplicado. Añadir `pedidos.version_precio`, clave única por obligación/version y `UNIQUE(idempotency_key)` para devolución/crédito. Implementar `AuditoriaPreciosDao.insertar(Connection, ...)` (clase separada de `AuditoriaPedidosDao`). Cambiar FK de `pedido_pagos` a `ON DELETE RESTRICT`; conservar y marcar legado los pagos ya existentes.
- [x] Persistir el cierre de mesa como transición transaccional distinta del pago: guardar `cerrada_en` (o equivalente) y congelar snapshots/totales. No asignar cierre a pedidos viejos por estado/nombre ni alterar importes históricos. Si el sistema actual cierra y cobra en una sola acción, mantener una ruta atómica compatible, pero probar también el estado cerrado sin pago para el caso de pago diferido. Aplicar la semántica decidida para liberar o reservar la mesa en las columnas generadas/índice único existentes y todos los lectores por mesa.
- [x] En `BD.sql`, marcar los registros de catálogo semilla `precio_clasificado=0` salvo que exista una decisión de moneda/precio documentada para ese registro; los asistentes deben clasificar antes de poner el catálogo a la venta. Mantener SQL de migración byte-idéntico con `cmp -s actualizar_bd.sql db/migrations/actualizar_bd.sql`; esperado: código 0. Implementar iguales postcondiciones en `MigradorEsquemaJdbc` y tests idempotentes para columnas, ledger, versión, constraints, FK RESTRICT e invariancia histórica.
- [x] Ejecutar `ant integration-test`; esperado: migración se aplica una vez, vuelve a ejecutarse sin cambios ni duplicados, los históricos permanecen invariantes y las restricciones se comprueban en MySQL.


### Task 3: Persistir catálogo dual y validar el formulario

**Files:**
- Modify: `src/Modelo/PlatosDao.java`
- Modify: `src/Servicio/PlatosServicio.java`
- Modify: `src/Vista/Sistema.java`
- Modify: `src/Vista/PanelAtencionMesasSplit.java`
- Modify: `test/Modelo/PlatosDaoTest.java`
- Modify: `test/Servicio/PlatosServicioTest.java`
- Create: `test/Vista/MonedaBaseCatalogoTest.java`

**Interfaces:**
- DAO carga y guarda `moneda_base`, `precio_usd`, `precio_ves`, `precio_clasificado`, `aplica_iva`, `precio_incluye_iva` juntos; deja `precio` heredado intacto y deja de leerlo tras resolver cutover. Los fallbacks legacy no pueden ocultar errores SQL distintos de “columna inexistente”.
- Servicio solo permite venta si precio_clasificado, precio primario positivo, tasa positiva para cotizar y política IVA explícita; valida hasta dos decimales y excluye IVA incluido cuando exento.
- UI muestra selector `USD/VES`, etiqueta de moneda en precio, precio de referencia convertido no editable y casilla “Precio incluye IVA” habilitada únicamente si gravado; producto legado sin clasificar muestra aviso y no permite agregarlo a pedidos hasta que un usuario autorizado decida.

- [ ] Agregar pruebas de persistencia round-trip USD/VES, de que cambiar categoría no altera moneda y que apagar `aplica_iva` fuerza `precio_incluye_iva = false`.
- [ ] Ejecutar `ant -q '-Dtest.includes=**/PlatosDaoTest.java' test-single` y luego `ant -q '-Dtest.includes=**/PlatosServicioTest.java' test-single`; esperado: los modelos viejos conservan comportamiento USD por defecto y las nuevas combinaciones se validan.
- [ ] Actualizar SELECT/INSERT/UPDATE y conversores de `PlatosDao`; no leer/escribir `precio` heredado desde el nuevo flujo y filtrar por `activo=1 AND precio_clasificado=1`. Solo usar fallback legacy al detectar SQLState/error code de columna faltante e informar error legible si la migración falta.
- [ ] Extender `Platos` y normalización del servicio; el usuario cambia únicamente el precio de la moneda primaria; al editar producto USD calcular `precio_ves = precio_usd × tasa`, al editar producto VES calcular `precio_usd = precio_ves ÷ tasa` redondeado solo como referencia; persistir ambos campos y marcar clasificado solo al guardar moneda primaria + precio + IVA explícitos.
- [ ] Extender alta, edición, selección y carga de catálogo en `Sistema`; no deducir moneda de categoría. Al guardar modificación, invocar un caso de uso que actualice mesas abiertas (Task 5) y excluir mesas cerradas aún no pagadas; notificar `RefrescoVistas` una sola vez al terminar la transacción.
- [ ] Actualizar precio mostrado en grillas/carrito en `PanelAtencionMesasSplit` para que USD-base se convierta a VES y VES-base se muestre directamente en VES con su referencia USD, manteniendo el layout actual.
- [ ] Ejecutar `ant -q compile` y tests focalizados Swing; esperado: catálogo crea, recarga y edita ambos tipos de moneda sin confundir moneda fuente ni estado de IVA.

### Task 4: Snapshot fiscal y monetario por línea de pedido

**Files:**
- Modify: `src/Modelo/PedidosDao.java`
- Modify: `src/Modelo/Pedidos.java`
- Modify: `src/Modelo/DetallePedido.java`
- Modify: `src/Servicio/PedidoServicio.java`
- Modify: `src/Vista/PanelAtencionMesasSplit.java`
- Modify: `src/Vista/Sistema.java`
- Modify: `test/Modelo/PedidosDaoTest.java`
- Modify: `test/Servicio/PedidoIvaIntegracionFlujoTest.java`
- Modify: `test/Vista/EdicionPedidoPendienteTest.java`

**Interfaces:**
- INSERT y UPDATE de líneas guardan `id_plato`, importe fuente, `moneda_base`, `aplica_iva`, `precio_incluye_iva`, `iva_porcentaje` y nombre snapshot.
- Lectura histórica tolera `id_plato = NULL`; no consulta precio maestro para pedidos finalizados.
- Toda creación/edición calcula resumen USD/VES llamando a `CalculoMonetarioPedido`; rechaza totales manipulados o inconsistentes que el cliente Swing envíe. **(P1-A)** El método `validarPedidoPersistible` de `PedidosDao` **se elimina completamente** en esta task y se reemplaza por una llamada a `CalculoMonetarioPedido`; no puede quedar como alternativa ni fallback. Cualquier otra ruta de cálculo de totales que exista en el DAO debe eliminarse o redirigirse al servicio puro.
- **(P2-B — despliegue conjunto):** Task 2 (esquema, `DECIMAL(14,2)` en precios fuente, columnas USD de pedido y DAO) y Task 4 deben ir empaquetados en la misma versión del JAR. Si los ALTER TABLE se aplican a una BD pero el JAR aún es el anterior, la validación de escala de `PedidosDao` puede rechazar importes legítimos o los nuevos totales pueden superar la capacidad de columnas USD antiguas. Planificar el cutover para que migración y JAR nuevo se activen al mismo tiempo.

- [ ] Cubrir round-trip de todos los atributos fiscales, mix USD/VES, flags de gravado/exento, y editar una línea histórica no ligada sin cambiar su nombre/precio antes de seleccionar explícitamente otro producto.
- [ ] Cubrir específicamente el caso `precio_incluye_iva=true`: verificar que la validación resultante produce base = bruto / (1 + iva%) y no suma IVA encima del bruto. Confirmar que el test falla con el `validarPedidoPersistible` original y pasa con `CalculoMonetarioPedido`.
- [ ] Ejecutar `ant -q '-Dtest.includes=**/PedidosDaoTest.java' test-single` y luego `ant -q '-Dtest.includes=**/PedidoIvaIntegracionFlujoTest.java' test-single`; esperado: los tests iniciales detectan snapshot incompleto y luego pasan.
- [ ] Cambiar todas las inserciones, lecturas, reemplazo de detalles y validación de `PedidosDao`; eliminar `validarPedidoPersistible` y su lógica ad hoc; mantener precio y moneda fuente por línea, y almacenar tasa/IVA de pedido vigentes cuando se crea o modifica.
- [ ] Sustituir sumas/conversiones ad hoc del carrito y edición de pedido por el servicio puro; mostrar VES pactado exacto para líneas VES y calcular IVA incluido hacia atrás sin subir el total acordado.
- [ ] En alta y edición, tomar nombre, precio, moneda e IVA desde el catálogo leído dentro de la misma transacción, usando `id_plato`; no confiar en valores monetarios o fiscales que lleguen desde Swing. Una vista obsoleta debe recibir error de conflicto/recargar catálogo, no restaurar precio viejo.
- [ ] Verificar con el test de edición que modificar cantidades conserva snapshots y volver a guardar no borra identificadores ni comentarios; esperado: línea pertenece al producto elegido y los acumulados coinciden con el cálculo central.


### Task 5: Propagar ediciones de catálogo a todos los pedidos abiertos

**Files:**
- Modify: `src/Modelo/PlatosRepositorio.java`
- Modify: `src/Modelo/PlatosDao.java`
- Modify: `src/Servicio/PlatosServicio.java`
- Create: `src/Modelo/PreciosPedidosDao.java`
- Modify: `src/Controlador/PlatosControlador.java`
- Modify: `test/Modelo/PedidosDaoTest.java`
- Create/modify: `test/integracion/ActualizacionCatalogoPedidosAbiertosIT.java`

**Interfaces:**
- `modificarProductoYRecalcularPendientes(Platos producto, String usuario): ResultadoActualizacionPrecios` ejecuta una sola transacción InnoDB.
- `ResultadoActualizacionPrecios` contiene `productoActualizado`, cantidad de pedidos recalculados, total anterior/nuevo y líneas no enlazadas que no se modificaron; no se publicará éxito parcial.

- [ ] Añadir pruebas con dos mesas abiertas, una mesa cerrada pendiente de pago y un pedido finalizado del mismo plato; actualizar precio/moneda/IVA. Esperado: solo las dos mesas abiertas se recalculan, el snapshot de la mesa cerrada y el finalizado quedan byte por byte iguales, los pagos legados quedan iguales y el pedido bloqueado no cambia a mitad de la operación. Invocar directamente un DAO de edición contra la mesa cerrada y exigir rechazo sin cambios.
- [ ] Añadir caso de dos productos con mismo nombre normalizado: no se propagan cambios por nombre si `id_plato` es nulo; la operación informa las líneas no enlazadas y ofrece resolución explícita en UI, sin asignarlas a ciegas.
- [ ] Ejecutar `ant integration-test`; esperado: concurrencia/rollback comprobables en MySQL, todos los pedidos o catálogo cambian juntos o ninguno cambia.
- [ ] Implementar DAO transaccional: bloquear fila de plato, luego obtener ids de pedidos pendientes y todavía abiertos (`cerrada_en IS NULL`) ordenados y bloquear filas `pedidos FOR UPDATE`; volver a comprobar estado y marca de cierre, reemplazar snapshot de líneas ligadas, recalcular por el servicio central, incrementar `version_precio`, actualizar detalle/cabecera, resolver explícitamente cualquier legado con pagos y llamar `AuditoriaPreciosDao.insertar(conexion, ...)` antes de commit. Excluir siempre mesas cerradas aunque sigan `PENDIENTE`.
- [ ] En todos los métodos DAO que añaden, editan, eliminan o reemplazan líneas/cabecera exigir que el pedido esté pendiente y abierto (`cerrada_en IS NULL`) al bloquear/verificar y en las escrituras condicionales. No basta con ocultar o deshabilitar botones Swing. Una llamada directa/obsoleta debe fallar con conflicto sin tocar snapshots. La reapertura, si se permite, será un caso de uso separado con autorización, auditoría y nueva versión de cotización.
- [ ] Inventariar consultas por número de mesa en DAO/repositorios/controladores/UI. Para operación y edición, seleccionar exclusivamente pedido abierto (`estado='PENDIENTE' AND cerrada_en IS NULL`); para cobrar pedido cerrado, usar su `id_pedido` y versión de cierre. Añadir IT con mesa N cerrada sin pagar, nuevo pedido abierto N, recarga de la lista, edición del pedido nuevo y cobro del anterior: cada acción debe afectar la fila correcta, sin consultas ambiguas ni resultados duplicados.
- [ ] Mantener un orden único de locks en todas las operaciones de precio/tasa/checkout y reintentar deadlock solo una vez cuando SQLState `40001`/1213 lo permita; si falla el segundo intento, mostrar error y rollback completo.
- [ ] Emitir evento `RefrescoVistas.PLATOS` y evento de pedidos solo después de commit; Swing recarga catálogo y mesas en EDT y presenta conteo de pedidos impactados y líneas históricas no vinculadas.

### Task 6: Actualizar tasa y pedidos abiertos sin carrera con caja

**Files:**
- Create: `src/Modelo/ActualizacionTasaDao.java`
- Modify: `src/Servicio/ConfiguracionServicio.java`
- Modify: `src/Vista/Sistema.java`
- Modify: `src/Vista/PanelAtencionMesasSplit.java`
- Modify: `test/Modelo/PedidosDaoTest.java`
- Modify: `test/Vista/ConfiguracionNegocioRifLogoTest.java`
- Create/modify: `test/integracion/ActualizacionTasaPedidosPendientesIT.java`

**Interfaces:**
- `actualizarTasaYRecalcularPendientes(BigDecimal tasaNueva, String usuario): ResultadoActualizacionPrecios` confirma configuración, referencias del catálogo, snapshots y totales en una transacción o revierte todos; solo ejecuta recálculo si cambió la tasa y la UI confirma el impacto.
- Para pedidos/productos USD, conserva USD y recalcula referencia/precio VES con tasa nueva; para VES conserva precio VES y recalcula referencia USD. IVA global nuevo solo se usa en pedidos creados desde ese cambio, salvo aprobación explícita de recalcular abiertos.

- [ ] Probar aumento/disminución de tasa con pedidos abiertos USD y VES, una mesa cerrada pendiente de pago, un pedido finalizado y un cierre concurrente. Esperado: VES no cambia por tasa en productos VES, USD no cambia en USD-base, solo mesas abiertas usan tasa nueva, la cerrada conserva el snapshot, el precio maestro de referencia USD/VES se actualiza y cierre ve pedido viejo o nuevo completo. Un cambio general de IVA conserva snapshots existentes, salvo aprobación explícita separada.
- [ ] Ejecutar `ant integration-test` sobre target aislado; esperado: rollback si falla cualquier pedido y exclusión mutua comprobada.
- [ ] Reemplazar guardado independiente de tasa por transacción que bloquee configuración, productos y pedidos pendientes todavía abiertos (`cerrada_en IS NULL`) con orden de locks consistente con Task 5 y cierre/cobro; una edición de logo/dirección o porcentaje IVA general sin autorización explícita no debe recalcular pendientes.
- [ ] Antes de cambiar tasa, comprobar solo mesas abiertas (`cerrada_en IS NULL`) para determinar si tienen líneas ligadas, moneda base y política IVA clasificadas; si hay líneas heredadas irresueltas en mesas abiertas, abortar toda la operación y mostrar IDs para que administrador confirme catálogo y condición fiscal. Las mesas cerradas quedan fuera de este bloqueo porque ya tienen cotización fija y no se recalculan; sus datos incompletos se reportan para resolución sin alterar el precio congelado. No dejar la tasa global nueva con mesas abiertas atrasadas. **(P2-C — salida de emergencia):** Si un producto ya no existe en el catálogo y sus líneas abiertas no pueden reclasificarse, proporcionar una acción explícita de administrador que persista `precio_congelado` junto con motivo/actor/fecha en el detalle (campos de Task 2), excluya esa línea del recálculo por tasa/catálogo y registre la acción en auditoría. La UI muestra cuántas líneas quedan congeladas y exige confirmación. Cubrir con test de integración y postcondición de migración.
- [ ] Recalcular resumen de línea/pedido solo para pedidos todavía abiertos (`estado='PENDIENTE' AND cerrada_en IS NULL` o predicado equivalente validado contra el modelo real). Preservar pagos legados y equivalente VES/tasa original: no revalorizar USD con tasa nueva. Una mesa cerrada pendiente de pago conserva tasa/precios/totales y no se toca. Incrementar `version_precio` e insertar auditoría en la misma transacción.
- [ ] Tras commit actualizar `Config` en memoria y notificar refresco de precio/pedidos. No actualizar controles Swing desde hilo de BD; usar `SwingUtilities.invokeLater`.
- [ ] Probar que fallo inducido en cualquier actualización impide guardar la tasa nueva; esperado: tasa config, líneas y totales no presentan mezcla de generaciones.

### Task 7: Congelar cotización al cerrar mesa y cobrar después

**Files:**
- Modify: `BD.sql`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `src/infraestructura/MigradorEsquemaJdbc.java`
- Modify: `src/Modelo/PagoPedido.java`, `src/Modelo/PedidosRepositorio.java`, `src/Modelo/PedidosDao.java`
- Modify: `src/Servicio/PedidoServicio.java`, `src/Controlador/PedidosControlador.java`, `src/Vista/Sistema.java`
- Modify: `src/Modelo/CierreCajaDao.java`, reportes de cierre relacionados
- Create: `src/Modelo/MovimientoSaldoPedido.java`
- Create: `src/Modelo/MovimientoSaldoPedidoDao.java`
- Create/modify tests en `test/Modelo/PedidosDaoTest.java`, `test/Servicio`, `test/Vista` y `test/integracion`

**Interfaces:**
- `cerrarMesa(idPedido, versionEsperada)` bloquea el pedido, verifica que siga abierto y clasificado, calcula con catálogo/tasa actuales, persiste snapshots fiscales/monetarios, guarda la versión de cotización y marca el cierre en una transacción. La operación es idempotente o rechaza reintentos con versión obsoleta; nunca cierra una cotización parcialmente actualizada.
- `pedido_pagos` se registra en el flujo normal solo después del cierre y paga la cotización congelada. La validación usa total, tasa y versión de cierre persistidos; no usa catálogo/tasa vigentes. El pedido solo pasa a FINALIZADO al completar el pago y emitir/registrar la factura conforme al flujo actual.
- `resumenConciliacion(idPedido)` para mesa cerrada devuelve total congelado, pagado histórico aplicable, saldo y excedente. **(P2-D)** El `pagado_bs_aplicado` usa exclusivamente `monto_bs` de cada fila de `pedido_pagos` tal como se guardó; jamás recalcula un pago en USD usando la tasa actual. Conservar esta regla para registros legados, aunque el flujo normal no registre anticipos.
- `pedidos.version_precio` aumenta al recalcular una mesa abierta; el snapshot/versionado de cierre no puede cambiar después de `cerrada_en`.

- [ ] Probar la secuencia: mesa abierta → cambia tasa/precio → líneas y total se recalculan; cerrar mesa → snapshot queda guardado; después cambiar de nuevo tasa/catálogo → ninguna línea/importe de mesa cerrada cambia; cobrar más tarde → pago/factura usan exactamente el snapshot de cierre.
- [ ] Resolver semántica UI/BD: si el botón actual “cerrar mesa” también cobra, separar internamente cierre de servicio y registro de pago sin alterar el flujo operativo; exponer claramente estado “cerrada, pendiente de pago”. No permitir añadir/modificar productos una vez cerrada salvo acción explícita de reapertura auditada.
- [ ] Implementar el cierre con locks/versionado, validación de productos clasificados, cálculo central, snapshots completos y marca de cierre atómica. Probar explícitamente carrera entre recálculo de tasa/catálogo y cierre: una operación gana y la otra recarga o falla por versión obsoleta; no se permite snapshot híbrido. Probar que una escritura DAO tardía no modifica la cotización cerrada.
- [ ] Implementar pago posterior leyendo solo el snapshot congelado. Aceptar pago mixto VES/USD con la tasa congelada al cierre; agregar montos aplicados por moneda antes de aplicar tolerancia, registrar `ajuste_cambio_bs` como residuo FX sin alterar el total fiscal y evitar doble cobro mediante idempotency key.
- [ ] Probar mixto: total congelado 100,00 VES; VES 40,00 + USD 1,50 a tasa de cierre 40,00 liquida; un total 100,03 VES con VES 40,00 + USD 1,50 registra ajuste FX `+0,03 VES` sin cambiar factura ni equivalentes aplicados. Verificar en ambos casos `SUM(monto_bs_aplicado) + ajuste_cambio_bs = total_bs_congelado`. Agregar residuo negativo solo si se admite en la política de pago y asegurar que no se convierta en vuelto o saldo a favor implícito. Repetir pago a tasa global posterior distinta y verificar que el resultado no cambia.
- [ ] Conservar pagos legados sin borrar ni cambiar moneda, monto, tasa o equivalente guardados. Cubrir `resumenConciliacion` con cambio de tasa y demostrar que el equivalente VES histórico no varía. Solo si se encuentra evidencia de anticipos operativos actuales, tratarlos como compatibilidad explícita fuera del camino normal y documentar su regla antes de ampliar la UI.
- [ ] Manejar efectivo entregado por encima del total congelado con campos persistidos `monto_recibido`, `monto_aplicado` y `cambio_devuelto` por fila/moneda; imponer `recibido = aplicado + vuelto` en moneda de origen. Conciliar el pago normal con `SUM(monto_bs_aplicado) + ajuste_cambio_bs = total_bs_congelado`; `monto_bs_aplicado` conserva el equivalente a tasa de cierre y `ajuste_cambio_bs` se contabiliza separado, no como pago, venta adicional ni vuelto. Probar idempotencia, que aplicado nunca exceda su monto de origen y que ventas/caja neteen solo aplicado mientras el flujo de caja física registra recibido y vuelto aparte. El vuelto nunca se registra en `ajuste_cambio_bs`. Para medios sin devolución/cambio inmediato, bloquear finalización o exigir resolución identificada y auditable; no crear crédito anónimo ni autoaplicarlo a otra venta.
- [ ] Cancelar una mesa cerrada sin pago según el flujo existente, manteniendo auditoría; impedir borrar físicamente pedidos con pagos o movimientos. Si existen pagos legados, exigir conciliación/devolución/crédito trazable antes de anular.
- [ ] Ajustar caja/reportes para reconocer el cobro cuando ocurre (después del cierre de mesa), una sola vez, y separar estado de mesa cerrada de venta pagada/finalizada. Probar corte por fecha para mesa cerrada en un día y pagada posteriormente.

### Task 8: Unificar catálogo, panel, factura, PDF y ticket

**Files:**
- Modify: `src/Vista/PanelAtencionMesasSplit.java`
- Modify: `src/Vista/Sistema.java`
- Modify: `src/Servicio/GeneradorPdfPedido.java`
- Modify: `src/Servicio/ServicioImpresionTicket.java`
- Modify: `src/Servicio/PedidoPdfServicio.java`
- Modify: `test/Servicio/GeneradorPdfPedidoTest.java`
- Create: `test/Servicio/ServicioImpresionTicketTest.java`
- Modify: `test/Vista/FinalizarPedidoSwingWorkerTest.java`

**Interfaces:**
- Presentación consume exclusivamente `CalculoPedido` y snapshots; no reconvierte precio mediante nombre/categoría ni usa de nuevo la tasa actual para un pedido cerrado.
- Diseño visual y formato de factura actual se conservan; los cálculos cambian y los valores VES se mantienen exactos. El ajuste de cambio se informa en conciliación/panel de pago, no se añade al documento fiscal.

- [ ] Añadir casos PDF/ticket para USD excluido, VES incluido 60,00 con total 60,00, exento y pedido mixto; esperado: totales e IVA coinciden con DAO, no aparecen recargos a precio incluido.
- [ ] Ejecutar `ant -q '-Dtest.includes=**/GeneradorPdfPedidoTest.java' test-single` y el test focalizado de ticket; esperado: sin cambio al diseño ya establecido y sin cálculo monetario duplicado.
- [ ] Reemplazar conversiones directas como `totalUSD × tasa` por el objeto de cálculo guardado en el pedido; eliminar forks de fórmulas duplicadas en panel, ticket y PDF.
- [ ] Mantener factura emitida/historial en formato actual, con importes congelados de cierre; filas históricas con flags IVA NULL usan renderer/valores legacy almacenados y nunca recalculan desde catálogo actual. Cambios fiscales futuros requieren nueva venta/ajuste aprobado, no reimpresión retroactiva con catálogo actual.
- [ ] Ejecutar `./e2e_virtual.sh Vista.E2EVirtual` solo si el agente de implementación confirma fixture seguro y `-De2e.permitir=SI`; esperado: flujo termina sin tocar escritorio real y elimina el pedido que crea. No usar interfaz de producción para verificar.

### Task 9: Refrescar pantallas y revisar auditoría transaccional

**Files:**
- Modify: `src/Vista/RefrescoVistas.java`, `src/Vista/Sistema.java`, `src/Vista/PanelAtencionMesasSplit.java`
- Tests nuevos bajo `test/Modelo`, `test/Servicio`, `test/Vista`, `test/integracion`

**Interfaces:**
- `AuditoriaPreciosDao` append-only registra `CAMBIO_TASA`/`CAMBIO_CATALOGO`, tasa anterior/nueva, producto, usuario, fecha, pedidos/líneas y totales antes/después dentro de las transacciones de Tasks 5–6.
- UI publica revisión de listas solamente al commit; mensajes incluyen cantidad de pedidos cambiados y si existen líneas históricas sin ID.

- [ ] Probar que cada actualización escribe exactamente una auditoría en Task 2/Tasks 5–6, reintentos idempotentes no duplican obligaciones y un rollback no deja auditoría huérfana.
- [ ] Ejecutar tests dirigidos de migración, concurrencia y actualización de UI; esperado: notificación posterior a commit y recuperación de datos del servidor, no solo cambios locales.
- [ ] Implementar evento de refresco con IDs de pedidos y productos modificados; cargar datos de nuevo desde DAO y actualizar Swing en EDT. Si otro terminal compite, al abrir/editar/finalizar un pedido recargar versión y tasa actual desde servidor.
- [ ] Comprobar que las acciones de catálogo/tasa muestran confirmación previa con conteo estimado de pedidos y explicación de que sus importes pendientes cambiarán; si falla, informar que toda la operación fue revertida.

### Task 10: Verificación integral, despliegue y operación segura

**Files:**
- Modify: `README.txt` o documentación operativa de precios/pagos
- Revisar sin reescritura: `BD.sql`, SQL duplicados, `build.xml`, `src/infraestructura/MigradorEsquemaJdbc.java`

- [ ] Revisar cobertura requisito por requisito: USD base estable con variación VES; VES base estable con variación USD; selector independiente de categoría; IVA incluido hacia atrás; exento sin IVA; cambio de tasa y catálogo recalculan solo mesas abiertas; cierre congela importes hasta cobro; pago posterior usa snapshot de cierre; pagos legados se conservan; efectivo excedente se registra como cambio; históricos congelados; interfaz/ticket/PDF coinciden.
- [ ] Ejecutar `ant -q compile jar`; esperado: éxito en JDK 21 y jar generado.
- [ ] Ejecutar tests unitarios focalizados (sin `ant test` completo contra `.env`): cálculo, catálogo, pedidos, pagos, facturación, migración; esperado: todos verdes.
- [ ] Ejecutar `ant integration-test`; esperado: suite aislada MySQL verde con migración desde BD anterior, repetición idempotente y transacciones concurrentes.
- [ ] Ejecutar pruebas visuales únicamente mediante `./e2e_virtual.sh [clase]` en Xvfb `:99`, según `AGENTS.md`; esperado: sin uso del cursor/escritorio real. No iniciar/reiniciar `Restaurante.jar` desplegado en horario de servicio.
- [ ] Revisar `git diff --check`, `cmp -s actualizar_bd.sql db/migrations/actualizar_bd.sql`, que las facturas cerradas no se recalculen, y que la migración jamás modifique montos históricos ni pagos.
- [ ] Preparar despliegue con copia de seguridad y migración validada en copia/restauración primero; el corte productivo solo se agenda tras aprobación contable del tratamiento de IVA incluido/excedentes y revisión de permisos de acceso a configuración/precios.

## Resultado de la revisión adversarial

El agente `/root/adversarial_plan_review` revisó el plan sin editar archivos, ejecutar pruebas ni acceder a BD. Marcó ocho riesgos P1/P2: conversión USD/VES que no permite igualdad matemática para precios VES impares; ambigüedad de columnas de precio; asociación peligrosa de pedidos viejos solo por nombre; IVA histórico que no puede inferirse; FK `ON DELETE CASCADE` que podía borrar evidencia de anticipos; uso de auditoría antes de crear su esquema; falta de idempotencia/distribución de excedentes multi-pago; y cancelación no definida con anticipos.

El plan actualizado responde con campos `precio_usd`/`precio_ves` explícitos; `precio_clasificado` y moneda nullable para legado; migración con reporte/confirmación (no asigna USD a lo heredado); ajuste de cambio guardado por liquidación con tope fijo de medio centavo USD al total agregado, no a cada fila; IVA incluido que conserva el bruto; snapshots IVA y decisión explícita para cambio de IVA general; congelamiento de históricos cerrados; FK `RESTRICT`; auditoría/versionamiento antes de recálculos; obligación idempotente con ajustes incrementales; asignación determinista por pago y cancelación bloqueada hasta resolver saldo. La tolerancia final y el tratamiento contable de créditos/devoluciones siguen sujetos a aprobación antes de producción, no son una regla legal inferida por el plan.

**Aclaración posterior del usuario (2026-10-15):** el cobro normal ocurre al final, una vez cerrada la mesa; desde el cierre hasta el pago se mantienen invariables los precios acordados. Esta decisión reemplaza cualquier supuesto anterior de anticipos parciales como flujo ordinario. El resultado adversarial anterior no revisó esta aclaración: antes de implementar, revisar adversarialmente el nuevo estado intermedio “cerrada pendiente de pago”, su migración, exclusión de recálculos y cobro con snapshot congelado. Los pagos/anticipos ya persistidos se mantienen como datos legados y se concilian sin revalorizar.

En la revisión final el agente confirmó que los dos últimos ajustes también quedaron consistentes: IVA global afecta pedidos nuevos por defecto, y liquidación mixta descuenta primero el componente VES antes de calcular el residuo USD. No reportó bloqueadores residuales.

## Fuentes para la decisión de excedentes

- Código Civil de Venezuela, art. 1178 sobre repetición de pago indebido: [WIPO Lex](https://www.wipo.int/wipolex/es/legislation/details/3997). Debe revisarlo asesoría local antes de convertirlo en regla automática de caja.
- IFRS 15 oficial, secciones de pasivos por reembolso y contraprestación recibida antes de transferir bienes/servicios: [IFRS Foundation](https://www.ifrs.org/content/dam/ifrs/publications/pdf-standards/english/2024/issued/part-a/ifrs-15-revenue-from-contracts-with-customers.pdf?bypass=on). Referencia de diseño contable; no se afirma aquí que sea la norma local aplicable.

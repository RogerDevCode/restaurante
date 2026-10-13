# Pago multimoneda en factura: Plan de implementación

> **Para agentes de ejecución:** implementar paso a paso y validar cada bloque. La ejecución de este plan está autorizada inline.

**Objetivo:** Registrar uno o varios medios de pago en Bs. y/o USD desde la pantalla de factura, sin diálogo modal y usando únicamente la tasa guardada en `pedidos.tasa_cambio`.

**Arquitectura:** `PagoPedido` valida cada tramo; `PedidosDao` verifica que la suma equivalente a Bs. cubra exactamente el total y guarda encabezado y detalle en una transacción. `Sistema` captura y presenta los tramos inline, muestra saldo/tasa y entrega los pagos al worker existente; cierre de caja y PDF consumen el desglose persistido.

**Tecnología:** Java 21, Swing, JDBC/MySQL, JUnit, Ant.

## Restricciones globales

- La SSOT de conversión de un pedido es `pedidos.tasa_cambio`; si falta o no es positiva, bloquear el cobro.
- El monto de cada fila debe ser positivo y tener como máximo dos decimales; la suma equivalente en Bs. debe igualar el total fiscal almacenado.
- Cada tramo conserva método, moneda, monto original, tasa y equivalentes Bs./USD.
- Cobro, cierre de pedido y detalle de pagos se confirman en una transacción; un error no deja pago parcial.
- La pantalla de factura no abre un diálogo modal para ingresar o confirmar el desglose.
- Conservar errores graves como diálogo; validaciones de fila/saldo se muestran inline.

---

## Archivos

- `src/Modelo/PagoPedido.java`: reglas de una línea de pago y conversión.
- `src/Modelo/PedidosRepositorio.java`, `src/Controlador/PedidosControlador.java`, `src/Servicio/ConsultaPedidosServicio.java`: API de finalización con lista de pagos.
- `src/Modelo/PedidosDao.java`: validación SSOT y persistencia atómica en `pedidos` y `pedido_pagos`.
- `src/Modelo/CierreCajaDao.java`: inclusión de efectivo Bs./USD que proviene de filas `MIXTO` y conversión de los medios.
- `src/Vista/Sistema.java`: captura inline, saldo pendiente, tasa y finalización sin diálogo.
- `src/Vista/FinalizarPedidoSwingWorker.java`: transportar el desglose sin cambiar el ciclo de impresión.
- `test/Modelo/PagoPedidoTest.java`, `test/integracion/MySqlIntegrationIT.java`, `test/Vista/PagoMultimonedaE2EVirtual.java`: conversión, transacción, conciliación y flujo de operador.

## Tareas

### 1. Validación y persistencia de pagos

- Añadir casos de `PagoPedido` para VES/USD, redondeo HALF_UP, restricciones de efectivo, montos inválidos y tasa no positiva.
- Añadir una ruta de repositorio/controlador que acepte `List<PagoPedido>` para todo cobro; una fila persiste su método y varias filas registran `MIXTO` en el encabezado.
- En DAO, leer `total_bs` y `tasa_cambio` del pedido pendiente; rechazar tasa ausente/inválida sin usar 36.5 ni configuración/UI.
- Exigir igualdad exacta con `total_bs`; persistir todas las filas y los acumulados de efectivo de ambas monedas en la misma transacción.
- Mantener las rutas legadas de finalización para callers anteriores; la factura nueva usa siempre la ruta detallada.

### 2. Conciliación de caja y factura

- Corregir el agregado de `CierreCajaDao` para incluir los campos de efectivo Bs. y USD de pedidos `MIXTO` sin contar transferencias/tarjetas como efectivo.
- Comprobar que la carga y el PDF muestran método, moneda y monto de cada línea y sus equivalentes según la tasa guardada.

### 3. Captura inline en Swing

- Sustituir la captura modal de `MIXTO` por una tabla compacta dentro de la factura con columnas método, moneda y monto, y acciones agregar/quitar fila.
- Precargar una fila `EFECTIVO_BS`/`VES` por el total en Bs.; actualizar equivalente pagado y saldo en ambas monedas al editar.
- Antes de ejecutar el worker, validar las filas, resaltar la fila inválida o faltante y pasar la lista completa; el botón finaliza solo si el saldo es cero exacto.
- Mantener la restauración de estado, navegación a selección de sala e impresión/PDF del worker actual.

### 4. Verificación

- Ejecutar pruebas unitarias del modelo y la prueba E2E virtual con teclado/mouse.
- Ejecutar pruebas MySQL solo en `restaurante_test` aislada para VES, USD, Bs.+USD, varias formas, faltante, exceso, tasa inválida, atomicidad y conciliación.
- Confirmar que no se abre `JDialog` durante el flujo de pago y que errores graves conservan su diálogo.


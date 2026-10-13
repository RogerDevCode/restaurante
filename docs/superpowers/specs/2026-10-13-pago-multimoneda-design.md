# Cobro en Bs. y USD integrado en factura

## Problema

La pantalla de facturación tiene una selección rápida de forma de pago, pero la opción `MIXTO` abre un diálogo modal para capturar varios medios. Esto añade pasos al cierre de una mesa y no permite completar el desglose desde la pantalla de factura.

## Diseño aprobado

El desglose de pagos se integra en la misma pantalla de factura. El operador puede registrar una o varias filas con medio de pago, moneda (Bs./VES o USD) y monto. Se permite combinar monedas en una sola factura, por ejemplo, una parte en Bs. y el saldo en USD, además de combinar medios de pago.

La pantalla muestra el total fiscal en ambas monedas, la tasa aplicada, el equivalente acumulado de los pagos y el saldo pendiente. El operador puede agregar/quitar filas y finalizar directamente cuando el monto cubra exactamente el total; no hay diálogo modal para completar el pago. Errores de validación se muestran junto al desglose y los errores graves mantienen el diálogo de error existente.

Para conservar el flujo rápido, la primera fila se precarga con efectivo en Bs. y el total equivalente en Bs. El operador puede cambiar el medio o la moneda antes de facturar. Métodos de pago no efectivo admiten ambas monedas; efectivo Bs. solo admite VES y efectivo USD solo admite USD.

## Fuente única de la tasa

La tasa usada durante el cobro es la persistida en `pedidos.tasa_cambio` para ese pedido, que conserva la tasa con la que se calcularon sus totales. No se usará una tasa de UI, servicio externo, valor por defecto ni constante de reemplazo. Si falta la tasa o no es positiva, se bloqueará el cobro y se mostrará una validación de error. Los importes convertidos se calculan con dos decimales y redondeo HALF_UP.

## Persistencia y validación

- Validar cada fila (método, moneda, monto positivo con máximo dos decimales) antes de finalizar.
- Sumar los importes convertidos a Bs. y exigir igualdad exacta con el total en Bs. del pedido, incluyendo impuestos ya calculados.
- Persistir el detalle de todos los medios/monedas en `pedido_pagos`, también cuando solo hay una fila.
- Mantener el campo legado `pedidos.metodo_pago` como el método de la fila única cuando exista un pago; para múltiples filas, `MIXTO`.
- Persistir los acumulados de efectivo en Bs. y USD para conciliación de caja. Los pagos por transferencia, tarjeta o pago móvil no se contabilizan como efectivo.
- Guardar los importes originales, moneda, tasa, equivalente Bs. y equivalente USD en cada fila de pago.
- Ejecutar la finalización del pedido y el guardado de pagos en una sola transacción; ante error no cerrar la mesa ni dejar pagos parciales.

## Límites

El trabajo se limita al flujo de captura y persistencia del cobro, su presentación en factura y la cobertura correspondiente. No cambia la configuración de tasa, la política tributaria, ni la lógica de impresión/reintento del PDF. No se agregan confirmaciones de rutina.

## Criterios de aceptación

1. Un cobro completo en Bs. finaliza desde la pantalla de factura sin diálogo adicional.
2. Un cobro completo en USD se convierte con la tasa de `pedidos.tasa_cambio` y concilia exactamente con el total.
3. Un cobro combinado Bs.+USD finaliza al cubrir exactamente el total y queda íntegro en el detalle de pagos.
4. Varias formas de pago pueden combinarse; pagos incompletos o excedidos no finalizan.
5. La tasa ausente o inválida bloquea el cobro sin usar valores alternativos.
6. Tras facturar y cerrar mesa, se mantiene el retorno a selección de sala y el flujo normal de impresión/PDF.

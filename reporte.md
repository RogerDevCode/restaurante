# Estado actual — Impresión de tickets

**Revalidado:** 2026-10-19 · **Plan de seguimiento:** `plan-produccion.md`, Fases 3, 4 y 7.

Este documento reemplaza el diagnóstico de la iteración 4, que conservaba advertencias de compilación ya reparadas y veredictos anteriores a los cambios de impresión. El análisis original se conserva en `reporte-impresion-historico.md` como referencia histórica; sus hallazgos no describen el estado actual.

## Estado verificado

- El resultado de impresión ahora distingue `ENVIADO_A_SPOOLER`, `ABIERTO_EN_VISOR` y `FALLO` mediante `ResultadoSalidaTicket`; la UI comunica el canal observado sin prometer que haya salido papel.
- El resultado tipado viaja por `ServicioImpresionTicket`, `PedidoPdfServicio`, `PedidosControlador`, `FinalizarPedidoSwingWorker` y el flujo de Cierre de Caja.
- Un fallo de impresión después de confirmar el cobro no revierte ni presenta el cobro como fallido; permite reimprimir desde Historial.
- Las reimpresiones de la UI se ejecutan fuera del EDT y muestran cursor de espera.
- `ACCION_IMPRESION` documenta `IMPRIMIR`, `VISOR` y `AUTO` en `.env.example`.
- `ant -q compile jar`, `ant -q compile-test` y pruebas focalizadas de impresión finalizaron correctamente.
- `./e2e_dia_completo_virtual.sh` terminó `E2E_OK`; el cierre Z fue solicitado por UI y la banda confirmó el envío al spooler `lp` simulado.

## Pendiente antes de aceptar producción

- Validar en Windows y Linux la impresora, visor y spooler configurados en instalaciones de prueba; la ejecución E2E usa `lp` simulado y no demuestra salida física.
- Verificar respuesta ante impresora apagada, sin papel, cola bloqueada y timeout de PowerShell/Java Print Service.
- Completar la política de retención y limpieza de PDFs temporales sin eliminar facturas ni comprobantes requeridos por auditoría.
- Repetir la aceptación de impresión como parte de una instalación limpia de release.

## Evidencia de la última revalidación

- E2E de jornada completa en MySQL efímero, Xvfb `:99` y spooler simulado: **pasó**. La primera ejecución reveló que la aserción esperaba el texto obsoleto “impresora térmica”; se actualizó a “spooler” y la repetición terminó `E2E_OK`.
- ZIP Linux del release: generado e inspeccionado; no contiene `.env`, claves privadas, volcados SQL comprimidos ni logs.
- Prueba con ticketera física y validación de canales en Windows: **pendientes**.

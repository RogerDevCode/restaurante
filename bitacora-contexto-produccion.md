# Bitácora de continuidad — preparación para producción

snapshot 19/10/2026 16:08

## Objetivo vigente

Cerrar con evidencia reproducible los bloqueos comprobables de `plan-produccion.md`: ejecutar validaciones aisladas, corregir hallazgos confirmados, mantener sincronizado el plan y distinguir los gates locales de los que requieren hardware o entorno externo. El plan es la fuente de verdad de fases y pendientes; esta bitácora guarda contexto para continuar el trabajo, decisiones verificables y evidencia, no razonamiento interno privado.

## Reglas estables

Las reglas permanentes de seguridad, operación, pruebas E2E, DI y formato de evidencia están en `AGENTS.md`. Consultarlo antes de ejecutar pruebas o modificar flujos operativos.

## Estado de continuidad

- Rama actual: `feature/persistencia-columnas-jtable`.
- La instancia operativa del puerto 49200 sigue activa; no reiniciarla ni desplegar encima durante este trabajo.
- Hay cambios de código, pruebas, runner, plan y documentación sin commit. Revisar `git status` y el diff completo antes de cerrar.
- `e2e_bootstrap_virtual.sh` crea y elimina su propio MySQL efímero. Su modo completo (`E2E_BOOTSTRAP_FULL_DAY=SI`) prueba bootstrap limpio y jornada fiscal con usuario de runtime limitado. Usa Xvfb `:99` y `ACCION_IMPRESION=IMPRIMIR` con `lp` simulado.
- La causa observada de una falla previa del historial fue `ACCION_IMPRESION=VISOR`: el visor PDF externo cubrió la ventana de Restaurante. Se cambió a impresión simulada para que el Robot controle la UI. La repetición corregida con log persistente terminó con código 0 y `E2E_OK`: ocho facturas finalizadas, una cuenta anulada, cero pendientes, Bs 5783.60 conciliados y cierre Z aceptado por spooler simulado. No atribuir el fallo previo a permisos ni a la navegación del producto.

## Evidencia de esta preparación

Formato obligatorio por evidencia: **comando → archivo de salida → PASS/FLAKY/FAIL**. El estado se limita a lo que demuestra esa ejecución.

| Comando | Archivo de salida | Estado y alcance |
|---|---|---|
| `ant -q compile jar && ant -q compile-test` | `evidencias/produccion/2026-10-19/admin-lock-compilacion.log` | **PASS** — compilación de aplicación y tests. |
| `E2E_BOOTSTRAP_FULL_DAY=SI ./e2e_bootstrap_virtual.sh` con `ACCION_IMPRESION=VISOR` (ejecución diagnóstica previa) | `/tmp/restaurante-e2e-historial-419731.png` | **FLAKY** — la ventana externa del visor tapó la UI; el runner ya cambió a spooler simulado. No es una falla reproducida de permisos ni de navegación. |
| `ant -q integration-test` | `evidencias/produccion/2026-10-19/integration-test.log` | **PASS** — 12 suites, 65 tests, 0 fallos, 0 errores, 1 omitido. |
| `ant -q integration-test` (regresión lock compartido) | `evidencias/produccion/2026-10-19/admin-lock-integration.log` | **PASS** — 12 suites, 66 tests, 0 fallos, 0 errores, 1 omitido; test entre conexiones confirma espera del advisory lock. |
| `ant -q test-single` (primera invocación, detenida por el guard de BD) | `evidencias/produccion/2026-10-19/admin-lock-unit.log` | **FAIL** — el guard la bloqueó antes de ejecutar JUnit al detectar configuración heredada potencialmente productiva. |
| `ant -q test-single` (LoginDaoCicloVidaTest, entorno saneado y propiedades explícitas para `restaurante_test`) | `evidencias/produccion/2026-10-19/admin-lock-unit-isolated.log` | **PASS** — 13 tests, 0 fallos, 0 errores. |
| `ant -q test` (ejecución aislada anterior, propiedades de sistema para la BD) | `evidencias/produccion/2026-10-19/unit-test.log` | **PASS** — 657 tests, 0 fallos, 0 errores, 30 omitidos en 118 suites. |
| `ant -q integration-test` (antes del fix, regresión roja intencional) | `evidencias/produccion/2026-10-19/admin-lock-red.log` | **FAIL** — la prueba nueva demostró que la mutación no esperaba el lock externo; el defecto quedó corregido y la repetición posterior pasó. |
| `set -o pipefail && E2E_BOOTSTRAP_FULL_DAY=SI ./e2e_bootstrap_virtual.sh 2>&1 | tee evidencias/produccion/2026-10-19/bootstrap-full-day-min-privileges.log` | `evidencias/produccion/2026-10-19/bootstrap-full-day-min-privileges.log` | **PASS** — terminó con código 0 y `E2E_OK`; bootstrap y jornada UI con privilegios mínimos, ocho facturas finalizadas, una cuenta anulada, cero pendientes, total Bs 5783.60 conciliado y cierre Z aceptado por spooler simulado. No prueba papel físico. |
| `ant -q compile jar && ant -q compile-test` | `evidencias/produccion/2026-10-19/admin-lock-compilacion.log` | **PASS** — compilación de aplicación y pruebas; compilación de tests focalizada también terminó al correr JUnit. |
| `cmp -s actualizar_bd.sql db/migrations/actualizar_bd.sql` | `evidencias/produccion/2026-10-19/integridad-final.log` | **PASS** — scripts de migración idénticos byte a byte. |
| `git diff --check` | `evidencias/produccion/2026-10-19/integridad-final.log` | **PASS** — sin errores de whitespace en el diff. |

## Pendientes de producción conocidos

Consultar `plan-produccion.md` para el desglose por fase. Permanecen gates no acreditados por el host actual: scripts PowerShell y ACL en Windows, restauración y conciliación en una BD secundaria, aceptación con impresora/driver y papel físicos, regeneración e inspección de los artefactos de release del árbol actual, y auditoría completa de permisos/revocación de sesiones. El lock MySQL de mutaciones de Administradores ya quedó implementado y verificado por integración. No declarar el sistema listo para producción mientras falten esos gates.

## Próximo paso

1. Mantener `plan-produccion.md` como fuente de verdad y completar los gates externos: Windows/ACL, restauración secundaria, impresora física y artefactos Linux del árbol final.
2. Auditar autorización de todas las acciones sensibles y revocación de sesión en caliente.
3. Revisar todas las modificaciones y los gates pendientes sin detener la instancia operativa.

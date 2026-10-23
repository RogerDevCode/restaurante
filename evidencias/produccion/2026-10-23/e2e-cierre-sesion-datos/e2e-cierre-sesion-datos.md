# E2E Cierre de sesión sin pérdida de datos + salida del programa (BD = SSOT)

Clase (local, `test/` desrastreado): `test/Vista/CierreSesionDatosE2EVirtual.java` — 100% interfaz (Robot real, Xvfb :99, BD desechable `restaurante_test` 3307, `restaurante_test_app` mínimos). Dos ventanas de `Sistema` simultáneas en la misma JVM (admin + mesonero).

## Flujo verificado (ambas corridas idénticas)

1. Inicio de sesión **admin**. Alta de mesonero (`MesoneroRev<sello>`) por la pestaña Usuarios.
2. Creación de sala fresca `Sala <sello>` ×2 mesas por la interfaz (pantalla Gestión de Salas).
3. Segundo inicio de sesión **mesonero** (segunda ventana de Sistema, misma JVM); pestañas por rol visibles.
4. Mesa libre → toma de pedido: ronda #1 enviada a cocina (**cuenta #N** persistida en `PENDIENTE`) + cantidad extra añadida al carrito y **NO enviada** (borrador solo en memoria).
5. Admin desactiva el mesonero desde la interfaz (usuarios → Desactivar).
6. La sesión B (60 s de vigilancia, Timer) se revoca al vuelo: diálogo "Acceso revocado" + dispose + reabre login, sin reiniciar la JVM.
7. Reintento de login con la cuenta revocada → diálogo "Acceso denegado" + permanece en login.
8. Re-login admin y verifico la cuenta abierta: el mismo folio + 1 línea exacta (la línea del borrador nunca tocó la BD).
9. Salida real por la interfaz: X en la ventana → "¡ADVERTENCIA: Mesas abiertas!" → *Sí* → `System.exit(0)` interno (hooks de bajada completan).
10. **Post-mortem** (proceso ya muerto): consulta SQL directa en el contenedor: la cuenta sigue `PENDIENTE` en la BD con su línea exacta ⇒ BD = SSOT; nada el burrador.

## Evidencia

| Comando | Archivo | Resultado |
|---|---|---|
| `timeout 700 ./e2e_virtual.sh Vista.CierreSesionDatosE2EVirtual` (pipeline manual: `docker compose -p e2ecierre -f docker-compose.e2e.yml up -d --wait` + `./inicializar_bd_test.sh` + bootstrap + seed) | `corrida1.log`: las 7 fases `OK`, `E2E_OK`, `SHUTDOWN_HOOKS_SLICE`, `EXIT=0` | PASS |
| misma corrida ×2 | `corrida2.log`: idéntico, `E2E_OK`, exit 0 | PASS |
| `docker exec e2ecierre-db-1 mysql … -e "SELECT id,estado,total FROM pedidos WHERE id=10"` (tras el exit real de la corrida 1) | `id=10 PENDIENTE 5.00`; `SELECT COUNT(*)…detalle_pedidos → 1`; línea `E2ESEED-POLLO ×1 @ 5.00` | PASS (SSOT) |
| misma consulta para la corrida 2 | `id=11 PENDIENTE 5.00`, 1 línea | PASS (SSOT) |
| `SELECT nombre,rol,activo FROM usuarios … MesoneroRev1792781815169` (post-mortem corrida 1) | `activo=0` (desactivación persistida) | PASS |

## Notas de diseño del test (flakiness suprimido)

- La tira de salas desborda su `JScrollPane` al acumular corridas → el clic al pie del scroll cae fuera de la ventana (diagnóstico `bajoElPunto=null`). El test toma la mesa por la **ruta por código** de la app (cabecera TOMA DE PEDIDO: `txtCodigoMesaDirecto` + Enter → `procesarDigitacionMesaDirecta`): se lee el prefijo de la sala en la tira (lectura, no clic) y se teclea `prefijo-N`.
- `mostrarSalaEnMapa` ya no clica botones de sala (solo garantiza que la tira esté cargada); se elimina la carrera del mapa asíncrono (`PanelMesasSwingWorker`).
- La revocación aserta por el diálogo (Timer de 60 s) y no por foco de ventana (no fiable en Xvfb sin gestor de ventanas).
- La X de salida se despacha en un hilo propio (el manejador `windowClosing` bloquea en el diálogo modal); salvavidas `Timer` de 8 s fuerza la salida si la propia la invoca; el eco `SHUTDOWN_HOOKS_SLICE` demuestra que el cierre completó los hooks de bajada.

Pipelines: bootstrap `Vista.BootstrapInicialE2EVirtual` (OK a la 1ª) y seed `Vista.SeedBaseE2EVirtual` (OK), con cuentas `E2E_LOGIN_USERNAME/PASSWORD`.

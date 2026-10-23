# Auditoría de permisos / sesión en caliente (gate accreditado)

snapshot 23/10/2026 07:35

Alcance: matriz `PoliticaAcceso.Accion` (19 acciones) → puntos de enforcement reales; mecanismo de revocación de sesión en caliente (BD como fuente de verdad); 2 tests nuevos de revocación ejecutados contra BD desechable 3307 (proyecto compose `db-audit`, fixture `BD-test.sql --datos`).

## 1. Mapa Accion → puntos de control

Enforcement doble: **presunto por servicios/controladores** (`exigir(...)` / `exigirPermiso(...)`, `PoliticaAcceso.permite`) y **por la UI** (`autorizar(...)` en `Sistema`, `politicaAcceso.permite` para habilitar componentes). Ninguna acción stub quedó sin control:

| Accion | Servicios/Controlador (exigir) | UI (autorizar/permite) |
|---|---|---|
| CONSULTAR_SALAS | 5 (SalasServicio) | 1 |
| CONSULTAR_OCUPACION_MESAS | 1 (ConsultaPedidosServicio:34) | 4 |
| GESTIONAR_SALAS | 3 (SalasServicio) | 3 autorizar |
| CONSULTAR_PLATOS | 8 (Platos/Categoria/Favorito) | 1 |
| GESTIONAR_PLATOS | 14 | 12 autorizar |
| REGISTRAR_PEDIDOS | 4 (PedidoServicio + PedidosControlador) | 1 |
| CONSULTAR_PEDIDOS_PENDIENTES | 3 (ConsultaPedidosServicio) | 2 |
| EDITAR_PEDIDOS_ABIERTOS | 4 (PedidoServicio + PedidosControlador) | 3 |
| CANCELAR_CUENTA_PENDIENTE | — | autorizar Sistema:4340 + Panel:1030 + selector 9166 |
| APLICAR_CORTESIA | — | autorizar Sistema:4396 + Panel:1028 |
| COBRAR_PEDIDOS | 11 (ConsultaPedidosServicio/PedidoServicio) | 1 |
| CONSULTAR_HISTORIAL | 15 (ConsultaPedidosServicio) | 8 |
| ANULAR_FACTURA_COBRADA | 2 (ConsultaPedidosServicio:143) | 2 (habilitar UI 475/9165) |
| GESTIONAR_CIERRE_CAJA | 2 (CierreCajaServicio) | 3 (8110 + 1102/1103) |
| CONGELAR_LINEA_PEDIDO | 1 (PedidosControlador:284) | — (soloAdministrador) |
| GESTIONAR_USUARIOS | checks en ConfiguracionAccesosServicio | 5 autorizar |
| GESTIONAR_ROLES_ACCESOS | ConfiguracionAccesosServicio:51 | tab 868/1069/2372/2397 |
| EDITAR_CONFIGURACION | — | 11 autorizar |
| PURGAR_HISTORIAL | 2 (ConsultaPedidosServicio:183) | 2 (1104) |

` PoliticaAcceso.permite()` además impone: (1) proveedor de sesión activo **consultado en cada llamada**; (2) bypass solo para `Administrador`; (3) acciones `soloAdministrador` cortesía/anulación/cierre/congelar/usuarios/roles/config/purgar no delegables. Matriz vacía ⇒ deny-all por defecto (observado corriendo sobre BD sin seed: WARN "se deniegan permisos por seguridad").

## 2. Revocación de sesión en caliente (BD = fuente de verdad)

- `Restaurante.lanzarLogin` (src/restaurante/Restaurante.java:196-206): una sola `PoliticaAcceso` por sesión cuyo `sesionActivaProveedor` es `loginDao.esUsuarioActivoConRol(id, rol)` en cada decisión; **fail-closed** ante error de conexión (WARN + deny).
- La misma instancia fluye a `Sistema`, `PedidosControlador` y todos los servicios (Restaurante.java:226-234) → desactivar el usuario en BD revoca inmediatamente la siguiente acción ejecutable, sin reiniciar nada.
- Vigilancia proactiva: `Sistema.iniciarVigilanciaSesion` (2141-2158) — listener `windowGainedFocus` + `javax.swing.Timer` de 60 s con coalesce; al detectar usuario inactivo/rol cambiado: `dispose()` + `lanzarLogin` con diálogo "Acceso revocado" (2186-2197).
- Verificación por acción: `Sistema.autorizar` (2290-2305) re-consulta `esUsuarioActivoConRol` antes de conceder; BD caída ⇒ acción denegada por seguridad (2439-2444).
- `esUsuarioActivoConRol` (src/Modelo/LoginDao.java:554) valida activo **y rol** ⇒ cambio de rol también revoca.

## 3. Evidencia ejecutada (BD desechable 3307)

Nuevo test `test/Servicio/PoliticaAccesoRevocacionVigorTest.java` (local, `test/` desrastreado) — 2 casos:

| Test | Qué demuestra | Resultado |
|---|---|---|
| `desactivarUsuarioEnBDRevocaLaSesionSinRecrearLaPolitica` | Usuario Asistente real registrado en BD; política replicando el ensamblaje de `lanzarLogin`; `permite(base)=true` (matriz seed); `desactivarUsuario` por BD ⇒ la MISMA instancia deniega `PoliticaAcceso.Accion.values()` completo y `esAdministrador()=false`; `reactivarUsuario` ⇒ vuelve a permitir. Cleanup `eliminarUsuario` en `finally`. | PASS |
| `administradorConUsuarioAusenteEnBDSeQuedaSinAcceso` | Política de Administrador con id inexistente (9999999) y proveedor BD: el bypass de admin **no** se aplica con proveedor falso ⇒ deny-all. | PASS |

`java -cp "build/classes:build/test/classes:librerias/*" ... org.junit.runner.JUnitCore Servicio.PoliticaAccesoRevocacionVigorTest` → `OK (2 tests)` (0.87 s; primera corrida sobre esquema vacío dejó la matriz de permisos vacía, denegatoria por defecto; segunda corrida con `./inicializar_bd_test.sh --datos` ejerció el ciclo permitir→revocar→rehabilitar completo).

Los 2 tests quedan dentro del patrón `**/Servicio/*Test` de la suite `ant test` (próxima corrida con overrides los suma al total).

## 4. Hallazgos / riesgos residuales

1. `CANCELAR_CUENTA_PENDIENTE`, `APLICAR_CORTESIA` y `EDITAR_CONFIGURACION` se controlan solo en la UI (sin `exigir` en servicio) — el "segundo cinturón" depende de que ninguna otra pieza de código llame directamente al DAO sin `autorizar`. Aceptable con la topología actual; queda anotado.
2. El proveedor consulta BD por cada `permite()` (servicios incluidos): carga extra de BD por clic, mitigada por índice PK y por el timer coalesced de 60 s.
3. Replica/sesiones ya abiertas con BD caída en el instante del switch: `esUsuarioActivoConRol` no genera excepción persistente — el timer reintenta el siguiente tick (FAIL-OPEN explícito SOLO para la vigilancia pasiva, con log FINE, y FAIL-CLOSED para acciones; el diálogo no llega si nunca recupera foco → riesgo residual menor documentado).
4. `verificarVigenciaSesion` ante error de BD solo "reintentará" (sin revocar): correcto (no mata la sesión por latido perdido), el gate de seguridad fuerte está en `autorizar`.

Cierre del gate: **ACREDITADO** (mapa completo + mecanismo probado en caliente contra BD real desechable con 2 tests verdes).

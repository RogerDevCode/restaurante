# Campaña QA Red Team 2026-10-21 — Búsqueda de bugs (informe de hallazgos, read-only)

> Archivo canónico: `plans/qa.md`. El fichero generado en `.kilo/plans/` es solo un espejo.

## Revisión crítica del plan (21/10/2026, posterior a la redacción)

Una revisión de escritorio y de entorno detectó 11 puntos que habrían hecho fallar la campaña en ejecución. Todos están corregidos en este documento; se conservan aquí para trazabilidad.

| # | Severidad | Defecto detectado | Corrección aplicada |
|---|---|---|---|
| C1 | Crítico | La secuencia de comandos era imposible: el compose de la Fase 0 deja 3307 ocupado y el wrapper E2E **aborta si 3307 está ocupado** | T0.3 se baja el compose antes de la integración y del bucle E2E; el bloque de comandos refleja el orden real |
| C2 | Crítico | `Restaurante.main` solo puede invocarse **una vez por JVM**: el guard de instancia única (puerto 49200) hace `System.exit(0)` y mata el test | La matriz se siembra con `--migrate-db` en un **proceso separado**; `main` en proceso solo en la corrida confirmatoria, con guardarraíles (T3R.0) |
| C3 | Crítico | El re-login por rol no estaba documentado: no existe "logout" de usuario | Se documenta la vía real del producto: `sistema.dispose()` + `Restaurante.lanzarLogin()` (T3R.2) |
| C4 | Alto | `FrmLogin` mata la JVM con ESC / X / botón "Salir" | Guardarraíles explícitos del arnés (T2.1) |
| C5 | Alto | La guarda anti-falso-positivo eximía a R5 de los viewports pero no a R3, y hay contenedores con suelos `Math.max(...)` que legítimamente exceden al padre | Guardas ampliadas a R3/R4/R8 y comparación contra el `preferredSize` declarado del padre (T2.3) |
| C6 | Alto | `DISPLAY=:98` no existe en la máquina (solo hay `:0`) | T0.2 levanta un Xvfb propio en `:98` y lo baja al final |
| C7 | Medio | Los anchos de tabla se persisten en **BD** (`preferencias_tabla`, clave `usuario_id`+`tabla_clave`), no en `user.home`: la corrida `real` y la `harness` se contaminan entre sí | El barredor **no redimensiona columnas**; la corrida confirmatoria (`real`) usa usuarios distintos y limpia `preferencias_tabla` (T2.3, T3R.2b) |
| C8 | Medio | El host no tiene `mysql` ni `mysqldump` | Toda comprobación por BD se hace con `docker exec <contenedor> mysql ...`; queda dicho en T0.2 y en T0.7 |
| C9 | Medio | `Restaurante.main` escribe `respaldos/` y `logs/` relativos al **CWD**, no a `user.home` | Se documenta el aislamiento de ficheros en T2.1 y en el bloque de comandos |
| C10 | Bajo | 5 intentos de login fallidos bloquean la cuenta 30 s | Se prohíbe el reintento automático de login en el arnés (T2.1) |
| C11 | Crítico | El compose de la campaña dejaba 3307 ocupado y las corridas del barredor usaban el wrapper del día, que **aborta con el puerto ocupado** y además **siempre crea una BD virgin** ⇒ la matriz sembrada nunca llegaba a la medición | El bloque de comandos se reordena en tres corridas con tres BDs (T2.7): artefacto, confirmatoria por vía real y medición contra la BD de campaña con `e2e_virtual.sh` directo |

## Objetivo y decisiones del operador (no reabrir)

Auditoría de calidad **exigente** del Restaurante 2026, orientada a **encontrar y documentar** defectos reales, no a corregirlos. Seis ejes: (1) llamadas a BD y **transacciones incompletas**, (2) **interfaz con pantalla virtual** propia (mouse/teclado virtuales), (3) **lógica de negocio**, (4) **componentes no visibles / texto cortado / componentes recortados**, (5) **acceso por rol (RBAC) y escalada de privilegios en la UI**, (6) regresión de las suites existentes.

Decisiones fijadas:
- Entregable = **solo informe de hallazgos**. **No se modifica `src/`.** Las correcciones se triagean aparte.
- **Sí** instrumentación nueva de prueba, en `test/` (ignorado por git: `.gitignore:44-46`; `git ls-files test/` vacío → **nunca se commitea**).
- Barrido en **dos pasadas**: primero dinero, luego el resto.
- **Sí** al eje RBAC multi-rol.
- **RBAC (decidido 21/10/2026)**: la matriz `seguridad.rol.*` se siembra en la BD desechable por la **vía real del producto** (`Restaurante --migrate-db` → `asegurarMatrizPredeterminada`, en un **proceso separado** del arnés), y se hace **1 corrida confirmatoria** con `Restaurante.main` in-process para atribuir los hallazgos al producto y no al arnés E2E. Nada de esto toca la BD de servicio ni la app en producción.

## Hechos verificados contra el código (con ruta:línea)

| Hecho | Evidencia |
|---|---|
| 20 acciones; **8 `soloAdministrador`**: `APLICAR_CORTESIA`, `ANULAR_FACTURA_COBRADA`, `GESTIONAR_CIERRE_CAJA`, `CONGELAR_LINEA_PEDIDO`, `GESTIONAR_USUARIOS`, `GESTIONAR_ROLES_ACCESOS`, `EDITAR_CONFIGURACION`, `PURGAR_HISTORIAL` | `src/Servicio/PoliticaAcceso.java:12-31` |
| `permite()`: Administrador → siempre `true`; resto → `!soloAdministrador && contiene(permiso)` | `PoliticaAcceso.java:86-92` |
| 7 roles; clave de rol = minúsculas (`clave()`) | `src/Servicio/RolesSistema.java:8-17,29-34` |
| **Los permisos efectivos se leen de `configuracion_sistema` (`seguridad.rol.<rol>.<accion>`)**, no del código. Si **no existe ninguna clave** → conjunto **vacío** (fail-secure) + WARNING | `src/Servicio/ConfiguracionAccesosServicio.java:27-46` |
| La matriz se siembra **solo si no existe**; los defaults por rol son solo la semilla | `ConfiguracionAccesosServicio.java:82-140` |
| La siembra ocurre en `Restaurante.main` (`:170`), en `--migrate-db` (`:153`) y en el bootstrap (`:115`) — **NO** en `Restaurante.lanzarLogin()` (`:194-210`), que es la vía que usa el arnés E2E | `src/restaurante/Restaurante.java:153,170,194-210`; `src/Servicio/ServicioBootstrapInicial.java:115` |
| `BD.sql` y `db/integration/*.sql` **no** siembran la matriz (0 coincidencias de `seguridad.rol`) | verificado con grep en los 3 ficheros |
| Mapa pestaña→acción; **`habilitarTab` habilita TODAS las pestañas con ese título** (⇒ el título duplicado "Platos" habilita menú **y** administración) | `src/Vista/Sistema.java:1046-1062,1116-1121` |
| Los controles mutativos de platos sí están **gated individualmente** por `GESTIONAR_PLATOS` | `Sistema.java:1084-1090` |
| El auto-selección del Dashboard está correctamente condicionada a `esAdministrador()` (**no** hay escalada por ahí) | `Sistema.java:929-936` |
| `aplicarTitulosTabsContraste` usa `tabComponent`, **no altera** `getTitleAt` | `Sistema.java:1022-1043` |
| 12 pestañas (orden real): `0 Panel` (2817) · `1 Salas` (2946) · `2 Mesas` (2968) · `3 Platos`=menú (3397) · `4 Finalizar Pedido` (3502) · `5 Historial Pedidos` (3549) · `6 Datos de la Empresa` (3732) · `7 Usuarios` (3937) · `8 Platos`=administración (4094) · `9 Dashboard` (7126) · `10 Atención Directa (Split)` (10924, **condicional**) · `11 Categorías` (11707) | `Sistema.java` (`addTab`) |
| 5 subpestañas de configuración: Impresión y Tickets, Logo e Ícono, Base de Datos y Respaldos, Mesoneros, Roles y accesos | `Sistema.java:863-867` |
| API real de `DiaE2ERobot`: `campo(62)`, `esperarVentana(70)`, `clic(81)`, `clicCelda(103)`, `seleccionarPestaña(int)(139)/(String)(151)`, `reemplazarTexto(253)`, `esperar(285)`, `texto(456)`, `esperarDialogo(427)`, `recolectarComponentes` estático (499). **No hay helper de captura**: la única captura usa `E2E_DIAG_SCREENSHOT` (388-395) | `test/Vista/DiaE2ERobot.java` |
| Patrón de arranque E2E: guard `e2e.permitir`, guard de BD (`restaurante_test`), `user.home`→`E2E_WORKDIR`, `modo.automatizado=true`, `Restaurante.lanzarLogin()`, login `admin`/`admin`, `MAXIMIZED_BOTH` | `test/Vista/UsuariosCrudE2E.java:40-78` |
| Credenciales por env con default `admin`/`admin` (patrón ya establecido) | `test/Vista/DiaCompletoFiscalE2EVirtual.java:52-54` |
| `e2e_virtual.sh` exige `E2E_BD_DESECHABLE=SI` + `DB_NAME/DB_USER/DB_URL` a 3307; toma lock `flock` de `:99` y **rechaza si :99 ya está vivo**; compila con `ant -q compile-test`; **hace `cd` a `E2E_WORKDIR` (se borra al salir)** | `e2e_virtual.sh:27-42,63-96` |
| `e2e_dia_completo_virtual.sh` **rechaza si 3307 está ocupado**; compose `-p` único por corrida; `TZ=UTC`; `ACCION_IMPRESION=IMPRIMIR`; `lp` falso; clase por argumento (default `Vista.DiaCompletoFiscalE2EVirtual`) | `e2e_dia_completo_virtual.sh:12-53` |
| `e2e_bootstrap_virtual.sh` **cambia la clave del admin** a `E2EBootstrap2026X` tras el bootstrap y luego corre 3 E2E **en la misma BD** (stack compartido → contaminación H4) | `e2e_bootstrap_virtual.sh:93-102` |
| Guard `-unit-db-safety`: **aborta** si `DB_URL`/`DB_NAME`/`MYSQL_DATABASE` apuntan a `restaurante`; `restaurante_test` en 3307 es el destino permitido; hay que neutralizar `.env` con `-DDB_URL/-DDB_NAME/-DMYSQL_DATABASE` (la CLI gana) y las sysprops del JVM entran como `-Dtest-sys-prop.*` | `build.xml:21-39`; `nbproject/build-impl.xml:519-521,547-549` |
| `ant test` usa `testincludes="**/*Test.java"` (**no** requiere `test.includes`); JUnit con `haltonfailure` por defecto ⇒ el suite se detiene en el primer fallo ⇒ el recuento real se extrae de `build/test/results/**/*.xml`. `integration-test` corre `integracion/*IT.class` con compose `-p` y `<fail>` si falla | `nbproject/build-impl.xml:1641-1649`; `build.xml:43-48,122-129` |
| Precedencia de configuración: **system property > variable de entorno > `.env`** | `src/infraestructura/ProveedorConexionJdbc.java:552-560` |
| Cobertura previa existente (no duplicar): `test/integracion/` = 12 IT (entre ellos `CierreMesaYPagosCongeladosIT` con idempotencia de pago, pago mixto, anulación bloqueada sin compensación; `CombinatoriaEndToEndTotalIT` roles+tasas+IVA+concurrencia; `CombinatoriaUsuarioIT`; `SimulacionUsuarioIT`; `AdversarialIntegrationIT`); `test/aceptacion/` = 12 clases; `test/Vista/` = 52 clases | `ls test/*` |

### Correcciones aplicadas respecto al borrador anterior

1. El barredor **no** hereda lector de `E2E_LOGIN_*`: debe leerlos él (patrón de `DiaCompletoFiscalE2EVirtual:52-54`).
2. La escalada "pestaña Platos de administración" **baja a MEDIA**: los controles mutativos sí están gated por `GESTIONAR_PLATOS`; lo que se expone es la *pestaña* de administración a roles de consulta. Pasa a ALTA solo si se encuentra un control mutativo **no** gated.
3. La línea base unitaria **no** es "sobre BD desechable" por defecto: el guard exige neutralizar `.env` y apuntar a `restaurante_test` en 3307 con `-Dtest-sys-prop.*`; el comando canónico ya documentado es `DISPLAY=:98 ant -q test` con esas props.
4. **3307 es un recurso único**: `e2e_dia_completo_virtual.sh` aborta si el puerto está ocupado y `ant integration-test` lo ocupa ⇒ **todas las fases que tocan BD son estrictamente secuenciales**.
5. `E2E_WORKDIR` se borra al terminar el wrapper ⇒ **las capturas y logs del barredor deben ir a ruta absoluta** (`evidencias/produccion/2026-10-21/`), nunca relativa.
6. La matriz de permisos **no existe** en la BD recién sembrada y el arnés E2E no la siembra ⇒ sin el paso T3R.0 el eje RBAC mediría "todo deshabilitado" (artefacto, no defecto).
7. La lista base de E2E es de **12 clases UI con `main`** (no 9): las `*Test.java` con `main` son unitarias, no E2E.
8. **RBAC (decisión del operador, 21/10/2026)**: la matriz de permisos se siembra por la **vía real del producto** (`--migrate-db`, proceso separado) y la corrida confirmatoria se hace con `Restaurante.main` in-process, frente a la corrida normal por `lanzarLogin` (arnés), para separar defecto de producto de artefacto del arnés.

## Restricciones duras (no negociables)

1. **Cero escrituras en la BD de servicio** (`restaurante` en 3306). Todo en `restaurante_test` (3307).
2. Todo `docker compose` con `-p <proyecto>` explícito y `down --volumes` al terminar. Nunca sin `-p`.
3. **E2E UI = solo interfaz**: prohibido DAO/servicios/SQL en `test/Vista/`. La verificación de BD va en `test/integracion/` (donde JDBC sí está permitido).
4. **No tocar `src/`.** `test/` esInstrumentación sí, **nunca commiteada**.
5. Impresión en E2E: `ACCION_IMPRESION=IMPRIMIR` con el `lp` falso. **Nunca `VISOR`** (bloquea al Robot).
6. No reiniciar la app de servicio ni desplegar en horario de atención.
7. Secretos: nada real; temporales a `/tmp/opencode` o rutas ya ignoradas. Sanear credenciales de los logs antes de commitear.
8. Todo hallazgo necesita **evidencia reproducible** (log + PNG si es visual) y **pasos de reproducción**.

## Entregables

| # | Artefacto | Ruta | Trackeado |
|---|---|---|---|
| E1 | Informe de hallazgos | `qa-informe-campana-2026-10-21.md` (raíz) | Sí |
| E2 | Evidencia (logs + capturas) | `evidencias/produccion/2026-10-21/**` | Sí |
| E3 | Entrada de bitácora | sección `snapshot 21/10/2026 HH:MM` en `bitacora-contexto-produccion.md` | Sí |
| E4 | Barredor visual (reusable) | `test/Vista/BarredorVisualE2EVirtual.java` | No |
| E5 | IT de huecos transaccionales | `test/integracion/TransaccionAtomicidadIT.java` | No |
| E6 | Matriz RBAC + diff de inventario por rol | `evidencias/produccion/2026-10-21/rbac/*` | Sí |

**Fuera de alcance:** aplicar correcciones; tocar `src/`; modificar `fallos.md`; pruebas en Windows/tickera; cualquier escritura en la BD de servicio.

## Fases

### Fase 0 — Línea base y preparación

- **T0.1** Registrar `git rev-parse HEAD` y `git status --short` → SHA de referencia de todo hallazgo.
- **T0.2** Toolchain y entorno: `java -version` (21.x), `ant -version` (1.10.x), `which Xvfb xdotool flock docker ss` — **el host NO tiene `mysql` ni `mysqldump`**: toda comprobación contra una BD se hace con `docker exec <contenedor> mysql -u<user> -p<pw> <bd> -e "..."`. Estado real de la máquina: solo existe el display `:0` (1366×768, del operador); **no hay displays virtuales** ⇒ hay que levantar el propio. Antes de empezar: comprobar que 3307 está libre (`ss -ltn | grep 3307`), que 3306 (servicio) queda intacto y que el puerto 49200 (instancia única de la app) está libre.
- **T0.2b** Display virtual propio para la suite unitaria: `Xvfb :98 -screen 0 1366x711x24 &` + `sleep 1` (el display `:0` del operador **nunca** se toca: sin robo de foco ni de mouse) y `kill %1` al terminar la fase. Xvfb existe en el host; `xvfb-run` no se usa para no depender de él.
- **T0.3** Base de datos desechable de la Fase 0 (**proyecto compose explícito `qa-campana-20261021`, volumen propio**; el de la campaña del barredor es `qa-barredor-20261021`, ver T3R.0):
  `docker compose -f docker-compose.integration.yml -p qa-campana-20261021 up -d --wait` → queda sembrada desde `BD.sql` + `db/integration/002-restore-permissions.sql` + `003-seed-integration.sql` (usuario `admin`/`admin`, rol Administrador, `bootstrap_inicial_completado=1`).
  **El puerto 3307 queda ocupado mientras viva este proyecto** ⇒ `ant integration-test` y el wrapper E2E lo rechazan. Por eso el compose **se baja justo después de la línea base unitaria** (ver bloque de comandos): las fases que necesitan BD levantan la suya.
- **T0.4** Línea base unitaria (**comando canónico ya validado en la bitácora 2026-10-19**; display virtual propio `:98` levantado en T0.2b, nunca `:0.0`):
  ```
  DISPLAY=:98 ant -q test \
    -DDB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
    -DDB_NAME=restaurante_test -DMYSQL_DATABASE=restaurante_test \
    -Dtest-sys-prop.MYSQL_PORT=3307 -Dtest-sys-prop.MYSQL_DATABASE=restaurante_test \
    -Dtest-sys-prop.MYSQL_USER=restaurante_test_app -Dtest-sys-prop.MYSQL_PASSWORD=test_only_app_password \
    -Dtest-sys-prop.DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
    -Dtest-sys-prop.DB_USER=restaurante_test_app -Dtest-sys-prop.DB_PASSWORD=test_only_app_password \
    2>&1 | tee evidencias/produccion/2026-10-21/base-unit.log
  ```
  `test_only_app_password` es la credencial **local y desechable** del compose de integración (`docker-compose.integration.yml`), no un secreto real. Registrar **código de salida** (`set -o pipefail` + `${PIPESTATUS[0]}`) y el recuento desde `build/test/results/**/*.xml` (`tests/failures/errors`), porque `haltonfailure` corta en el primer fallo. Referencia histórica: 121 suites / 671 tests / 0 fallos.
- **T0.5** Línea base de integración (levanta y baja su propio compose `-p restaurante-integration` en 3307 ⇒ **requiere 3307 libre**: el compose de T0.3 debe estar bajado):
  `ant -q integration-test` → `base-integration.log` (referencia histórica: 12 suites / 66 tests / 0 fallos).
- **T0.6** Línea base E2E **aislada por clase** (obligatoria; corrige el defecto H4). Las **12 clases UI con `main`**:
  `Vista.E2EVirtual` · `Vista.PedidoRapidoE2EVirtual` · `Vista.DiaCompletoFiscalE2EVirtual` · `Vista.AtencionDirectaE2EVirtual` · `Vista.AtencionDirectaCompletoE2EVirtual` · `Vista.OpcionCSplitE2EVirtual` · `Vista.Platos70IvaMonedaE2EVirtual` · `Vista.RedimensionarColumnasJTableE2EVirtual` · `Vista.UsuariosCrudE2E` · `Vista.BootstrapInicialE2EVirtual` (⚠ requiere el flujo de bootstrap: se ejecuta aparte con `e2e_bootstrap_virtual.sh`, no con el wrapper diario) · `aceptacion.E2EF09Virtual` · `aceptacion.E2EGuardadoPlatosVirtual`.
  Para cada una: `./e2e_dia_completo_virtual.sh <FQCN>` (el wrapper sube y baja su propio compose + volumen ⇒ aislamiento real de datos y de proceso) → `evidencias/produccion/2026-10-21/base-e2e-<Clase>.log`.
  Clasificar: FAIL **aislado** = candidato a hallazgo de producto; FAIL **solo en stack compartido** (`e2e_bootstrap_virtual.sh` corre 3 clases en la misma BD) = hallazgo de proceso.
  **Presupuesto**: 11 levantamientos de MySQL (uno por clase E2E) + 1 de bootstrap + 3 del barredor + 1 de la Fase 0 + 1 de `ant integration-test`. Registrar en el log de fase el tiempo por corrida.
  Cada invocación del wrapper crea su propio proyecto compose (`restaurante-dia-e2e-$$-<epoch>`) y destruye el volumen al salir ⇒ **cada clase parte de una BD virgen**; el wrapper **aborta si 3307 está ocupado**, de modo que las corridas son estrictamente secuenciales y ninguna puede solaparse con T0.3/T0.5.
- **T0.7 Prueba de no-escritura en la BD de servicio (evidencia del punto 1 de "Restricciones duras")**: antes de la campaña, leer (solo lectura) un testigo del estado de la BD de servicio —p. ej. `SELECT COUNT(*) FROM pedidos`, `SELECT COUNT(*) FROM pedido_pagos`, `SELECT MAX(cerrada_en) FROM pedidos` y `SELECT COUNT(*) FROM cierres_caja`— vía `docker exec <contenedor-de-servicio> mysql -uadmin -padmin restaurante -e "..."`, y volcarlo a `evidencias/produccion/2026-10-21/servicio-antes.txt`. Repetir exactamente las mismas consultas al cierre de la campaña → `servicio-despues.txt`. **Si algún contador cambia, es una escritura no autorizada** y hay que abortar e informar. (El host no tiene CLI `mysql`: la orden va siempre por `docker exec`.)
- **Gate 0:** las tres suites con PASS/FAIL real; E2E con PASS/FAIL **por clase en aislamiento**; fallos preexistentes marcados como tales.

### Fase 1 — Eje A: llamadas a BD y transacciones incompletas

- **T1.1 (lectura estática)** Inventariar toda operación con ≥2 escrituras. Por cada una, con ruta:línea: `setAutoCommit(false)` **antes** de la primera escritura; `commit` **después** de la última; `rollback` en el `catch`; `finally` que restaura `autocommit=true`; y **todas** las escrituras con **la misma conexión**. La detección ya existe: `ProveedorConexionJdbc.devolverAlPool` hace `rollback` + `SEVERE` + lanza `SQLException` si la conexión vuelve con escrituras sin confirmar.
- **T1.2 (anti-patrones, grep dirigido)** `catch` que traga; `commit` intermedio; `Statement` en vez de `PreparedStatement`; `UPDATE`/`DELETE` sin `WHERE`; `SELECT`→`UPDATE` sin `FOR UPDATE`; **orden de bloqueo distinto entre dos flujos** (deadlock); `SELECT … LIMIT 1` sin `ORDER BY`; N+1.
- **T1.2b (heurística de escritura del pool)** `ControlMantenimientoBD.esEscritura` + el clasificador de la marca transaccional de `ProveedorConexionJdbc`: todo `INSERT/UPDATE/DELETE/MERGE/REPLACE/TRUNCATE/DDL/CALL` debe detectarse como escritura (F-62 quitó falsos positivos con `;` y `WITH`; comprobar que no reintrodujo falsos negativos) y la marca **no** debe resetearse antes del commit/rollback (F-63). Una escritura no detectada = conexión devuelta al pool con transacción abierta = **corrupción silenciosa**.
- **T1.3 (flujos de dinero, atómico o nada)** `PedidosDao.actualizarEstadoConCliente` (facturación: `pedidos` + `pedido_liquidaciones` + `pedido_pagos` + `clientes`), `PedidosDao.actualizarPedidoCompleto` (¿commit intermedio entre borrar y reinsertar detalles?), `anularPedidoConAuditoria` (contra-asiento en `pedido_saldos_movimientos` + reversión en caja), `CierreCajaDao.guardarCierre` (Cierre Z inmutable + `uq_cierres_caja_z_fecha`), `ActualizacionTasaDao.ejecutarTransaccion` (recálculo masivo + auditoría).
- **T1.3b (fecha y zona horaria — recurrencia)** En todo filtro temporal de dinero: usar `cerrada_en` (cierre) y no `fecha` (apertura) ni al revés (F-25/F-36); el corte de jornada debe usar la **hora local del restaurante**, no `CURRENT_DATE()` del servidor (F-31: `CURRENT_DATE()` en `asegurarCatalogoPlatosVigente` borraba el catálogo tras las 20:00 por desfase UTC/local). Verificar que `TZ=UTC` del contenedor de pruebas **no** altera los reportes de caja respecto de la TZ del operador.
- **T1.4 (IT de huecos — primero el mapa de cobertura)** Antes de escribir código, extraer de las 12 IT existentes qué ya está probado (idempotencia de pago, pago mixto, anulación sin compensación, concurrencia de cierre, roles+IVA+concurrencia). **Solo se implementan los huecos** en `test/integracion/TransaccionAtomicidadIT.java`:
  1. **Fallo a media facturación**: error inyectado tras la primera escritura (violación de FK/CHECK) ⇒ verificar que **no** queda `pedidos.FINALIZADO` sin cabecera de pagos, ni pagos huérfanos, ni `pedido_saldos_movimientos` descuadrado.
  2. **Cierre Z duplicado concurrente**: dos hilos con la misma fecha ⇒ **una sola** fila en `cierres_caja` (constraint + `FOR UPDATE`).
  3. **Invariantes de dinero** tras facturación: `Σ detalle == cabecera`, `base + exento == subtotal`, `iva == ROUND(base × pct, 2)`, `total == subtotal + iva`, 2 decimales exactos.
  4. **Anulación con reversión atómica**: el contra-asiento y la reversión de caja quedan en la **misma** transacción.
  (Sufijo `IT` ⇒ `ant integration-test` lo recoge: `<include name="integracion/*IT.class"/>`.)
- **T1.5** Buscar `SEVERE` de `ProveedorConexionJdbc` en los logs de la línea base y de la campaña ⇒ cada uno es candidato a transacción abandonada.
- **Gate 1:** lista de transacciones no atómicas con ruta:línea y escenario de fallo reproducible + resultado de la IT nueva (PASS/FAIL con su alcance real).

### Fase 2 — Eje B: barredor visual de la interfaz (pantalla virtual propia)

Crear `test/Vista/BarredorVisualE2EVirtual.java` (clase `main`, no trackeada) reutilizando `DiaE2ERobot` y `DiaE2EFixture`.

- **T2.1 Estructura y guardarraíles del arnés**: guard `-De2e.permitir=SI`; guard de BD (`restaurante_test`); `System.setProperty("user.home", E2E_WORKDIR)` y `modo.automatizado=true` (patrón `UsuariosCrudE2E:52-54`); **vía de arranque conmutable** por `args[0]`: `harness` (por defecto) → `Restaurante.lanzarLogin()`, como el resto de E2E; `real` → `Restaurante.main(new String[0])`, solo en la corrida confirmatoria (T3R.2b). Login con `E2E_LOGIN_USERNAME`/`E2E_LOGIN_PASSWORD` (default `admin`/`admin`); `esperarVentana(Sistema.class)`; `MAXIMIZED_BOTH`; al final **descomponer todas las ventanas**.
  - **Guardarraíles que matan la JVM si se incumplen**: (a) nunca pulsar **ESC**, la **X** ni el botón **"Salir"** de `FrmLogin` → `System.exit(0)` (`FrmLogin.java:87-93,411,487-490`); (b) la **X de `Sistema`** abre un `JOptionPane` y luego `System.exit(0)` (`Sistema.java:330-336,6430-6466`) ⇒ para cerrar una sesión usar `sistema.dispose()` + `Restaurante.lanzarLogin()` (patrón ya del producto en `Sistema.java:2189-2190`), **nunca** la X; (c) `Restaurante.main` solo puede invocarse **una vez por JVM** (guard de instancia única en el puerto 49200 ⇒ la segunda llamada hace `System.exit(0)` y mata el proceso: `Restaurante.java:85-111`); si el puerto 49200 estuviera ocupado, usar `-Drestaurante.instancia.puerto=<otro>`; (d) si `bootstrap_inicial_completado` no valiera 1, `main` abriría un `JDialog` **modal** que cuelga el hilo principal ⇒ verificar esa clave antes de usar el modo `real`; (e) **no reintentar un login fallido** en bucle: 5 intentos bloquean la cuenta 30 s (`LoginDao.java:169-170`).
  - `E2E_EVIDENCIA_DIR` (ruta **absoluta**, obligatoria): destino de PNG y TSV. Nunca dentro de `E2E_WORKDIR` (se borra al salir). Si falta, el barredor **falla** en vez de escribir en un sitio relativo (default documental: `<repo>/evidencias/produccion/2026-10-21`).
  - **Aislamiento de ficheros**: `respaldos/` (respaldo diario) y `logs/` se escriben **relativos al CWD**, no a `user.home` (`ServicioRespaldoBaseDatos.java:53`, `ArchivoLogDiario.java:32-33`); los PDF de factura usan `user.home` (`GeneradorPdfPedido.java:102`). Con `user.home` = `E2E_WORKDIR` y CWD = `E2E_WORKDIR` (el wrapper hace `cd`), todo queda en el temporal que se borra ⇒ **nada** se escribe en el repo ni en el home del operador.
  - Acceso tipado: helper local `private static <T> T campo(Object obj, String nombre, Class<T> tipo)` (patrón `FiltroRapidoPlatosTest`/`SistemaTabsYVistasTest`); **no** se modifica `DiaE2ERobot` (compartida por 20+ E2E).
- **T2.2 Recorrido**: `jTabbedPane1` vía `campo(sistema,"jTabbedPane1",JTabbedPane.class)`; iterar **`getTabCount()` real** (12 esperados; "Atención Directa (Split)" es condicional) con `robot.seleccionarPestaña(tabs, i)` **por índice**. Registrar índice+título+componente de cada pestaña y **detectar títulos duplicados** ("Platos" en 3 y 8). Antes de medir, esperar a que la pestaña se pueble (los `SwingWorker` cargan asíncrono: espera acotada; si sigue vacía → "SIN DATOS" en la matriz, no hallazgo). Si `getComponentAt(i)` es `null` → hallazgo "pestaña sin componente".
  - Cubrir también **`FrmLogin`** (antes de entrar) y **`FrmBootstrapInicial`** (diálogo) con las mismas reglas.
  - Dentro de "Datos de la Empresa", barrer las **5 subpestañas** de `tabConfigDerecha` (la última, "Roles y accesos", solo con `GESTIONAR_ROLES_ACCESOS`).
- **T2.3 Reglas** (cada violación = hallazgo con ruta del componente y captura):

  **Precondición:** forzar `doLayout()` + `validate()` + `revalidate()` antes de medir y leer los bounds **post-layout**; `AbsoluteLayout` ignora `setBounds()` de sus hijos (F-67), comparar bounds pretendidos daría falsos positivos.

  | Regla | Detección | Severidad inicial |
  |---|---|---|
  | R1 Texto cortado | `JLabel`/`JButton`: `getPreferredSize().width > getWidth() + 2`; con HTML comparar también alto; confirmar con `FontMetrics.stringWidth` | MEDIA |
  | R2 Columna de tabla cortada | `TableColumn.getWidth() < getPreferredWidth() - 2`; ancho total vs viewport; alto de fila vs alto preferido (caso `RendererPlato`) | MEDIA |
  | R3 Recortado por el padre | padre con layout `null`/`AbsoluteLayout` o tamaño fijo: `hijo.x+hijo.width > padre.width` o `hijo.y+hijo.height > padre.height` | ALTA |
  | R4 Fuera de pantalla | `getLocationOnScreen()`+size no cabe en 1366×711 ni en el frame visible | ALTA |
  | R5 Invisible | visible en el árbol pero `width==0 \|\| height==0`, o en un viewport cuyo `visibleRect` no lo contiene **y** sin scroll que lo revele | ALTA |
  | R6 Tabla sin scroll | `JTable` sin ancestro `JScrollPane` | MEDIA |
  | R7 Área de texto sin scroll | `JTextArea` con `setLineWrap(true)` y alto fijo sin `JScrollPane` | MEDIA |
  | R8 Padre que ignora al hijo | `hijo.getPreferredSize()` mayor que el tamaño asignado al padre | MEDIA |
  | R9 Inventario oculto/deshabilitado | volcado de `setVisible(false)`/`setEnabled(false)` → **no es hallazgo automático**: inventario para triage, contrastado con el oráculo de rol (`SistemaTabsYVistasTest:309-331`) | INFO → hallazgo si contradice el oráculo |

  **Contexto de layout verificado:** solo ~9 % de los contenedores de `Sistema.java` usan `AbsoluteLayout` o layout `null` (11 AbsoluteLayout + 3 `setLayout(null)`, de ~128 contenedores) y **0 %** en `PanelAtencionMesasSplit.java` ⇒ el riesgo de inundación no viene del layout absoluto, sino de los viewports y de los suelos `Math.max(...)` (guarda e). Y `AbsoluteLayout` **ignora `setBounds()`** de sus hijos, por eso la precondición de forzar el layout es imprescindible.

  **Guardas anti-falso-positivo (obligatorias):** (a) una pestaña **deshabilitada por RBAC** no es R5 ni R3 (las pestañas se deshabilitan, no se ocultan) → va al inventario R9; (b) un componente dentro de un viewport fuera de `visibleRect` **con scroll** no es R5 **ni R3** (el viewport es más pequeño que su contenido por diseño); (c) componentes en contenedores colapsados/ocultos se ignoran; (d) tolerancia 2 px; (e) **R3/R4/R8 se comparan contra el `preferredSize` declarado del padre, no contra su tamaño asignado**: hay contenedores con suelo `Math.max(...)` que se auto-agrandan y legítimamente exceden (p. ej. `jPanel25` se auto-agranda a 980×480 con `preferredSize` 1194×620, `Sistema.java:1326-1347`; `jLabel38` tiene ancho mínimo 1290 px, `Sistema.java:2031-2033`) ⇒ un hijo que excede al padre **pero cabe en su `preferredSize`** no es hallazgo; (f) el barrido **no redimensiona columnas** ni arrastra bordes: `GestorAnchosTabla` **guarda** los pesos en la tabla `preferencias_tabla` de la BD con debounce de 600 ms (`GestorAnchosTabla.java:26,43-56,101-132`) ⇒ el propio barrido alteraría la medición de corridas posteriores; (g) **tope por pestaña y regla** (máx. 5 hallazgos por regla y pestaña) para que el informe no se inunde, priorizando los de mayor desbordamiento; (h) todo hallazgo se **confirma a ojo con su PNG** antes de entrar al informe.
- **T2.4 Evidencia**: `java.awt.Robot` propio del barredor → `robot.createScreenCapture(...)` a **ruta absoluta** `evidencias/produccion/2026-10-21/visual/<pestana>-<regla>-<n>.png` (el `E2E_WORKDIR` se borra al salir). Una línea de log por hallazgo: pestaña, índice, clase, texto, ancho requerido vs real, coordenadas.
- **T2.5 Salida**: **acumula**, no aborta en el primero. Imprime `BARRIDO_VISUAL_OK` (0 hallazgos) o `BARRIDO_VISUAL_HALLAZGOS <n>` y `exit 1`; vuelca TSV/JSON de hallazgos en `$E2E_EVIDENCIA_DIR` (ruta absoluta, pasada por env) para parseo posterior. Cada PNG se nombra `<pestana>-<regla>-<n>.png` en `$E2E_EVIDENCIA_DIR/visual/`; los TSV por rol en `$E2E_EVIDENCIA_DIR/rbac/`.
- **T2.6 Casos dirigidos con dato real** (para que R1/R2 tengan disparador real, no vacío): plato con nombre ≥60 caracteres y precio con decimales largos (columnas "Plato"/"Precio" de `tblTemPlatos`/`tblCatalogoPlatos` — reincidencia del H1: 158 px < 172 px); mesonero con nombre largo (tarjetas `btnMesa` con HTML de 3 líneas en `preferredSize(120,110)`, `PanelAtencionMesasSplit:875,890-900`); cliente con RUC/dirección largos (cabecera de factura); texto largo en `txtDesc` (`Sistema:8649-8662`, `JTextArea` sin scroll, alto 110) y `txtInfoModos` (`:847-858`, alto 120); Split POS (`splitDerecho` con divisor 180 px, `PanelAtencionMesasSplit:241-242`, frente a columna "Plato" de 300 px `:340`, y el botón `btnFuga` + 7 controles acumulados — regresión F-70); diálogos con `setSize` fijo (logs 950×600 `:7520`, reactivar platos 520×360 `:8481`, auditoría 620×320 `:9280`) con contenido saturado.
- **T2.7 Ejecución: tres corridas, tres preguntas distintas.** Ojo: `e2e_dia_completo_virtual.sh` **siempre crea una BD virgen** (`restaurante-dia-e2e-$$-<epoch>`, volumen propio) y **aborta si 3307 está ocupado** ⇒ no sirve para correr contra una BD ya sembrada. Para eso hay que invocar `e2e_virtual.sh` directamente (no toca docker, no comprueba el puerto), exportando a mano el entorno que el wrapper del día prepara (`DB_*`, `TZ=UTC`, `BACKUP_HMAC_KEY`, `ACCION_IMPRESION=IMPRIMIR`, `CUPS_SERVER`, `PDF_FACTURAS_DIR`, `E2E_WORKDIR` temporal y un `lp` falso en el `PATH`). Las tres corridas son **secuenciales** (el `flock` y el lock de `:99` lo impiden en paralelo):
  1. **Artefacto del arnés** — modo `harness` contra BD **virgen** (sin matriz): `./e2e_dia_completo_virtual.sh Vista.BarredorVisualE2EVirtual harness` → `barredor-visual-harness-virgin.log`. Documenta que, sin matriz, todo rol no-Administrador queda sin pestañas (fail-secure). Es la corrida que **no** es la medición.
  2. **Confirmatoria por la vía real** — modo `real` contra BD **virgen**: `./e2e_dia_completo_virtual.sh Vista.BarredorVisualE2EVirtual real` → `barredor-visual-real.log`. `Restaurante.main` siembra la matriz **in-process** (`Restaurante.java:170`) y mide el comportamiento real del producto por rol. Requiere puerto 49200 libre y `bootstrap_inicial_completado=1` (guardarraíles en T2.1).
  3. **Medición** — modo `harness` contra la BD de la campaña **con la matriz ya sembrada** por `--migrate-db` (T3R.0), invocando `e2e_virtual.sh` **directamente** con el entorno exportado a mano → `barredor-visual-harness-matriz.log`. El diff entre (3) y (2) debe ser ≈ vacío: eso demuestra que el arnés reproduce el producto. El diff entre (2) y (1) es el artefacto del arnés.
- **T2.8 Segunda resolución (opcional)**: `e2e_virtual.sh` fija `GEOM=1366x711x24` (peor caso en alto) = baseline obligatorio. Para una segunda resolución, **copiar el script a `/tmp/opencode/` y cambiar `GEOM` ahí** (el script es infraestructura compartida). Al repetirla hay que **replicar los exports manuales** del paso 9 (`E2E_BD_DESECHABLE`, `DB_*`, `TZ`, `BACKUP_HMAC_KEY`, `ACCION_IMPRESION`, `CUPS_SERVER`, `PDF_FACTURAS_DIR`, `E2E_WORKDIR` temporal y el `lp` falso en el `PATH`): `e2e_virtual.sh` no los prepara por sí mismo y, sin `E2E_WORKDIR`, el CWD sería la raíz del repo.
- **Gate 2:** el barredor corre completo, deja PNG por hallazgo, salida parseable y **cada hallazgo confirmado visualmente**.

### Fase 3 — Eje C: lógica de negocio (pasada 1 — dinero primero)

Por interfaz (patrón `DiaCompletoFiscalE2EVirtual`), verificando **en la UI** (no por SQL).

- **T3.0 Smoke de aceptación** (ítem abierto del gate de release, `plan-produccion.md:288`): login → abrir mesa → comanda → cobro → cierre de caja, sin fallos intermedios. Si falla ⇒ hallazgo **CRÍTICO** (bloquea el release).
- **T3.1 Facturación**: comanda → finalizar → total/IVA/subtotal en pantalla; reabrir y comprobar persistencia idéntica; `Σ líneas == total` **en la tabla**.
- **T3.2 Pago multi-método**: efectivo USD + tarjeta Bs; arqueo de efectivo y resumen por método del Cierre Z = lo cobrado (regresión F-03/F-17).
- **T3.3 Anulación de factura pagada**: el flujo de reversión en caja **existe en la UI** (no solo la validación que la bloqueaba, F-07/F-57) y saldo/arqueo cuadrados.
- **T3.4 Cierre Z**: guardar, reabrir, inmutabilidad; un **segundo** cierre del mismo día debe ser rechazado con mensaje al usuario.
- **T3.5 Fuga / mesa no pagada**: la mesa queda liberada y el cierre no arrastra esa cuenta (F-08/F-36).
- **T3.6 Robo de cantidades** en `txtCantidadManual`: escribir "4" y clicar otra fila sin Enter ⇒ la cantidad no cambia (F-09).
- **T3.7 Escape de datos entre mesas**: cliente/RUC de una factura no se arrastran a la mesa siguiente (F-53).
- **Gate 3:** cada flujo con evidencia (log + captura del estado final) y PASS/FAIL.

### Fase 3C — Eje R: acceso por rol (RBAC multi-rol)

- **T3R.0 Preparar la matriz de permisos (IMPRESCINDIBLE antes de medir)**: la BD recién sembrada **no** tiene claves `seguridad.rol.*` y el arnés E2E (`Restaurante.lanzarLogin()`) **no** las siembra ⇒ sin este paso, todo rol no-Administrador tendría **cero** permisos y el eje mediría un artefacto, no el producto.
  **Procedimiento (decidido por el operador el 21/10/2026): sembrar por la vía real del producto.** La siembra se hace con la CLI soportada, en un **proceso separado** (no dentro del E2E):
  `DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?...' DB_USER=restaurante_test_app DB_PASSWORD=test_only_app_password DB_NAME=restaurante_test java -jar dist/Restaurante.jar --migrate-db`
  (`Restaurante.java:149-166`: `--migrate-db` valida el esquema con el migrador y llama a `asegurarMatrizPredeterminada()`; es un proceso propio que termina solo, con lo que **no** se activa el guard de instancia única ni el diálogo modal de bootstrap dentro del arnés). El `dist/Restaurante.jar` existe (`dist/Restaurante.jar`, ~1,9 MB).
  La siembra se aplica a la **BD de la campaña del barredor** (el proyecto compose `qa-barredor-20261021`, la única que persiste entre corridas; el de la Fase 0 es `qa-campana-20261021` y se baja antes). El wrapper del día **no** puede reutilizarla porque crea un volumen nuevo en cada invocación ⇒ por eso la corrida 3 de T2.7 invoca `e2e_virtual.sh` directamente, y por eso la corrida 2 (modo `real`) siembra su propia matriz in-process.
  Verificar tras la siembra que la matriz existe: la UI "Roles y accesos" debe listar las 20 acciones por rol y, opcionalmente, contar claves con `docker exec <contenedor> mysql -uroot -ptest_root_pass restaurante_test -e "SELECT COUNT(*) FROM configuracion_sistema WHERE clave LIKE 'seguridad.rol.%'"` (el host no tiene CLI `mysql`).
  Reportar además, con veredicto propio, el hallazgo de robustez: **matriz ausente ⇒ todos los roles no-Administrador quedan sin ninguna pestaña habilitada, sin mensaje al usuario** (fail-secure). No es escalada, pero sí bloqueo total, reproducible en un despliegue restaurado desde un dump antiguo o arrancado por otra vía.
- **T3R.1 Crear un usuario por rol vía UI** (patrón `UsuariosCrudE2E`: pestaña "Usuarios" → `txtNombre`/`txtCorreo`/`txtPass`/`cbxRol` → `btnIniciar` (guardar) → `TableUsuarios`). Roles: **Administrador, Asistente, Mesonero, Cajero, Cocinero** (Maitre y Host se cubren por lectura de código: son casi-subconjuntos de Mesonero/Asistente). Contraseña de prueba conocida, solo en la BD desechable.
- **T3R.2 Barrido por rol**: el barredor acepta `-De2e.roles=admin,asistente,mesonero,cajero,cocinero` y hace, **en la misma JVM** (con la matriz ya sembrada por T3R.0 **en la BD de la campaña**; en la corrida 2, la matriz la siembra el propio `Restaurante.main` in-process):
  1. login `admin`/`admin`;
  2. alta por UI de los usuarios de los 5 roles (T3R.1);
  3. por cada rol: **cerrar la sesión con `sistema.dispose()` y abrir un nuevo login con `Restaurante.lanzarLogin()`** (patrón del propio producto en `Sistema.java:2189-2190`; **nunca** la X de `Sistema`, que hace `System.exit(0)`), autenticar con las credenciales del rol, registrar la **matriz de habilitación** (pestaña × enabled/visible) y el **inventario R9** (controles ocultos/deshabilitados), y ejecutar el barrido visual R1–R9;
  4. volcar por rol: `rbac/<rol>-pestanas.tsv`, `rbac/<rol>-inventario.tsv` y PNG de las pestañas de ese rol.
  Los 5 roles **no se contaminan entre sí**: los anchos de tabla se guardan en `preferencias_tabla` con clave `usuario_id`+`tabla_clave` (`PreferenciasTablaDao.java:45,66-69`), es decir, por usuario. Cada cambio de sesión deja un `Timer` de vigencia de 60 s vivo (`Sistema.java:2154`): es ruido acotado, se acepta y se anota como limitación.
- **T3R.2b Contraste vía real vs arnés (corrida confirmatoria, autorizada por el operador)**: el diff se hace entre la **corrida 2** (modo `real`, matriz sembrada in-process por el producto) y la **corrida 3** (modo `harness` contra la BD con la matriz sembrada por `--migrate-db`): debe ser **≈ vacío**, y cualquier diferencia ⇒ hallazgo de atribución (el arnés E2E no reproduce el arranque del producto). La **corrida 1** (harness contra BD sin matriz) documenta el artefacto: roles no-Administrador sin ninguna pestaña habilitada. Para que los diffs sean limpios, la corrida 2 debe usar **usuarios distintos** de la corrida 3 (o vaciar `preferencias_tabla` entre corridas), porque los anchos de tabla persisten en BD por `usuario_id` y las dos corridas comparten el `admin`.
- **T3R.3 Escalada por navegación — hipótesis verificadas contra el código, con veredicto a producir**:
  1. **"Platos" duplicado** (`Sistema:1116-1121`): `habilitarTab` habilita **las dos** pestañas con el mismo título ⇒ Asistente/Mesonero/Maitre (CONSULTAR_PLATOS) alcanzan la pestaña de **administración** de platos (índice 8). Los controles mutativos **sí** están gated por `GESTIONAR_PLATOS` (`:1084-1090`) ⇒ severidad inicial **MEDIA (exposición de UI de administración)**, y **ALTA** si el barrido encuentra en `jPanel2`/`jScrollPane4`/`jPanel11` un control mutativo **no** gated (borrar, reactivar, duplicar, exportar).
  2. **"Categorías"** ← `CONSULTAR_PLATOS` (`:1062`) mientras administrar categorías pertenece a `GESTIONAR_PLATOS` ⇒ enumerar los controles de la pestaña y comprobar el gating uno a uno.
  3. **"Dashboard"** ← `EDITAR_CONFIGURACION` (solo Administrador) ⇒ el **Cajero no ve el Dashboard** pese a cobrar y consultar historial ⇒ **sobre-restricción** a decidir con el operador (el auto-selección del Dashboard sí está correctamente gated a `esAdministrador()`, `Sistema:929-936`: **no** hay escalada por esa vía).
  4. **Cocinero con 0 permisos por default** ⇒ `aplicarAccesosInterfaz` deshabilita **todas** las pestañas ⇒ verificar si el Cocinero tiene alguna pantalla de comandas/marchas accesible (el diario de marchas está implementado, `plan-produccion.md:299`). Si no la tiene ⇒ rol **operativamente inalcanzable** (ALTA).
  5. **"Atención Directa (Split)"** se habilita con **cualquiera** de REGISTRAR_PEDIDOS / CONSULTAR_PEDIDOS_PENDIENTES / COBRAR_PEDIDOS / EDITAR_PEDIDOS_ABIERTOS (semántica OR, `:1059-1061`) ⇒ Asistente puede abrirla ⇒ enumerar los controles del split (recibe `politicaAcceso`, `Sistema:10919`) y comprobar el gating individual.
  6. **"Datos de la Empresa"** ← `EDITAR_CONFIGURACION` ⇒ contiene "Base de Datos y Respaldos" (respaldo y **restauración**), "Mesoneros" y "Roles y accesos": todo admin-only. Verificar coherencia con la acción de cada subpestaña; `btnPurgarHistorial` (`:915-917`) y `spMesesRetencion` (`:902`) son admin-only.
- **T3R.4 Acciones sensibles inalcanzables**: para cada una de las 8 acciones `soloAdministrador`, comprobar por rol que **no** hay vía de invocación desde la UI. No se intenta ejecutar la acción prohibida: solo se comprueba que la UI no ofrece el camino.
- **T3R.5 Coherencia con el oráculo de test**: contrastar el diff por rol con `test/Vista/SistemaTabsYVistasTest.java:309-331`. Discrepancia ⇒ hallazgo.
- **Gate 3R:** matriz `rol × pestaña × acción` completa + diff de inventario + los 6 puntos de T3R.3 con veredicto (escalada / sobre-restricción / correcto) **y evidencia**.

### Fase 4 — Eje D: resto de la aplicación (pasada 2)

Con barredor (T2) **y** verificación funcional por UI: **T4.1** catálogo (alta/edición/baja/reactivar, IVA y moneda, filtro rápido, redimensionado de columnas — revisar por qué fallaba `RedimensionarColumnasJTableE2EVirtual`); **T4.2** salas y mesas (alta/edición, mesonero, estados, leyenda); **T4.3** usuarios y roles (CRUD, protección del último Administrador, cambio de rol, columnas de la tabla); **T4.4** configuración (empresa RUC/dirección/logo, impresión/tickets, respaldos, mesoneros, roles y accesos — 5 subpestañas); **T4.5** dashboard (KPIs, Top Platos, Mesoneros, clientes, resumen de ventas **con datos reales** para que el barrido detecte cortes); **T4.6** historial (filtros, reimpresión, previsualización, totales); **T4.7** categorías y favoritos (asignación, borrado bloqueado por uso, renombrado — F-59/F-60).
- **Gate 4:** cobertura de 12 pestañas + 5 subpestañas + Split POS + diálogos, con la matriz del informe rellenada (incluidas las áreas **sin** hallazgos).

### Fase 5 — Eje E: regresión, aislamiento y consolidación

- **T5.1** Reejecutar `ant -q test`, `ant -q integration-test` y las 12 clases E2E (aisladas) ⇒ la instrumentación nueva no contaminó nada.
- **T5.2** **Reproducir cada hallazgo en sesión aislada** (regla H4). Un hallazgo que no se reproduce aislado **no entra al informe**; se anota "no reproducible / descartado" con el motivo.
- **T5.3** Deduplicar y graduar: **CRÍTICA** (corrupción de datos/dinero o pérdida de operación) · **ALTA** (bloqueo o descuadre con impacto real) · **MEDIA** (defecto visible o lógica sin pérdida) · **BAJA** (cosmético). Numeración propia **`QA-01`…`QA-NN`** (no reutilizar la serie `F-` de `fallos.md`).
- **Gate 5:** cada `QA-NN` con evidencia, pasos, severidad y recomendación (sin aplicar el arreglo).

### Fase 6 — Informe y cierre

- **T6.1 `qa-informe-campana-2026-10-21.md`** (raíz, trackeado): 1) Alcance, entorno (SHA, geometría, versiones) y **qué NO se hizo**; 2) **Matriz de cobertura**: filas = 12 pestañas + `FrmLogin` + `FrmBootstrapInicial` + 5 subpestañas + Split POS + 3 diálogos; columnas = R1–R9 + ejes A/C/D/R ⇒ `PASS`/`FAIL`/`NO EJECUTADO`/`SIN DATOS` con puntero a evidencia, más columnas de **resolución** (1366×711 y alternativa) y de **rol**; incluir la **matriz RBAC** `rol × pestaña × acción`; 3) Resumen ejecutivo por severidad; 4) Tabla de hallazgos `# | Severidad | Categoría | Componente (ruta:línea) | Evidencia | Impacto | Recomendación`; 5) Detalle por hallazgo (pasos, esperado vs observado, PNG, por qué importa en operación); 6) **Áreas sin hallazgos**; 7) Limitaciones y falsos positivos descartados.
- **T6.2 `bitacora-contexto-produccion.md`**: sección `snapshot 21/10/2026 HH:MM` con la tabla `| Comando | Archivo de salida | Estado y alcance |` (PASS/FLAKY/FAIL con el alcance real de cada ejecución) para compilación, las 3 líneas base, la línea base E2E por clase, la IT nueva, el barredor (por rol), cada E2E de flujo y las reejecuciones; referenciar el informe.
- **T6.3 Commit** (solo si el operador lo pide): informe + bitácora + `evidencias/produccion/2026-10-21/**`. **Nunca** `test/**` ni nada con credenciales. Antes: `git status --short` + `git ls-files | grep -iE "env|respaldo|log|key|hmac|cred|sql"`.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Falsos positivos del barredor | Confirmación visual obligatoria por PNG; tolerancia 2 px; guardas de R3/R5 (pestaña deshabilitada ≠ invisible; viewport con scroll ≠ recortado) |
| Contaminación entre clases E2E (H4) | T0.6 por clase en BD propia + T5.2 reproducción aislada |
| **3307 ocupado** por otro proceso o por `ant integration-test` | Todas las fases con BD son **secuenciales**; verificación previa de puerto libre; compose siempre con `-p` |
| **El `E2E_WORKDIR` se borra** al terminar el wrapper | Evidencias del barredor a **ruta absoluta** (`E2E_EVIDENCIA_DIR`), nunca relativa |
| Falsos FAIL por tocar la BD de servicio | Todo a `restaurante_test`/3307; guard `-unit-db-safety`; neutralizar `.env` con `-DDB_URL/-DDB_NAME/-DMYSQL_DATABASE` |
| `ant test` sin display o con el del operador | Display virtual propio `:98` |
| Impresión que bloquea al Robot | `ACCION_IMPRESION=IMPRIMIR` con `lp` falso; nunca `VISOR` |
| **Medir RBAC sobre una matriz de permisos inexistente** (artefacto del arnés) | Semilla por `--migrate-db` (T3R.0) verificada + corrida normal `harness` y corrida confirmatoria `real` (T3R.2b), para separar el artefacto del arnés del comportamiento del producto |
| `Restaurante.main` en el E2E dispara migrador (DDL), semilla de la matriz y respaldo diario ⇒ escrituras y archivos | Solo sobre la BD desechable 3307; el respaldo diario va a `respaldos/` relativo al CWD y los PDF a `user.home`, ambos dentro de `E2E_WORKDIR`; `BACKUP_HMAC_KEY` de prueba (el mismo que usa el wrapper) |
| Confundir un FAIL de suite con un bug de producto | Línea base por clase (T0.6) + reproducción aislada (T5.2) + verdictos de artefacto |
| Duplicar cobertura ya existente | T1.4 exige el mapa de cobertura previo antes de escribir la IT nueva |
| `ant test` se detiene en el primer fallo (`haltonfailure`) | Recuento real desde `build/test/results/**/*.xml`, no del exit code |
| Coste de corridas (11 levantamientos de MySQL + bootstrap + 3 del barredor + reproducciones) | Se acepta por credibilidad; se registra el tiempo por corrida y se pide autorización al operador si las reproducciones superan 15; total previsto ≈ 17 levantamientos de MySQL en la campaña |
| **R3/R4/R8 inundan el informe** (suelos `Math.max`, viewports) | Comparar contra el `preferredSize` del padre, eximir viewports en R3, tope de 5 hallazgos por regla y pestaña, confirmación visual por PNG |
| **Xvfb :98 no existe** (solo hay `:0`) | T0.2b lo levanta y lo baja; el display del operador nunca se toca |
| **El backup diario de `main` escribe `respaldos/` en el CWD** | CWD = `E2E_WORKDIR` (temporal que se borra) ⇒ nunca en el repo |
| **`Restaurante.main` mata la JVM** (instancia única 49200 / bootstrap modal) | Modo `real` solo en la corrida confirmatoria, con 49200 libre y `bootstrap_inicial_completado=1` verificados |
| **Login fallido en bucle bloquea la cuenta 30 s** | Credenciales correctas por rol, sin reintentos automáticos |
| Secretos en el informe | Sanear credenciales de los logs antes de commitear; `.env` no se toca |

## Checklist de cierre

- [ ] Las 3 líneas base con estado real; fallos preexistentes identificados.
- [ ] Línea base E2E **por clase** (12 clases) con PASS/FAIL y reclasificación de los FAIL de stack compartido.
- [ ] Mapa de cobertura de las IT existentes y IT nueva solo con los huecos, con alcance declarado.
- [ ] Barredor ejecuta completo, deja PNG por hallazgo y salida parseable; hallazgos confirmados a ojo.
- [ ] Matriz de cobertura de 12 pestañas + 5 subpestañas + Split POS + diálogos.
- [ ] RBAC: matriz `rol × pestaña × acción`, diff de inventario por rol, **corrida 2 (`real`) vs corrida 3 (`harness` con matriz) ≈ vacío** y **corrida 1 (`harness` sin matriz) = artefacto documentado**, y los 6 puntos de T3R.3 con veredicto y evidencia; T3R.0 documentado.
- [ ] Cada hallazgo con evidencia, pasos, severidad, recomendación y **reproducción aislada**.
- [ ] Informe + bitácora escritos; `evidencias/produccion/2026-10-21/` poblado.
- [ ] `src/` intacto; `test/` ignorado y no commiteado; nada con credenciales commiteado.

## Comandos de referencia (orden de ejecución, todo secuencial)

El orden importa: **3307 es un recurso único** y el wrapper del día **aborta si está ocupado** y **siempre crea una BD virgin**. Por eso: (a) el compose de la Fase 0 se baja en el paso 3 antes de la integración y del bucle E2E; (b) las corridas 1 y 2 del barredor usan el wrapper (BD virgin, 3307 libre); (c) la corrida 3 usa la BD de campaña persistente y por eso invoca `e2e_virtual.sh` **directamente** con el entorno exportado a mano, porque el wrapper no puede consumir una BD ya levantada.

```bash
set -o pipefail
mkdir -p evidencias/produccion/2026-10-21

# --- Fase 0: línea base de las tres suites (todas secuenciales) ---

# 0) Display virtual propio para la suite unitaria (nunca se toca :0)
Xvfb :98 -screen 0 1366x711x24 & XVFB_PID=$!; sleep 1

# 1) BD desechable de la Fase 0 (proyecto compose explícito; ocupa 3307)
docker compose -f docker-compose.integration.yml -p qa-campana-20261021 up -d --wait

# 2) Línea base unitaria (el guard -unit-db-safety exige neutralizar .env)
DISPLAY=:98 ant -q test \
  -DDB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
  -DDB_NAME=restaurante_test -DMYSQL_DATABASE=restaurante_test \
  -Dtest-sys-prop.MYSQL_PORT=3307 -Dtest-sys-prop.MYSQL_DATABASE=restaurante_test \
  -Dtest-sys-prop.MYSQL_USER=restaurante_test_app -Dtest-sys-prop.MYSQL_PASSWORD=test_only_app_password \
  -Dtest-sys-prop.DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
  -Dtest-sys-prop.DB_USER=restaurante_test_app -Dtest-sys-prop.DB_PASSWORD=test_only_app_password \
  2>&1 | tee evidencias/produccion/2026-10-21/base-unit.log

# 3) BAJAR el compose: 3307 debe quedar libre para la integración y para el wrapper E2E
docker compose -f docker-compose.integration.yml -p qa-campana-20261021 down --volumes --remove-orphans

# 4) Línea base de integración (levanta y baja su propio compose; requiere 3307 libre)
ant -q integration-test 2>&1 | tee evidencias/produccion/2026-10-21/base-integration.log

# 5) Línea base E2E por clase (BD virgin por corrida; SECUENCIAL: el wrapper aborta si 3307 está ocupado)
for c in Vista.E2EVirtual Vista.PedidoRapidoE2EVirtual Vista.DiaCompletoFiscalE2EVirtual \
         Vista.AtencionDirectaE2EVirtual Vista.AtencionDirectaCompletoE2EVirtual \
         Vista.OpcionCSplitE2EVirtual Vista.Platos70IvaMonedaE2EVirtual \
         Vista.RedimensionarColumnasJTableE2EVirtual Vista.UsuariosCrudE2E \
         aceptacion.E2EF09Virtual aceptacion.E2EGuardadoPlatosVirtual; do
  ./e2e_dia_completo_virtual.sh "$c" 2>&1 | tee "evidencias/produccion/2026-10-21/base-e2e-${c}.log"
done
# BootstrapInicial va aparte (su propio flujo y su propia clave de admin): ./e2e_bootstrap_virtual.sh

# --- Fase 2/3C: barredor visual + matriz RBAC (tres corridas, tres BDs) ---

# 6) Corrida 1 — ARTEFACTO del arnés: modo harness contra BD VIRGEN (sin matriz).
#    Wrapper del día: crea su propia BD y aborta si 3307 está ocupado (por eso va aquí, con 3307 libre).
E2E_EVIDENCIA_DIR="$PWD/evidencias/produccion/2026-10-21" \
  ./e2e_dia_completo_virtual.sh Vista.BarredorVisualE2EVirtual harness \
  2>&1 | tee evidencias/produccion/2026-10-21/barredor-visual-harness-virgin.log

# 7) Corrida 2 — CONFIRMATORIA por la vía real: modo real contra BD VIRGEN.
#    Restaurante.main siembra la matriz in-process. Requiere 49200 libre y bootstrap_inicial_completado=1.
E2E_EVIDENCIA_DIR="$PWD/evidencias/produccion/2026-10-21" \
  ./e2e_dia_completo_virtual.sh Vista.BarredorVisualE2EVirtual real \
  2>&1 | tee evidencias/produccion/2026-10-21/barredor-visual-real.log

# 8) MEDICIÓN: BD de campaña persistente con la matriz sembrada por la vía real del producto.
#    RUN_E2E se crea ANTES: CWD y logs van allí, nunca a la raíz del repo
#    (ConfiguracionLogs.configurar() corre antes del early-return de --migrate-db y crea logs/ en el CWD).
ant -q compile jar
RUN_E2E="$(mktemp -d "${TMPDIR:-/tmp}/restaurante-qa-XXXXXX")"; mkdir -p "$RUN_E2E/bin" "$RUN_E2E/facturas" "$RUN_E2E/logs"
printf '#!/bin/sh\nexit 0\n' > "$RUN_E2E/bin/lp"; chmod +x "$RUN_E2E/bin/lp"
docker compose -f docker-compose.integration.yml -p qa-barredor-20261021 up -d --wait
( cd "$RUN_E2E" && DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
  DB_USER=restaurante_test_app DB_PASSWORD=test_only_app_password DB_NAME=restaurante_test \
  java -Drestaurante.logs.dir="$RUN_E2E/logs" -jar "$OLDPWD/dist/Restaurante.jar" --migrate-db ) \
  2>&1 | tee evidencias/produccion/2026-10-21/siembra-matriz.log

# 9) Corrida 3 — modo harness contra esa BD (e2e_virtual.sh directo: el wrapper crearía una BD virgin).
#    El entorno del wrapper del día hay que exportarlo a mano (e2e_virtual.sh no lo prepara).
export PATH="$RUN_E2E/bin:$PATH" E2E_WORKDIR="$RUN_E2E" PDF_FACTURAS_DIR="$RUN_E2E/facturas"
export E2E_BD_DESECHABLE=SI TZ=UTC ACCION_IMPRESION=IMPRIMIR CUPS_SERVER=127.0.0.1:65534
export BACKUP_HMAC_KEY='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
export DB_URL='jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER=restaurante_test_app DB_PASSWORD=test_only_app_password
export DB_NAME=restaurante_test MYSQL_DATABASE=restaurante_test
E2E_EVIDENCIA_DIR="$PWD/evidencias/produccion/2026-10-21" \
  ./e2e_virtual.sh Vista.BarredorVisualE2EVirtual harness \
  2>&1 | tee evidencias/produccion/2026-10-21/barredor-visual-harness-matriz.log

# 10) Limpieza final del volumen de la campaña (proyecto propio, nunca sin -p)
docker compose -f docker-compose.integration.yml -p qa-barredor-20261021 down --volumes --remove-orphans
rm -rf "$RUN_E2E"
kill $XVFB_PID
```

## Rutas clave

`src/Vista/Sistema.java` (12 pestañas, `aplicarAccesosInterfaz` 1046, `habilitarTab` 1116) · `src/Vista/PanelAtencionMesasSplit.java` · `src/Vista/RendererPlato.java` · `src/Servicio/PoliticaAcceso.java` · `src/Servicio/ConfiguracionAccesosServicio.java` · `src/Servicio/RolesSistema.java` · `src/restaurante/Restaurante.java` (153/170/194) · `src/infraestructura/ProveedorConexionJdbc.java` (552) · `src/infraestructura/ControlMantenimientoBD.java` · `src/Modelo/PedidosDao.java` · `src/Modelo/CierreCajaDao.java` · `src/Modelo/ActualizacionTasaDao.java` · `test/Vista/DiaE2ERobot.java` · `test/Vista/DiaE2EFixture.java` · `test/Vista/UsuariosCrudE2E.java` · `test/Vista/SistemaTabsYVistasTest.java` · `test/integracion/*IT.java` · `e2e_virtual.sh` · `e2e_dia_completo_virtual.sh` · `e2e_bootstrap_virtual.sh` · `build.xml` · `nbproject/build-impl.xml` · `bitacora-contexto-produccion.md` · `fallos.md` · `plan-produccion.md` · `AGENTS.md` · `CONTEXT.md`

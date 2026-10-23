# Bitácora de continuidad — preparación para producción

snapshot 23/10/2026 16:01

## E2E cierre de sesión + salida del programa (BD = SSOT) (gate cerrado)

| Comando | Evidencia | Resultado |
|---|---|---|
| `timeout 700 ./e2e_virtual.sh Vista.CierreSesionDatosE2EVirtual` ×2 corridas (pipeline manual: compose `-p e2ecierre` + `inicializar_bd_test.sh` + bootstrap + seed) | `evidencias/produccion/2026-10-23/e2e-cierre-sesion-datos/corrida{1,2}.log`: fases 1-6 `OK` (alta mesonero, sala fresca, doble sesión en-JVM, ronda enviada + borrador NO enviado, desactivación por UI, revocación al vuelo, re-login denegado, re-login admin con cuenta íntegra) + `E2E_OK` + `SHUTDOWN_HOOKS_SLICE`, `EXIT=0` ambas | PASS |
| SQL post-mortem (proceso muerto) ambas corridas | cuenta `PENDIENTE` (`id=10` y `id=11`, total `5.00`, 1 línea exacta; borrrador nunca en BD; mesonero `activo=0` persistido) en `…e2e-cierre-sesion-datos/e2e-cierre-sesion-datos.md` | PASS |
| Arreglo de flake por acumulación de salas de prueba: clic de tira de salas desbordada caía fuera de la ventana (`bajoElPunto=null`); `seleccionarMesa` reescrita a la ruta por código de la app (`txtCodigoMesaDirecto` + Enter), siempre por interfaz | mismo archivo §"Notas de diseño"; 2 corridas verdes consecutivas tras el cambio | PASS |

Nota: clase de test local (`test/` desrastreado); no requiere `src/`. El guard de cookies del arnés (`e2e_dia_completo_virtual.sh`) destruye la BD — para el pool SSOT se usó pipeline manual replicando sus pasos.

snapshot 23/10/2026 07:40

## Auditoría de permisos / sesión en caliente (gate cerrado)

| Comando | Evidencia | Resultado |
|---|---|---|
| Mapa estático de los 19 `PoliticaAcceso.Accion` (exigir en servicios/controladores + `autorizar`/`permite` en UI) vía `grep` dirigido | `evidencias/produccion/2026-10-23/auditoria-acceso-sesion-caliente.md` (§1): sin acciones huérfanas; solo-UI anotadas (`CANCELAR_CUENTA_PENDIENTE`, `APLICAR_CORTESIA`, `EDITAR_CONFIGURACION`) | PASS |
| `JUnitCore Servicio.PoliticaAccesoRevocacionVigorTest` (nuevo, 2 casos, BD desechable `db-audit` 3307 + `inicializar_bd_test.sh --datos`) con el ensamblaje exacto de `Restaurante.lanzarLogin` (`esUsuarioActivoConRol` en cada `permite`, fail-closed) | mismo archivo §3: desactivar usuario en BD → la misma política deniega las 19 acciones en caliente (incluye admin fantasma) y reactivar rehabilita | PASS |
| Revisión del mecanismo de vigencia: `iniciarVigilanciaSesion` (foco + temporizador 60s coalesce; los días con revocación muestran 'Acceso revocado') + `autorizar` re-verifica BD por acción (Sistema.java:2141/2290) | §2 + §4: fail-closed en acciones, reintento en vigilancia pasiva; cambio de rol también revoca vía `esUsuarioActivoConRol(id, rol)` | PASS |

La clase del test entra en el patrón `**/Servicio/*Test` de `ant test` (2 casos más en la próxima corrida del suite). `test/` sigue desrastreado; se commitea sólo la evidencia y la documentación.

snapshot 22/10/2026 21:23

snapshot 22/10/2026 21:23

## Ciclo BD-test.sql + arneses E2E por UI (todo por la interfaz, Xvfb :99)

Se consolidó el ciclo del fixture `BD-test.sql` (generación, carga, verificación) y el arnés E2E completo por UI real. Cambios: `DiaE2ERobot` (selección de combos por mouse en popups GTK bajo Xvfb sin WM, pegado con Ctrl+V para acentos, reintento de texto y de celdas, y re-deducción de fila por intento tras refrescos), `BootstrapInicialE2EVirtual` (reintento del primer clic con aviso tolerado) y fase-1 de tasa que admite la ausencia del diálogo cuando la tasa sembrada ya coincide; fase de pagos que ahora entra al combo del editor de celda.

| Comando | Evidencia | Resultado |
|---|---|---|
| `./generar_bd_test.sh` (run 4) | `evidencias/produccion/2026-10-22/generar-bd-test.log` | **PASS** — `GENERACION_BD_TEST_OK`, `SEED_PLATOS_OK: 108 (18x6)`, 349 INSERTs, `368 líneas` en `BD-test.sql`, cierre vacío `BD_INICIALIZADA_OK`. Normalización aplicada: salas `(1,'Sala Principal',4,'SALON')` + GRANT DROP en `db/e2e/002-privilegios.sql` (requeridos por arneses test/integration). |
| `./cargar_bd_test.sh` | `evidencias/produccion/2026-10-22/cargar-bd-test.log` | **PASS** — `BD_TEST_OK` + `BD_TEST_CARGADA_OK` (109 platos, config 40/16, salas id=1, mapeos sin huérfanos); luego `./inicializar_bd_test.sh` dejó `BD_INICIALIZADA_OK` (24 tablas, 0 filas). |
| `./e2e_bootstrap_virtual.sh` (E2E_BOOTSTRAP_FULL_DAY=NO) | `evidencias/produccion/2026-10-22/e2e/bootstrap.log` | **PASS** — `BOOTSTRAP_E2E_OK`, `SEED_BASE_OK`, `E2E_OK`, `E2E_PEDIDO_RAPIDO_OK`. |
| `./e2e_dia_completo_virtual.sh` | `evidencias/produccion/2026-10-22/e2e/dia-fiscal-default.log` | **PASS** — `E2E_OK` día fiscal completo por UI: 8 facturas, 1 anulación, 0 pendientes, Bs 5783.60 conciliado en la UI. |
| `E2E_SEED_PLATOS=108 ./e2e_dia_completo_virtual.sh` | `evidencias/produccion/2026-10-22/e2e/dia-fiscal-seed108.log` | **PASS** — `SEED_PLATOS_OK: 108 (18x6)` + `E2E_OK` del día fiscal. |
| `E2E_SEED_PLATOS=108 ./e2e_dia_completo_virtual.sh Vista.Platos70IvaMonedaE2EVirtual` | `evidencias/produccion/2026-10-22/e2e/platos70.log` | **PASS** — `SEED_PLATOS_OK: 108` + `E2E70_OK` (70/70 creados y verificados por UI). |
| `ant test` (overrides 3307 + fixture cargada) | `evidencias/produccion/2026-10-22/ant-test.log` | **PASS** — 125 suites, **679 tests, 0 fallos, 0 errores, 31 omitidos**. |
| `ant integration-test` | `evidencias/produccion/2026-10-22/ant-integration-test.log` | **PASS** — BUILD SUCCESSFUL (compose propio levantado y bajado). |
| JUnitCore: BDTestSqlGateTest + E2ESinSiembraTest + EsquemaE2ETest | (consola; código en `test/infraestructura/`) | **PASS** — 8 tests OK (gates: FK sin desactivar, E2E sin siembra por BD, esquema issues válido). |
| `ultimo-fallo-con-diagnostico.log` | `evidencias/produccion/2026-10-22/e2e/ultimo-fallo-con-diagnostico.log` | **INFO** — último intento rojo previo al fix (fila vista↔modelo tras el refresco); sirve de rastreo del defecto de selección con filtros. |

snapshot 20/10/2026 13:50

## Resumen de Ventas en el Dashboard (diseño aprobado por el operador, implementado)

Sección nueva "Resumen de Ventas" en el panel Dashboard del 49200 (franja bajo las tarjetas KPI): combo de período `Hoy · Ayer · Últimos 7 días · Este mes · Personalizado` (2 spinners de fecha solo en Personalizado) + `Ver` + `PDF`; dos tarjetas (VENTAS DEL PERÍODO USD en verde / Bs en azul con "Tasa efectiva: N") y tabla `Método | Pagos | USD | Bs | %` con fila TOTAL fija.

| Pieza | Detalle | Verificación |
|---|---|---|
| `CierreCajaDao.consultarResumenVentas(desde,hasta,usuario,config)` | Solo lectura; reutiliza fórmulas monetarias §1/§3 de `consultarCierre` con `DATE(COALESCE(cerrada_en,fecha)) BETWEEN ? AND ?` (REPEATABLE_READ, union de pedidos legados, tasa efectiva del período = ΣBs/ΣUSD escala 4); no toca `cierres_caja` ni correlativos | IT en BD scratch (`evidencias/produccion/2026-10-20/resumen-ventas-it-test.log`): Σ desglose == cabecera USD y Bs, anulados excluidos, 4×35.50 exacto, ticket 35.50, rango vacío → ceros, rango invertido rechazado — BUILD SUCCESSFUL |
| Enum `TipoCierre.RESUMEN("RESUMEN DE VENTAS")` | switches del PDF explícitos (case TOTAL / RESUMEN / default); Pedido resources no afectados | `ant compile` exit 0; 5 suites targeted de PDF/Servicio en verde (`CierreCajaTest`, `CierreCajaExhaustivoTest`, `ConciliacionEfectivoCierreTest`, `AdversarialFallbacksFiscalesTest`, `CierreCajaYPrevisualizacionVistaTest`) |
| `GeneradorPdfCierre` modo RESUMEN | prefijo `resumen_…pdf`, "Período:", fila Pendientes oculta, tabla fiscal recortada a TOTAL GENERAL, "Tasa Efectiva del Período", pie `*** RESUMEN INFORMATIVO - NO FISCAL ***`; X/Z inalterados | misma batería targeted |
| `CierreCajaServicio.obtenerResumenVentas` / `generarResumenVentas` | datos para la tarjeta + PDF con canal de salida estándar (`procesadorSalida.procesar`), sin `guardarCierre` | targeted suite |
| UI en `Sistema.initDashboardYClientes` + `ResumenVentasSwingWorker` | guard `versionResumenVentas` (patrón dashboard), spinners `yyyy-MM-dd`, `cargarResumenVentas()` público, PDF exige resumen en pantalla | compile exit 0 |
| Artefacto regenerado | `./generar_restaurante_ejecutable.sh` sobre `97cf685` (el script exige árbol limpio) | log `/tmp/opencode/regen_resumen.log`; SHA-256 JAR `78cac8ad35f82923adda1082e311ecb647db3af58d34295d90ba9bc67325baef`; ZIP `22baf3dc18861500bb8184d8843c41ea2067e0fd80eefecfd9fa14e029e60edb` |

Spec reutilizable: `docs/superpowers/specs/2026-10-20-resumen-ventas-dashboard.md` (`dea4dba`). Flags aprendidos: el guard `-unit-db-safety` exige neutralizar `.env` con `-DDB_URL/-DDB_NAME/-DMYSQL_DATABASE` en CLI y las sysprops del JVM entran como `-Dtest-sys-prop.*`; clase entera → `test-single`, un método → `test-single-method` con `-Dtest.class` + `-Djavac.includes`.

snapshot 20/10/2026 13:35

## Contraste Modo C v2: texto negro pedido por el operador + estado deshabilitado legible (causa raíz hallada)

**Causa raíz confirmada en pantalla**: los botones inferiores del split aparecían con "letra fantasma" porque los tres (Pre-Factura, Cortesía, No Pagó) quedan **deshabilitados** cuando no hay mesa con pedido seleccionada, y el LaF pinta el texto deshabilitado con el gris claro del tema sobre el fondo de color (aún con `setForeground` fijado). El v1 (blanco/negro por luminancia) solo afectaba el texto habilitado, por eso el operador "no veía los cambios".

| Cambio | Detalle | Verificación |
|---|---|---|
| `BotonSolidoUI.paintText` propio | Pinta SIEMPRE con `b.getForeground()` (nunca el gris del tema); en deshabilitado atenuante con `AlphaComposite` 0.45 en vez de cambiar de color. Aplica a toda la app (todos los botones sólidos) | Tira de pantalla en vivo `evidencias/produccion/2026-10-20/contraste-modoc-negro-strip.png` |
| Texto negro a petición del operador | `btnPreFactura` (verde 39,174,96), `btnCortesia` (morado 142,68,173) y `btnFuga`/No Pagó (rojo 192,57,43) usan `new Color(0,0,0)` de forma determinista | Mismo recorte: los 3 legibles en negro |
| Artefacto regenerado | `./empaquetar_produccion.sh` sobre `878b59f` | `evidencias/produccion/2026-10-20/paquete-produccion-contraste-negro.log`; SHA-256 zip `ef4be2b0538b9161aa02d7a9c82866ad423177b05b4cdbbb46355be4cccd1765` |

App del 49200 reiniciada con jar `276c8137…` (unidad `restaurante-app` activa, pid 1374544); verificación visual hecha en el display real del operador tras iniciar sesión `admin`.

snapshot 20/10/2026 13:05

## Contraste de texto normalizado en los botones inferiores de Atn. directa (Modo C)

| # | Cambio | Razón | Verificación |
|---|---|---|---|
| 1 | `Sistema.colorTextoContraste(Color fondo)`: devuelve blanco o negro según cuál rinde más contraste (luminancia WCAG, umbral por comparación directa, no 50 % fijo) | Reutilizable; el LaF nativo ignoraba el `setBackground` de los JButton directos | `ant compile` exit 0 |
| 2 | `PanelAtencionMesasSplit`: los 5 botones inferiores (Enviar a Cocina, Pre-Factura, Cortesía, No Pagó, Cobrar) ahora usan `Sistema.botonSolido(boton, fondo, colorTextoContraste(fondo))` | Pre-Factura (verde 39,174,96) tenía **2.9:1** con texto blanco (fallos WCAG AA, el texto era incomprensible); el patrón sólido garantiza fondo pintado + hover ≥4.5:1 | E2E de tablas del split → `evidencias/produccion/2026-10-20/contraste-modoc-regresion-tablas.log` (`E2E TABLAS ID: TODO OK`, exit 0) |

Resultado por botón: Enviar a Cocina/Cortesía/No Pagó/Cobrar conservan blanco (contraste 5.0–5.9; No Pagó 5.5); **Pre-Factura pasa a texto negro** (7.3:1 sobre su verde). La app del 49200 se reinició con el jar `e522d5e9d5034fef…` (unidad `restaurante-app` activa, pid 1360691; MySQL 3306 healthy); el log del día no registra SEVERE nuevos tras el arranque. Artefacto regenerado sobre `8a57384`: SHA-256 zip `ce1548da4ee6b385e2ce6e072eea1c44b66202467e6fce25e6d095e52731c054` (`evidencias/produccion/2026-10-20/paquete-produccion-contraste-modoc.log`).

snapshot 20/10/2026 12:58

## Hallazgos H1 y H2 cerrados con re-ejecución aislada

| # | Defecto | Corrección | Verificación |
|---|---|---|---|
| H1 | `tblTemPlatos` (toma de pedido): la columna Precio (min/pref 110px) quedaba en 158px y no cubría el precio más largo (172px) | `Sistema.java` (`configurarColumnasProporcionales(tblTemPlatos…)`): min y preferred de la columna Precio a **175px** | `./e2e_dia_completo_virtual.sh Vista.PreciosVisiblesComponenteTest` → `evidencias/produccion/2026-10-20/h1-aislado-PreciosVisibles.log`: `ancho 190px cubre el precio más largo (172px)`, `E2E PRECIOS: TODO OK`, exit 0 |
| H2 | El E2E esperaba `txtIdHistorialPedido=id` tras hacer clic en un pedido **PENDIENTE** del historial; el app deja ese campo **vacío por diseño** (para FINALIZADO, que es cuando se usa para reimpresión/PDF; el id del pendiente vive en `txtIdPedido`) y `cargarPedidoEnPantalla` salta a la pestaña de caja, lo que dejaba la tabla fuera de pantalla para el clic siguiente | El test ahora valida la semántica real: (a) PENDIENTE → carga en caja con `txtIdPedido` y `txtIdHistorialPedido` vacío; (b) subcaso nuevo con pedido **FINALIZADO** → `txtIdHistorialPedido` fija el id y `btnPdfPedido` se habilita; (c) tras la carga que cambia de pestaña, el test vuelve a seleccionar la pestaña de historial y re-aplica el sorter antes del segundo clic | `./e2e_dia_completo_virtual.sh Vista.TablasIdOcultoComponenteTest` → `evidencias/produccion/2026-10-20/h2-aislado-TablasIdOculto.log`: los tres subcasos en OK, `E2E TABLAS ID: TODO OK`, exit 0 |

Quedan sin hallazgos abiertos locales: H3 (resuelto antes), H4 y H5 siguen como notas de proceso (no defectos de producto). El jar desplegado en `Restaurante_Ejecutable/` (sha256 `0430a3de…`) incluye H1; la instancia 49200 activa tomará el cambio al próximo arranque. Artefacto de distribución regenerado sobre `642669f`: sha256 zip `3b79ab8e5e7498b4c6de7002e5f7f383997bf2292e0ec47f7bd8ea9078caa496` (`evidencias/produccion/2026-10-20/paquete-produccion-post-h1h2.log`).

snapshot 20/10/2026 10:57

## Hallazgo H3 reparado (catálogo de platos) + incidente de compose registrado

| Ítem | Verificación | Estado |
|---|---|---|
| **H3** — `test/aceptacion/PruebaRedTeamIvaIncluidoPlatosTest` borraba de `plato_categorias` (tabla inexistente, columna `id_plato` inexistente; SQLSTATE 42S02/1146) | La limpieza ahora borra la matriz real `plato_categoria` por `nombre_clave = LOWER(TRIM(nombre))` (join genuino `PlatosDao`), probado en BD desechable 3307 | **RESUELTO** — `evidencias/produccion/2026-10-20/h3-test-single-iva-incluido.log` + `build/test/results/TEST-aceptacion.PruebaRedTeamIvaIncluidoPlatosTest.xml` (`tests=1 failures=0 errors=0`, timestamp 09:35:46). Cero residuos '%RT %' en producción ni en scratch. Convención documentada: para un test unitario aislado vía `test-single` hay que pasar `-Dtest.includes` y `-Djavac.includes`, además de `-DDB_NAME/-DMYSQL_*/-DDB_URL/-Dtest-sys-prop.*` (precedencia Ant: el primer valor asignado gana)). |
| **H1** (`PreciosVisiblesComponenteTest`, preexistente) y **H2** (`TablasIdOcultoComponenteTest`, preexistente), **H4** (stack compartido de E2E), **H5** (convención documentada) | Documentados el 19/10 | Pendientes; H3 era la única higiene de catálogo | 

### Incidente registrado (20/10 09:31): reconfiguración accidental del contenedor de producción por colisión de proyecto Compose

Al lanzar la BD de integración con `docker compose -f docker-compose.integration.yml up -d` SIN `-p`, el proyecto por defecto (`Restaurante`) coincidió con el proyecto de producción: Compose reconfiguró el contenedor `restaurante-db-1` con el config de integración (port 3307, volumen de integración recién sembrado) durante ~1 min (09:30:52–09:31:18), interrumpió conexiones de la app del 49200 (CommunicationsException, "No se pudo revalidar el rol de la sesión" hasta 09:31:50) y ambos servicios fueron restablecidos manualmente. Los datos de producción NUNCA se tocaron (el volumen real `restaurante_restaurante_mysql_data` siguió íntegro; estado verificado después del restablecimiento: 24 tablas, pedido 13, usuarios Roger+Administrador, v15, restaurante_control=OPERATIVO). El pool de `ProveedorConexionJdbc` valida y descarta conexiones muertas al pedir depósito (`isValid` línea ~287), así que la app se reestableció sola sin reinicio.

**Regla derivada para agentes (prohibición):** los atajos `docker compose` sin `-p <proyecto>` están PROHIBIDOS SIEMPRE en este repo (producción usa `-p restaurante` por defecto del directorio). Toda BD temporizador debe levantarse con proyecto distinto: `docker compose -f docker-compose.integration.yml -p restaurante-integra up -d` (y bajarse con `-p restaurante-integra down --volumes`). El volumen accidental `restaurante_restaurante_integration_mysql_data` fue eliminado a mano tras el restablecimiento; la producción quedó `restaurante_restaurante_mysql_data` única.

## Artefacto de producción regenerado sobre el commit 6e239fd

| Comando | Archivo de salida | Estado y alcance |
|---|---|---|
| `./empaquetar_produccion.sh` (árbol limpio, guarda vigente) | `evidencias/produccion/2026-10-20/paquete-produccion-empaquetado.log` | **PASS** — BUILD SUCCESSFUL, staging con lista blanca, prohibidos ausentes; `../../Restaurante_2026_Produccion.zip` (10 MB) + `.sha256` propio; SHA-256 zip `b183d26084886b86c7e6470dc0c5bb6b31797aedd18044cc19428b2118333a48` (verificado contra `.sha256`), jar dentro del zip `c7294b993ade22aad056adbf81e2c754a72f5edbff85290b4cbb6604d474e074`. |
| `diff -rq` de `dist/Restaurante.jar` vs `Restaurante_Ejecutable/Restaurante.jar` extraídos | salida en consola de sesión | **CONTENIDO IDÉNTICO** — 0 diferencias en todas las entradas (manifest y listado de entradas iguales); el jar desplegado (`a12fb108…`) y el jar del release (`c7294b99…`) difieren solo en metadatos de timestamp de zip, no en bytes de contenido. Criterio de equivalencia usado: contenido extraído idéntico. |

Completado el reempaquetado, los gates restantes requieren entorno externo: instalación Windows un-clic (`.bat`/`.ps1` nativos), impresora/tickera física y verificación de revocación de sesión en caliente con operador. La app del puerto 49200 quedó activa (pid 1261593, unidad `restaurante-app`) con commit `6e239fd`.

snapshot 20/10/2026 10:30

## Restauración en BD secundaria heterogénea (drill S1–S7) — PASS integral

`drill_restauracion_bd_secundaria.sh` (nuevo): leva **tres servidores MySQL efímeros y heterogéneos** — origen (3307, TZ UTC, runtime con privilegios mínimos), secundaria (3308, TZ America/Caracas, admin) y verificador (3309, admin) — y ejecuta la ruta CLI completa del operador en cada servidor distinto: `--migrate-db` + siembra del pedido 13 REAL de producción (mysqldump read-only), `--respaldar`, `--verificar-respaldo` en schema temporal DE LA SECUNDARIA (servidor distinto), `--restaurar-respaldo` completo (con respaldo previo de emergencia), conciliación financiera y conteo por tabla contra el origen, invariantes v15/OPERATIVO, CHECK TABLE EXTENDED, verificación en tercer servidor, fail-closed (corrupto/truncado rechazados sin tocar la secundaria) y verificación del respaldo REAL de producción en el servidor 3309 con la HMAC real de producción.

| Comando | Archivo de salida | Estado y alcance |
|---|---|---|
| `./drill_restauracion_bd_secundaria.sh` (origen 3307 / secundaria 3308 / verificador 3309) | `evidencias/produccion/2026-10-20/secundaria-drill-completo.log` (+ `secundaria-*.log`) | **PASS** — `RESTAURACION_SECUNDARIA_OK`: respaldo en origen (46,413 B), VERIFICACION_OK en schema temporal del servidor 3308, RESTAURACION_OK con respaldo previo de emergencia, conciliación idéntica con datos reales (total 132.24 / bs 5,924.35 / pagos 5,924.35+132.24), todas las tablas base con conteo idéntico, v15 APLICADA en ambos, restaurante_control=OPERATIVO, CHECK TABLE EXTENDED limpio, VERIFICACION_OK en el tercer servidor, corrupto y truncado rechazados sin alterar la secundaria, respaldo REAL de producción verificado e importado en el servidor distinto (firma HMAC de producción). |
| `--respaldar` de producción con jar actual (solo lectura) | `evidencias/produccion/2026-10-20/secundaria-respaldo-produccion-fresco.log` + `respaldos-produccion/respaldo_restaurante_20261020_102249_986_097614a2…sql(+.manifest+.sha256)` (69,239 B, sha256 cb8972aeb9ebd39e…) | **ÉXITO** — RESPALDO_OK: genera respaldo firmado con checksum desde la BD viva sin modificarla; este triplete es el que se verificó en el servidor distinto. |

Hallazgos y decisiones del drill:

1. **El respaldo histórico de producción del 19/10 20:40 (69097 B) NO lleva sección de checksum**: fue creado por el despliegue previo al jar con checksum (verificado `VERIFICACION_FALLIDA: El respaldo no tiene checksum`). Por eso se generó y verificó un respaldo fresco con el jar actual → `VERIFICACION_OK` en el servidor 3309. El siguiente respaldo automático nocturno de producción, ya con el jar desplegado, quedará firmado y verificable.
2. **La comparación de contenido A↔B es exacta para todas las tablas base** (sin exclusiones: no hay tablas de artefactos; el diario de recuperación es archivo, no tabla). La siembra demostró además que la fila viva de producción (pedido 13 + detalle + pago + liquidación) sobrevive restaurada con referencias consistentes.
3. **La BD de producción quedó intocada**: el drill solo le lee (mysqldump y `--respaldar`). La app del puerto 49200 no fue诊 toda la sesión (está DETENIDA desde el 19/10 21:21 — falta reiniciarla con el usuario).
4. El levantamiento del esquema sobre BD.sql determina `schema_migrations` → una sola fila `15 APLICADA` (sonda en MySQL efímero ad-hoc con `--migrate-db` + confirmación igual en la BD viva de producción) — por eso el criterio de igualdad A↔B usa v15.

Archivos sin commit (pendiente de OK del usuario): `drill_restauracion_bd_secundaria.sh`, evidencias `evidencias/produccion/2026-10-20/*`, y los cambios de bootstrap/admin-imborrable del 19/10.

snapshot 19/10/2026 23:30

## Autoreparación de esquema sobre BD de cliente (redteam) — verificada y 3 correcciones

Se añadió `redteam_autoreparacion_bd.sh`: siembra una BD con datos de negocio, degrada el esquema como una instalación vieja (faltan tablas v5–v15, columnas, índices, FKs y checks; `schema_migrations` en v13; columna legado `config.modo_atencion`), ejecuta la ruta real del cliente (`Restaurante --migrate-db`, la misma de `actualizar_bd.bat`) y verifica reparación completa, cero pérdida de datos, respaldo pre-migración verificable, idempotencia, reanudación de migración interrumpida y guardas fail-closed.

| Comando | Archivo de salida | Estado y alcance |
|---|---|---|
| `./redteam_autoreparacion_bd.sh` (MySQL efímero 3307) | `evidencias/produccion/2026-10-19/redteam-autoreparacion-2020.log` | **PASS** — `REDTEAM_AUTOREPARACION_OK`: 6 tablas y 12 artefactos (columnas/índices/FKs/checks) reparados, v15 APLICADA, 242 filas intactas, respaldo verificado, segunda corrida sin cambios ni respaldos nuevos, reanudación EN_CURSO OK, BD vacía y BD futura rechazadas sin escribir. |
| `DISPLAY=:98 ant -q test` (props de BD aislada 3307) | `evidencias/produccion/2026-10-19/unit-post-autoreparacion-2023.log` | **PASS** — 121 suites, 671 tests, 0 fallos, 0 errores, 31 omitidos. |
| `DISPLAY=:98 ant -q integration-test` | `evidencias/produccion/2026-10-19/integration-post-autoreparacion-2028.log` | **PASS** — 38 suites, 174 tests, 0 fallos, 0 errores. |
| `./drill_restauracion_bd.sh` (con el fix de catálogo) | `evidencias/produccion/2026-10-19/drill-restauracion-post-autoreparacion-2021.log` | **PASS** — `DRILL_RESTAURACION_OK`: respaldo, verificación temporal, mutación, restauración, reversión y rechazo por conciliación. |

### Correcciones derivadas del redteam (sin commit)

| # | Defecto encontrado | Corrección |
|---|---|---|
| R1 | `chk_login_intentos_no_negativos` no se reafirmaba si `login_intentos` ya existía (solo se creaba en el `CREATE TABLE` inicial): una BD degradada quedaba sin el CHECK | `MigradorEsquemaJdbc.asegurarTablaIntentosLogin` reafirma el CHECK con `asegurarCheck` |
| R2 | `validarContenidoVivo` usaba `Connection.getCatalog()`, que queda obsoleto tras `USE` (Connector/J no lo actualiza): la verificación en schema temporal inspeccionaba la BD viva y rechazaba respaldos válidos cuando el esquema vivo difiere del respaldado (caso real: respaldo pre-migración verificado después de migrar) | `ServicioRespaldoBaseDatos.catalogoEfectivo(con)` resuelve el schema real con `SELECT DATABASE()` |
| R3 | `--migrate-db` fallaba en silencio ante rechazos fail-closed (mensaje solo en el log de archivo); `actualizar_bd.bat` no mostraba la causa al operador | La rama CLI imprime `MIGRACION_FALLIDA: <causa raíz>` en stderr y sale 1 |

Comportamiento intencional documentado (no defecto): la migración normaliza `configuracion_sistema.imprimir_logo_ticket` de `'true'` a `'1'` (parámetro migrado desde `config`; `LoginDao` los trata equivalentes). El redteam lo permite explícitamente y lo verifica tras migrar.

La instancia operativa del puerto 49200 no se reinició ni se tocó. Los tres archivos corregidos y el script del redteam están sin commit; `Restaurante_Ejecutable/` aún no se regeneró con estos cambios.

snapshot 19/10/2026 22:10

## Campaña final de pruebas (etapas A–D cerradas)

Etapas A (matriz de permisos + revocación de sesión), B (conciliación financiera + CLI de respaldo/restauración + drill), C (empaquetado reproducible con guarda de árbol limpio e inventario SHA-256) y D (espera acotada del spooler) quedaron implementadas y pusheadas: `6037cc3`, `2f1a962`, `56bc5a8`, `3c68835`, `e9e6335`.

| Comando | Archivo de salida | Estado y alcance |
|---|---|---|
| `ant -q integration-test` | `evidencias/produccion/2026-10-19/final-integration-test.log` | **PASS** — 12 suites, 66 tests, 0 fallos, 0 errores, 1 omitido. |
| `DISPLAY=:98 ant -q test` con `-Dtest-sys-prop.MYSQL_PORT=3307 -Dtest-sys-prop.MYSQL_DATABASE=restaurante_test -Dtest-sys-prop.MYSQL_USER=restaurante_test_app -Dtest-sys-prop.MYSQL_PASSWORD=…` sobre MySQL efímero 3307 | `evidencias/produccion/2026-10-19/final-unit-test-authoritative.log` | **PASS** — 121 suites, 671 tests, 0 fallos, 0 errores, 31 omitidos. |
| `RUN_DESTRUCTIVE_DB_TESTS=YES MYSQL_DATABASE=restaurante_test MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 ant -q test-single -Djavac.includes=Servicio/ServicioRespaldoBaseDatosTest.java …` | `evidencias/produccion/2026-10-19/final-respaldo-destructivo.log` | **PASS** — 23/23 del respaldo/restauración en schema desechable (incluye tablas financieras). |
| `./drill_restauracion_bd.sh` | `/tmp/redteam/B-drill.log` | **PASS** — `DRILL_RESTAURACION_OK`: respaldo, verificación en schema temporal, mutación, restauración, reversión y rechazo de respaldo descuadrado por conciliación. |
| `./e2e_dia_completo_virtual.sh` | `evidencias/produccion/2026-10-19/final-e2e-dia-completo.log` | **PASS** — `E2E_OK`, 8 facturas, 1 anulación, 0 pendientes, Bs 5783.60 conciliados, cierre Z aceptado por spooler simulado. |
| `E2E_BOOTSTRAP_FULL_DAY=SI ./e2e_bootstrap_virtual.sh` | `evidencias/produccion/2026-10-19/final-e2e-bootstrap-full-day.log` | **PASS** — `E2E_OK` con bootstrap limpio y privilegios mínimos de runtime. |
| `./e2e_virtual.sh <clase>` (15 clases UI en stack compartido) | `evidencias/produccion/2026-10-19/final-e2e-Vista_*.log`, `final-e2e-aceptacion_*.log` | **12 PASS / 3 FAIL** — fallos en `Vista.RedimensionarColumnasJTableE2EVirtual`, `Vista.PreciosVisiblesComponenteTest`, `Vista.TablasIdOcultoComponenteTest`. |
| Repetición aislada en stack limpio (`./e2e_dia_completo_virtual.sh <clase>`) | `evidencias/produccion/2026-10-19/aislado-e2e-*.log` | **1 PASS / 2 FAIL** — `RedimensionarColumnasJTableE2EVirtual` pasa aislado (era contaminación); `PreciosVisiblesComponenteTest` y `TablasIdOcultoComponenteTest` fallan también aislados. |
| Repetición de los 2 fallos en `git worktree HEAD~5` (antes de A–D) | `/tmp/prevbc/log-Vista_Precios*.log`, `/tmp/prevbc/log-Vista_TablasId*.log` | **FAIL** — ambos fallos son preexistentes, no los introdujo A–D. |

### Hallazgos abiertos (con evidencia)

| # | Hallazgo | Evidencia | Severidad |
|---|---|---|---|
| H1 | `Vista.PreciosVisiblesComponenteTest`: la columna `Precio` de `tblTemPlatos` no cubre el precio más largo (158 px < 172 px) | `aislado-e2e-Vista_PreciosVisiblesComponenteTest.log:80` | Media (cosmético, preexistente) |
| H2 | `Vista.TablasIdOcultoComponenteTest`: al hacer clic en una fila de la tabla ordenada no se carga el pedido (`sel=0`, `txtHist` vacío) | `aislado-e2e-Vista_TablasIdOcultoComponenteTest.log:69` | Media (funcional, preexistente) |
| H3 | `test/aceptacion/PruebaRedTeamIvaIncluidoPlatosTest.java:72` borra de `plato_categorias` (tabla inexistente, columna `id_plato` inexistente); la tabla real es `plato_categoria` por `nombre_clave` | `final-unit-test-authoritative.log:1634+` (SQLSTATE 42S02, código 1146) | Baja (hixiene de test; contamina la BD compartida) |
| H4 | El stack compartido entre clases E2E contamina estado dependiente de orden/anchuras guardadas en config | `final-e2e` vs `aislado-e2e` de `RedimensionarColumnasJTableE2EVirtual` | Baja (proceso de prueba) |
| H5 | `ant test` con solo `-D` de Ant no aísla la BD: los tests sin guard de escritura golpean la BD viva y fallan; hay que usar `-Dtest-sys-prop.*` y `RUN_DESTRUCTIVE_DB_TESTS=YES` para la suite destructiva | `evidencias/…/final-unit-test-isolated.log` (5 fallos por `DB_URL` forzado), `final-unit-test-isolated2.log` (6 fallos por `MYSQL_HOST` forzado) | Baja (documentación de comando) |

Sin hallazgos en la verificación de esquema: `information_schema` de la BD operativa (`restaurante`, 24 tablas, migraciones hasta v15) coincide con `BD.sql`, y `actualizar_bd.sql` == `db/migrations/actualizar_bd.sql`. La instancia del puerto 49200 no se reinició ni se desplegó.

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

# Plan de preparación para producción — Restaurante 2026

> **Para agentes de implementación:** ejecutar las tareas en orden y respetar las restricciones globales. Cada fase debe quedar revisable y pasar su gate antes de empezar la siguiente. No desplegar ni reiniciar la instancia de servicio como parte de este plan.  
> **Validado por Red Team (19/10/2026):** Este plan incorpora las enmiendas críticas del Red Team para evitar bloqueos por privilegios MySQL, congelamientos de EDT, incompatibilidades de instaladores Windows, pérdida de respaldos por rotación HMAC, fallos en LAN privada y colapso de suites E2E.

**Objetivo:** cerrar los riesgos de secretos, cuentas iniciales, impresión, recuperación, autorización y distribución antes de declarar Restaurante 2026 apto para una nueva instalación productiva.

**Arquitectura:** mantener Java 21, Swing, JDBC y Ant. Provisionar secretos fuera de los artefactos; crear un flujo de bootstrap seguro para la cuenta Administrador (con pre-siembra aislada en BD de pruebas); hacer explícitos los resultados de impresión en spooler sin bloquear el EDT; y validar migración/restauración en una BD desechable. Mantener la autorización centralizada en servicios/controladores y la composición de dependencias en `restaurante.Restaurante`.

**Stack:** Java 21, Apache Ant, MySQL 8.4, Swing, JDBC, Docker Compose, JUnit 4, Xvfb/xdotool para E2E.

## Estado de ejecución (actualizado 19/10/2026)

Esta sección registra el avance del trabajo en curso. Los cambios indicados como implementados todavía no equivalen a una fase aprobada: cada fase conserva su gate y sus casillas pendientes hasta completar la validación integral. La línea base de la sección siguiente corresponde a una ejecución anterior a estos cambios y no demuestra el estado actual.

| Fase | Estado | Ejecutado hasta ahora | Falta para cerrar la fase |
|---|---|---|---|
| 0 — Línea base y separación | **Implementación aislada; gate parcial** | `feature/persistencia-columnas-jtable` está rebasada sobre `main`; la refactorización UI-only se confirmó en `40688ef` y se publicó en `origin/feature/persistencia-columnas-jtable`. `RedimensionarColumnasJTableE2EVirtual` pasó por UI en Xvfb: redimensionó y recuperó visualmente 14 tablas tras reabrir sesión. | Integrar la rama solo después de cerrar los gates pendientes; hacer aceptación final desde workspace limpio. |
| 1 — Secretos y empaquetado | **Implementación parcial; Linux validado** | Cambios en clasificación TLS LAN/WAN, rotación HMAC, aprovisionamiento de secretos Windows y listas permitidas para paquetes. Los instaladores configuran `restaurante_app` con privilegios acotados; `actualizar_bd.bat` reporta `.env` ausente. Pasaron pruebas de instaladores. Se generaron e inspeccionaron los dos ZIP Linux: contienen JAR, `.env.example` y seis librerías; no incluyen `.env`, claves, volcados comprimidos ni logs. | Ejecutar/validar los scripts PowerShell en Windows y probar ACL/instalación; validar contra un MySQL nuevo con cuenta runtime; cubrir scanner de ZIP en casos negativos. PowerShell no está disponible en este entorno. |
| 2 — Bootstrap | **Bootstrap UI probado; gate de seguridad parcial** | Instalación desde `BD.sql` completó bootstrap por UI con el usuario runtime restringido a `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES` en `restaurante_test`; las credenciales demo fueron rechazadas y la cuenta nueva pudo iniciar sesión. `ant integration-test` pasó **66 pruebas, 0 fallos, 0 errores y 1 omitida**, incluyendo concurrencia de bootstrap, protección del último Administrador y espera del lock MySQL compartido desde otra conexión. | Revisar cambio de rol del último Administrador y escenarios de concurrencia entre instancias para mutaciones de usuarios. |
| 3 — Impresión | **Validación local cerrada; falta aceptación física** | Resultado tipado diferencia spooler, visor y fallo; el cobro se conserva si falla la salida; reimpresiones corren fuera del EDT y la reimpresión de marcha toma snapshot exacto. Comandas v15 guardan un diario inmutable; los flujos clásico y Split imprimen solo la marcha persistida. Pruebas PDF y E2E validan incrementos, ajustes y ancho de 80 mm. Los PDF de facturas/reimpresiones se conservan en `facturas/<AAAA-MM>` con versiones timestamp, por lo que no se borran con una limpieza temporal; el runner de E2E elimina su directorio efímero al terminar. | Probar escalado y salida de papel con impresora/driver físico; `lpstat` no encuentra destinos configurados aquí. |
| 4 — Migración y recuperación | **Implementación parcial; recuperación HMAC probada** | Bootstrap conserva la cuenta Administrador personalizada al inicializar la clave faltante; `MigradorEsquemaJdbc` y los dos scripts SQL implementan la detección y la suite de integración actual pasó ambos escenarios. Se confirmó paridad byte a byte. La suite focalizada de respaldo/restauración pasó **22 pruebas, 0 fallos, 0 errores y 0 omitidas** en BD efímera: incluye restauración con clave HMAC previa, rechazo de alteraciones y entradas SQL firmadas pero fuera de la lista blanca. | Revisar idempotencia global y bloqueo de mantenimiento ante fallo DDL; ampliar la comparación de saldos/conteos de la recuperación. |
| 5 — Autorización | **Implementación parcial** | Matriz de permisos fail-closed, acceso de Administrador preservado, bloqueo informativo de Cocinero y exclusión del rol Cocinero al crear usuarios. Pruebas focalizadas de autorización pasaron. | Auditar autorización centralizada de todas las acciones sensibles y revocación de sesión en caliente; completar regresión por UI y demostrar que fallos de configuración deniegan operaciones protegidas. |
| 6 — Aislamiento de pruebas | **Gates de base verificados; suites locales aprobadas** | El guard de `build.xml` acepta `restaurante_test` y rechaza URL a `restaurante` y alias `DB_NAME=RESTAURANTE` (comparación sin distinción de mayúsculas); E2E exige URL, usuario y BD desechables. `ant -q test`: **657 pruebas, 0 fallos, 0 errores, 30 omitidas** en 118 suites. Último `ant -q integration-test`: **66 pruebas, 0 fallos, 0 errores y 1 omitida** en 12 suites. E2E UI-only reciente: bootstrap limpio, comandas parciales, pedido rápido, CRUD de usuarios y jornada completa pasaron en runners efímeros; la jornada completa pasó también con cuenta runtime restringida. | Repetir E2E de release que falten y completar aceptación física de impresora. |
| 7 — Ensayo de release | **Parcial; build y ZIP Linux validados previamente desde worktree limpio** | En worktree limpio `b6ded11`, `ant clean jar`, `generar_restaurante_ejecutable.sh` y `empaquetar_produccion.sh` terminaron correctamente. Ambos ZIP tienen 34 entradas, el JAR, `.env.example` y las seis librerías; scanner encontró cero secretos/archivos prohibidos. SHA-256: ejecutable `e071ad0611ccb9e4499fb2ba6557df291785b5cc0a88749e6a8e142f23cd72ef`; producción `3b45a1444ce1123a91a6d8a3a024c9145452cf9bcb6c16dd32c94fece9553240`. No se desplegó ni reinició la instancia. | Regenerar artefactos para el árbol actual; generar/inspeccionar ZIP Windows y validar ACL en Windows; restauración secundaria y aceptación con impresora física. PowerShell no está instalado aquí. |

### Evidencia de validación disponible

- En la actualización actual, `ant -q compile jar` y `ant -q compile-test` terminaron correctamente; la suite completa `ant -q test` pasó con **657 pruebas, 0 fallos, 0 errores y 30 omitidas** en 118 suites, usando `restaurante_test` por el puerto 3307.
- En un worktree limpio del commit `b6ded11`, `ant clean jar` compiló desde cero. Los dos empaquetadores Linux terminaron correctamente; los ZIP se inspeccionaron: 34 entradas cada uno, JAR, `.env.example`, seis librerías, cero secretos o archivos prohibidos. SHA-256 ejecutable `e071ad0611ccb9e4499fb2ba6557df291785b5cc0a88749e6a8e142f23cd72ef`; distribución `3b45a1444ce1123a91a6d8a3a024c9145452cf9bcb6c16dd32c94fece9553240`. PowerShell no está instalado, por lo que no se ejecutaron scripts Windows. No se copió ningún JAR a la instalación operativa ni se reinició el servicio.
- En una ejecución anterior, `ant -q integration-test` terminó contra la BD desechable en 3307 con **56 pruebas, 0 fallos, 0 errores y 1 omitida**. La ejecución más reciente, posterior a serializar mutaciones administrativas entre conexiones MySQL, pasó **66 pruebas, 0 fallos, 0 errores y 1 omitida** en 12 suites; incluye `mutacionAdministradorEsperaLockCompartidoDeOtraConexion`.
- La suite unitaria completa `ant test` terminó sin fallos ni errores en la BD efímera de integración (`restaurante_test`, puerto 3307): **289 pruebas, 0 fallos, 0 errores y 3 omitidas**. El primer intento fue bloqueado antes de ejecutar pruebas porque el guard detectó un alias de BD productiva en el entorno; se corrigieron explícitamente `DB_URL`, `DB_NAME` y `MYSQL_DATABASE` para la segunda ejecución. El contenedor/volumen temporal se eliminó al terminar.
- La suite focalizada de `ServicioRespaldoBaseDatosTest` pasó en una BD efímera con `RUN_DESTRUCTIVE_DB_TESTS=YES`: **22 pruebas, 0 fallos, 0 errores y 0 omitidas**. Se agregó restauración real de un respaldo firmado con `BACKUP_HMAC_KEY` previa tras rotar a una clave actual distinta; también se corrigieron las fixtures fabricadas para incluir HMAC válido, de modo que las pruebas alcancen las validaciones de lista blanca y estructura.
- El guard `unit-db-safety-check` pasó para `DB_NAME=restaurante_test`, rechazó URL `jdbc:mysql://127.0.0.1:3306/restaurante` y también rechazó `DB_NAME=RESTAURANTE`; se hicieron estas comprobaciones tras volver case-insensitive el bloqueo de aliases. El guard de `build.xml` sigue aceptando solo configuración desechable. `ConfiguracionAccesosServicioTest` pasó con `ant test-single`.
- En la actualización actual, `bash -n empaquetar_produccion.sh generar_restaurante_ejecutable.sh`, la comprobación BOM/CRLF de los tres `.ps1` modificados y `cmp -s actualizar_bd.sql db/migrations/actualizar_bd.sql` terminaron correctamente.
- `./e2e_dia_completo_virtual.sh` completó la jornada con `E2E_OK` en MySQL efímero, `Xvfb :99`, interacción solo por UI y spooler `lp` simulado; el primer intento reveló una aserción E2E desactualizada sobre el texto del cierre, que se alineó con el resultado visible “spooler” y pasó en la repetición.
- `./e2e_dia_completo_virtual.sh Vista.RedimensionarColumnasJTableE2EVirtual` terminó `E2E_OK`: verificó por UI el cambio y la recuperación visual de anchos en 14 tablas. El test fue actualizado para eliminar acceso JDBC/DAO directo y observar proporciones de columna en pantalla tras reabrir sesión.
- E2E adicionales en runners aislados, con interacción exclusivamente por UI y MySQL efímero: `Vista.AtencionDirectaCompletoE2EVirtual` (`E2E_ATENCION_DIRECTA_OK`), `Vista.UsuariosCrudE2E` (`E2E_USUARIOS_OK`), `Vista.PedidoRapidoE2EVirtual` (`E2E_PEDIDO_RAPIDO_OK`) y `Vista.OpcionCSplitE2EVirtual` (`E2E_OPCION_C_OK`). Cubren conmutación de modos, CRUD de usuarios, pedido/cobro/anulación rápida, apertura de mesa y liberación de comanda.
- `Vista.AtencionDirectaE2EVirtual` terminó `E2E_ATENCION_DIRECTA_OK`. `Vista.Platos70IvaMonedaE2EVirtual` se rehízo para iniciar sesión y crear una categoría de prueba por interfaz, luego crear y verificar 70 productos leyendo la tabla y ficha visibles; `./e2e_dia_completo_virtual.sh Vista.Platos70IvaMonedaE2EVirtual` terminó `E2E70_OK`. La BD efímera y el directorio de trabajo se retiraron con el wrapper.
- `ant -q integration-test` se repitió tras los cambios de respaldo y terminó `BUILD SUCCESSFUL`: **56 pruebas, 0 fallos, 0 errores y 1 omitida**.
- El arranque `java -Djava.awt.headless=true ... restaurante.Restaurante --migrate-db` terminó con “Migración de base de datos validada correctamente” dentro de un contenedor JRE 21 conectado solo a MySQL Compose efímero; los directorios mutables fueron `tmpfs`. No se utilizó ni tocó el puerto de la instancia de escritorio.
- Se generaron `Restaurante_Ejecutable.zip` y `/tmp/Restaurante_2026_Produccion.zip`. Inspección automatizada de ambos: **34 entradas**, JAR y `.env.example` presentes, las seis librerías presentes, cero `.env`/secretos/volcados `.sql.gz`/logs.
- El primer intento del empaquetador Linux falló porque `zip` no acepta el archivo vacío que crea `mktemp`; ambos scripts se corrigieron. El empaquetador de producción también se corrigió para cargar SDKMAN con `nounset` temporalmente desactivado. La segunda generación y la compilación limpia del JAR terminaron correctamente.
- Pasaron pruebas focalizadas de `ProveedorConexionJdbcTest`, `InstaladoresSeguridadTest`, `ServicioBootstrapInicialTest`, `ConfiguracionAccesosServicioTest`, `PedidoPdfServicioTest`, `ServicioImpresionTicketTest`, `FinalizarPedidoSwingWorkerTest` y `CierreCajaExhaustivoTest`.
- La prueba geométrica `pdfTicketDetalleMantieneMontosDentroDelAnchoImprimible80mm` pasó con precio en Bs. `499999999999.99` (escala/precisión máxima `DECIMAL(14,2)`): comprueba ancho PDF de 227 pt (≈80 mm), texto de montos dentro de sus columnas y total dentro del margen derecho imprimible de 10 pt. Se probó mediante `ant test-single-method` en la suite `GeneradorPdfPedidoTest`; el ajuste compiló con `ant -q compile jar`. Esto valida el PDF generado, no el escalado aplicado por un controlador físico.
- Red Team encontró inicialmente que 12 nombres de 100 caracteres fragmentaban el ticket en 3 páginas y que `Cant.` y separadores decorativos se partían. Se ajustó el formato: nombre/cantidad en una línea con recorte por ancho real de fuente; precios USD/Bs debajo; separadores reducidos al ancho. Verificación posterior: `ant test-single` para `GeneradorPdfPedidoTest` pasó; las pruebas nuevas cubren un nombre de 200 caracteres y 12 nombres máximos, ambos en una página. El PDF adversarial quedó en **1 página de 227 x 480 pt**; `pdftotext -layout` confirmó nombre con `...` y montos debajo, y la revisión del render no mostró desbordamientos. La prueba de montos grandes mantiene el control geométrico dentro del margen de 10 pt. No se ha probado con impresora física: `lpstat` no encuentra impresoras ni destino predeterminado.
- Para comandas parciales se añadió la migración v15 y sus snapshots inmutables. `ant -q integration-test`: 12 suites, **63 pruebas, 0 fallos, 0 errores, 1 omitida**; `ComandasIncrementalesIT` pasó 6/6, incluida la concurrencia y consulta de marchas exactas. `ComandaMarchaTest` pasó 4/4 y `ServicioComandaCocinaTest` pasó 5/5; sus pruebas PDF detectaron y llevaron a corregir el ajuste de ancho de `PREPARAR [cantidad]` para no partir la acción en el ticket de 80 mm. `ant -q compile-test` pasó y los scripts de migración permanecen byte-idénticos. E2E `Vista.E2EVirtual` (`E2E_OK`) imprimió vía spooler simulado la comanda inicial de un pollo y la segunda marcha del incremento de dos; E2E `Vista.AtencionDirectaCompletoE2EVirtual` también pasó. La validación fue aislada; no se reinició ni desplegó en la instancia operativa.
- Tras incorporar ambas tablas de marchas a la lista blanca de respaldos, `ServicioRespaldoBaseDatosTest` pasó en MySQL Compose efímero: **22 pruebas, 0 fallos, 0 errores, 0 omitidas**. El caso de restauración de respaldo antiguo reejecuta la migración faltante v15 y verifica el esquema sincronizado; no se probó el backfill contra datos de una copia de producción.
- `ant -q test-single` de `LoginDaoCicloVidaTest` pasó **13/13** con propiedades de conexión dirigidas a `restaurante_test`; `ant -q integration-test` pasó **66 pruebas, 0 fallos, 0 errores y 1 omitida**. Una nueva regresión confirmó que el DAO espera un advisory lock de MySQL retenido por otra conexión.
- Estado más reciente del smoke con MySQL de privilegios mínimos: se identificó que `ACCION_IMPRESION=VISOR` abría un lector PDF externo que tapaba la aplicación; `e2e_bootstrap_virtual.sh` se corrigió a `IMPRIMIR`, con `lp` simulado en PATH. `E2E_BOOTSTRAP_FULL_DAY=SI ./e2e_bootstrap_virtual.sh` terminó `E2E_OK`: la UI mostró **8 facturas FINALIZADAS**, 1 cuenta anulada, 0 pendientes, total conciliado Bs. 5783.60 y cierre Z aceptado por el spooler simulado. Esto valida bootstrap limpio, operación y cierre con los privilegios del runner; no acredita papel físico.
- `ant -q compile jar`, `ant -q compile-test`, `git diff --check` y `cmp -s actualizar_bd.sql db/migrations/actualizar_bd.sql` pasaron tras los cambios actuales. La suite unitaria completa fue **657/0/0/30 omitidas**; integración actual **65/0/0/1 omitida**.
- Sigue pendiente validar scripts/ACL en Windows, regenerar los ZIP Linux con el árbol actual, restaurar un respaldo en BD secundaria y probar salida en tickera física.
- Se creó el commit `8594ec0` (`feat: harden production readiness and ticket printing`) y se subió a `origin/main`; no se desplegó ni reinició la aplicación.

---

## Restricciones globales y principios del Red Team

- **Aislamiento de árbol sucio:** Los cambios en `src/Vista/PanelAtencionMesasSplit.java`, `src/Vista/Sistema.java`, `test/Vista/DiaE2ERobot.java` y `test/Vista/RedimensionarColumnasJTableE2EVirtual.java` deben aislarse en una rama dedicada (`feature/persistencia-columnas-jtable`) antes de iniciar trabajos de release, manteniendo `main` limpio.
- **E2E estrictamente por UI:** Cumplir la regla de `AGENTS.md`: todo test E2E interactúa exclusivamente vía GUI. Prohibido preparar estado o aserciones con JDBC/DAO directos (corregir `RedimensionarColumnasJTableE2EVirtual`).
- **No tocar producción:** No ejecutar pruebas ni comandos SQL contra la BD productiva `restaurante`. Pruebas de integración únicamente contra `127.0.0.1:3307/restaurante_test` con el contenedor Compose aislado.
- **Privilegios de MySQL Runtime:** La cuenta de aplicación (`restaurante_app`) debe poseer `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON restaurante.*`. Esto es indispensable porque `MigradorEsquemaJdbc` se ejecuta en el arranque de la app. No otorgar privilegios administrativos globales (`DROP DATABASE`, `GRANT`, `CREATE USER`).
- **Política TLS realista:** Exigir `sslMode=VERIFY_IDENTITY` en conexiones WAN / hostnames públicos externos. Para terminales en red LAN privada (rangos RFC 1918: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), permitir `sslMode=REQUIRED` (tráfico cifrado en tránsito sin requerir CA pública) o truststore personalizado.
- **Compatibilidad con scripts Windows:** Al editar `.bat`, `.ps1` y `.vbs`, respetar estrictamente las reglas de `AGENTS.md` (CRLF obligatorio, UTF-8 con BOM en `.ps1`, ASCII puro en `.bat` sin `chcp 65001`). No romper las aserciones de `InstaladoresSeguridadTest.java`.
- **Rotación HMAC con retrocompatibilidad:** `ServicioRespaldoBaseDatos` debe soportar `BACKUP_HMAC_KEY_PREVIOUS` para validar respaldos históricos antes de invalidar claves previas.
- **Migraciones y DDL en MySQL:** En MySQL, las operaciones DDL provocan commit implícito inmediato. No asumir "rollback de DDL"; la red de seguridad ante fallos de migración es el modo `MANTENIMIENTO` y la restauración del respaldo `pre_migracion_*.sql`.
- **Paridad byte a byte:** `actualizar_bd.sql` y `db/migrations/actualizar_bd.sql` deben ser siempre byte-idénticos (exigido por `InstaladoresSeguridadTest`).

---

## Estado de partida verificado

- `ant -q compile jar`: BUILD SUCCESSFUL.
- `ant -q compile-test`: BUILD SUCCESSFUL; 141 fuentes de prueba compilaron.
- `ant integration-test`: BUILD SUCCESSFUL; 54 pruebas, 0 fallos, 0 errores y 1 omitida en BD desechable.
- `generar_restaurante_ejecutable.sh` y `.ps1` copian o generan `.env` si existe; `empaquetar_produccion.sh` comprime con exclusiones genéricas y requiere exclusión explícita de `.env` y credenciales.
- La BD semilla de producción debe eliminar la cuenta `admin/admin` activa, pero el entorno de integración y E2E (`restaurante_test`) debe retener fixtures de prueba para no invalidar las suites automatizadas.
- `reporte.md` describe hallazgos desactualizados de compilación y no debe usarse como referencia sin revalidación directa.
- El diario persistente de marchas de cocina existe; aún no hay una pantalla de operación dedicada al rol `Cocinero`, por lo que debe mantenerse el aviso informativo y evitar una vista vacía.

---

## Fase 0 — Congelar la línea base y separar el trabajo

**Archivos:** `AGENTS.md`, `CONTEXT.md`, `reporte.md`, árbol de trabajo actual.

- [ ] Registrar `git status --short`, rama y `git diff`.
- [ ] **Aislar el trabajo en curso en rama dedicada:**
  Crear la rama `feature/persistencia-columnas-jtable`, añadir `src/Vista/PanelAtencionMesasSplit.java`, `src/Vista/Sistema.java`, `test/Vista/DiaE2ERobot.java` y `test/Vista/RedimensionarColumnasJTableE2EVirtual.java`, commitear y retornar a `main` limpio:
  ```bash
  git checkout -b feature/persistencia-columnas-jtable
  git add src/Vista/PanelAtencionMesasSplit.java src/Vista/Sistema.java test/Vista/DiaE2ERobot.java test/Vista/RedimensionarColumnasJTableE2EVirtual.java
  git commit -m "feat(ui): persistencia de columnas JTable y soporte E2E Robot"
  git checkout main
  ```
- [ ] Registrar en el backlog de la feature que `RedimensionarColumnasJTableE2EVirtual.java` debe refactorizarse para eliminar llamadas JDBC directas antes de integrarse en `main`.
- [ ] Archivar los resultados de compilación de partida (`ant -q compile jar`, `ant -q compile-test`, `ant integration-test`).
- [ ] Depurar `reporte.md` para reflejar el estado técnico verificado, eliminando advertencias obsoletas de fallo en `compile-test`.

**Gate:** `main` se encuentra 100% limpio (`git status` sin modificaciones); el trabajo de columnas está respaldado en su propia rama; la línea base compila y pasa integración.

---

## Fase 1 — Cerrar filtración de secretos y blindar empaquetado (bloqueo P0)

**Archivos:** `generar_restaurante_ejecutable.sh`, `generar_restaurante_ejecutable.ps1`, `empaquetar_produccion.sh`, `instalar_mysql_y_bd.ps1`, `instalar_acceso_directo.ps1`, `actualizar_bd.bat`, `.gitignore`, `.env.example`, `src/infraestructura/ProveedorConexionJdbc.java`, `src/Servicio/ServicioRespaldoBaseDatos.java`, `test/infraestructura/InstaladoresSeguridadTest.java`.

- [ ] **Empaquetado limpio en Linux:**
  - Modificar `generar_restaurante_ejecutable.sh` para copiar únicamente binarios, librerías y `.env.example`. Eliminar la copia condicional de `.env`.
  - Modificar `empaquetar_produccion.sh` para construir el ZIP a partir de una lista blanca estricta; excluir `.env`, `.env.*` (salvo `.env.example`), `.git`, logs, respaldos y volcados SQL.
- [ ] **Ajuste en Scripts Windows (respetando `InstaladoresSeguridadTest`):**
  - Actualizar `generar_restaurante_ejecutable.ps1` para que empaquete `.env.example` y no exija la presencia de un `.env` con credenciales reales para generar el ZIP.
  - Actualizar `instalar_mysql_y_bd.ps1` e `instalar_acceso_directo.ps1` para que, al instalar en el cliente, generen un `.env` local seguro con contraseña aleatoria (`New-RandomDatabasePassword`) aplicando ACLs NTFS restringidas (solo accesible por el usuario del servicio/operador).
  - Asegurar que `actualizar_bd.bat` muestre un error descriptivo si `.env` no ha sido aprovisionado externamente.
- [ ] **Definición de Usuario MySQL Runtime de Mínimos Privilegios:**
  - Configurar la cuenta runtime de la app (`restaurante_app`) con los privilegios exactos que requiere su arquitectura:
    ```sql
    GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON restaurante.* TO 'restaurante_app'@'%';
    ```
  - Prohibir privilegios administrativos globales (`DROP DATABASE`, `GRANT OPTION`, `CREATE USER`).
  - Documentar que la creación de la BD y la asignación inicial de privilegios corresponde al script instalador (usando credenciales de root de instalación temporal).
- [ ] **Soporte de TLS Dinámico (LAN vs WAN):**
  - En `ProveedorConexionJdbc.java`, diferenciar entre conexiones locales/privadas y remotas públicas:
    - Host en loopback (`127.0.0.1`, `localhost`): TLS opcional/desactivado según config.
    - Host en rangos LAN RFC 1918 (`192.168.*`, `10.*`, `172.16-31.*`): exigir `sslMode=REQUIRED` (tráfico cifrado sin exigir validación de CA pública).
    - Host FQDN externo o IP pública: exigir `sslMode=VERIFY_IDENTITY` con certificados de CA válidos.
- [ ] **Rotación Segura de HMAC en `ServicioRespaldoBaseDatos`:**
  - Añadir soporte para la variable `BACKUP_HMAC_KEY_PREVIOUS` en `ServicioRespaldoBaseDatos.java`.
  - Al validar un respaldo con `validarChecksumSiExiste`, intentar primero con `BACKUP_HMAC_KEY`; si falla, validar contra `BACKUP_HMAC_KEY_PREVIOUS` antes de arrojar `ErrorAplicacionException`.
- [ ] **Pruebas automatizadas de empaquetado:**
  - Crear test en `InstaladoresSeguridadTest` que inspeccione los ZIPs generados y falle si contienen archivos prohibidos (`.env`, `*.key`, `*.pem`, `*.sql.gz`, `*.log`).

**Gate:** Los paquetes distribuibles no contienen `.env` ni claves privadas; los scripts Windows generan `.env` aleatorio con ACLs; `restaurante_app` arranca y ejecuta `MigradorEsquemaJdbc` sin fallos de permisos; respaldos firmados con la clave HMAC previa siguen siendo restaurables.

---

## Fase 2 — Bootstrap y credenciales del Administrador (bloqueo P0)

**Archivos:** `BD.sql`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `src/Modelo/LoginDao.java`, `src/Modelo/Usuario.java`, `src/Servicio/AutenticacionServicio.java`, `src/Vista/FrmLogin.java`, `src/Vista/FrmBootstrapInicial.java` (nuevo modal), `src/restaurante/Restaurante.java`, `test/Vista/DiaE2EFixture.java`.

- [ ] **Estado de Bootstrap en Base de Datos:**
  - Registrar en `configuracion_sistema` la clave `bootstrap_inicial_completado` con valor `'0'` o `'1'`.
  - En la migración idempotente de `actualizar_bd.sql`, si ya existe un Administrador activo con contraseña personalizada (distinta de la conocida por defecto), establecer automáticamente `bootstrap_inicial_completado='1'` para no bloquear instalaciones preexistentes.
- [ ] **Separación entre Producción y Fixtures de Pruebas:**
  - En `BD.sql` (producción): crear la cuenta Administrador con estado inactivo o con contraseña aleatoria bloqueada, y `bootstrap_inicial_completado='0'`.
  - En el entorno de pruebas e integración (`127.0.0.1:3307/restaurante_test` y fixtures de `DiaE2EFixture`): asegurar que se pre-siembre el usuario `admin/admin` y `bootstrap_inicial_completado='1'` para que las suites unitarias, de integración y E2E no se rompan ni requieran interacción humana.
- [ ] **Flujo de Bootstrap GUI y Exención Headless:**
  - Si la aplicación se ejecuta con `--migrate-db` o en entorno headless (`java.awt.GraphicsEnvironment.isHeadless()`), omitir por completo el flujo de interfaz de bootstrap.
  - Al iniciar `Restaurante.java` en modo GUI normal: si `bootstrap_inicial_completado != '1'`, abrir un diálogo modal `FrmBootstrapInicial` que solicite obligatoriamente:
    - Nombre del propietario/administrador.
    - Correo / usuario nuevo.
    - Contraseña segura con confirmación (mínimo 8 caracteres, validación defensiva).
- [ ] **Concurrencia y Transaccionalidad:**
  - Envolver la creación/activación del Administrador y la actualización de `bootstrap_inicial_completado='1'` en una única transacción JDBC protegida por el lock de MySQL `SELECT GET_LOCK('restaurante_bootstrap_lock', 10)`. Si otra terminal inicia simultáneamente, esperará a que el bootstrap concluya.
- [ ] **Reglas de Dominio Permanentes:**
  - Prohibir en `UsuarioDao` y `AutenticacionServicio` la desactivación, eliminación o cambio de rol del último Administrador activo.
  - Asegurar que ningún log ni traza de error capture contraseñas en texto claro.

**Gate:** Una instalación limpia no permite entrar con `admin/admin` ni operar sin completar el asistente de bootstrap; el modo `--migrate-db` no se bloquea; las suites de integración y E2E en `restaurante_test` continúan pasando al usar sus fixtures de prueba.

---

## Fase 3 — Hacer inequívocos los resultados de impresión sin colgar el EDT (bloqueo P1)

**Archivos:** `src/Servicio/ServicioImpresionTicket.java`, `src/Servicio/PedidoPdfServicio.java`, `src/Servicio/CierreCajaServicio.java`, `src/Vista/FinalizarPedidoSwingWorker.java`, `src/Controlador/PedidosControlador.java`, `src/Vista/Sistema.java`, pruebas de impresión.

- [x] **Comandas parciales / marchas incrementales (bloqueo funcional cerrado en integración y E2E):**
  - Al enviar cambios de una mesa, no volver a imprimir los detalles ya enviados a cocina.
  - Registrar cada envío como una marcha persistente e inmutable ligada al pedido; incluir únicamente las nuevas líneas y el incremento de cantidad desde la última marcha, incluso cuando el mismo plato ya figure en una marcha anterior.
  - Persistir la marcha y el cambio del pedido en una sola transacción; asignar secuencia única por pedido y conservar snapshots para consulta exacta.
  - Reducciones y cambios de comentario generan un comprobante de ajuste con `RETIRAR`/`PREPARAR`; la migración v15 crea un baseline no imprimible para pedidos pendientes existentes. Si la impresión se desactiva, la siguiente marcha incluye todo lo pendiente.
  - Cubrir primera marcha, incremento repetido, reducción, cambio de nota, no-op, omisión de impresión, representación PDF y flujo UI con pruebas aisladas.
- [x] **Contrato Tipado de Salida:**
  - Crear el tipo canónico de resultado:
    ```java
    public record ResultadoSalidaTicket(
        EstadoSalida estado, // ENVIADO_A_SPOOLER, ABIERTO_EN_VISOR, FALLO
        String canal,        // "Java Print Service (XP-80T)", "PDF24", "Visor Sistema"
        String detalleError
    ) {
        public enum EstadoSalida { ENVIADO_A_SPOOLER, ABIERTO_EN_VISOR, FALLO }
    }
    ```
  - Clarificar en los mensajes y documentación que `ENVIADO_A_SPOOLER` confirma que el spooler del sistema operativo (`spoolsv.exe` / CUPS) aceptó el documento, sin prometer mecánicamente la salida de papel físico.
- [x] **Desacoplar la Reimpresión del Event Dispatch Thread (EDT):**
  - En `Sistema.java` (líneas 594, 4181, 7207), mover las invocaciones a `pedidosControlador.reimprimirPdfPedido(...)` fuera del EDT usando un hilo virtual (`Thread.startVirtualThread`) o un `SwingWorker` ligero con cursor `WAIT_CURSOR`.
  - Evitar que los reintentos o timeouts de PowerShell (hasta 14 segundos) congelen la ventana principal.
- [x] **Manejo Resiliente en `FinalizarPedidoSwingWorker`:**
  - Actualizar `ResultadoFinalizacion` para contener `ResultadoSalidaTicket`:
    ```java
    public record ResultadoFinalizacion(boolean finalizado, ResultadoSalidaTicket salidaTicket) {}
    ```
  - Si el cobro se registró en BD pero la impresión falla (`salidaTicket.estado() == FALLO`), mostrar un aviso informativo claro:
    *"Cobro registrado con éxito en caja. La impresión del ticket físico falló; puede reimprimirlo desde el Historial."*
    **Prohibido lanzar excepción fatal que sugiera al cajero que el cobro no se completó.**
- [x] **Unificación de Parámetros de Salida:**
  - Homogeneizar la variable `ACCION_IMPRESION` (`IMPRIMIR`, `VISOR`, `AUTO`) en `ServicioImpresionTicket`, documentarla en `.env.example` y eliminar referencias a variables huérfanas.
- [x] **Ciclo de vida de documentos de salida:**
  - Los tickets/facturas se conservan en `facturas/<AAAA-MM>` y las reimpresiones generan copias con timestamp; no se purgan automáticamente porque son comprobantes. Los archivos temporales propiedad del E2E viven en `E2E_WORKDIR` y el wrapper los elimina junto con la BD desechable. No se usa `temp/tickets/` para los documentos fiscales.

**Gate:** Ninguna pantalla muestra "impreso" si sólo se abrió visor o hubo fallo; la reimpresión no congela el cursor de la UI ante timeouts de impresora; un fallo de impresión tras un cobro deja la venta confirmada y notifica al usuario sin error fatal.

---

## Fase 4 — Migraciones, integridad y recuperación operacional (bloqueo P1)

**Archivos:** `BD.sql`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `src/infraestructura/MigradorEsquemaJdbc.java`, `src/Servicio/ServicioRespaldoBaseDatos.java`, `test/infraestructura/InstaladoresSeguridadTest.java`.

- [ ] **Procedimiento de Recuperación ante Falla DDL en MySQL:**
  - Documentar con rigor que MySQL ejecuta commits implícitos en sentencias DDL.
  - Asegurar que ante cualquier falla o interrupción de `MigradorEsquemaJdbc`:
    1. La base queda bloqueada en estado `MANTENIMIENTO` dentro de `configuracion_sistema`.
    2. La aplicación aborta el arranque informando la anomalía.
    3. El procedimiento de recuperación ejecuta la restauración del volcado `pre_migracion_*.sql` generado automáticamente justo antes de iniciar la migración.
- [ ] **Idempotencia de Esquema:**
  - Garantizar que cada instrucción en `actualizar_bd.sql` e interior de `MigradorEsquemaJdbc` consulte `DatabaseMetaData` antes de alterar columnas o constraints para evitar excepciones por reintento.
- [ ] **Sincronización Byte-Idéntica Obligatoria:**
  - Tras cualquier modificación en migraciones SQL, ejecutar:
    ```bash
    cp -f actualizar_bd.sql db/migrations/actualizar_bd.sql
    ```
  - Verificar que pase `InstaladoresSeguridadTest` (aserciones de igualdad de bytes y finales de línea).
- [ ] **Pruebas de Respaldo y Restauración en BD Aislada:**
  - Validar generación de respaldo con firma HMAC.
  - Validar restauración exitosa con clave primaria y con `BACKUP_HMAC_KEY_PREVIOUS`.
  - Validar rechazo inmediato ante respaldos alterados o con firma ilegítima.

**Gate:** Las migraciones son 100% idempotentes; los dos `actualizar_bd.sql` son idénticos byte a byte; la prueba de respaldo y restauración en base aislada concluye con validación de saldos y conteos idénticos.

---

## Fase 5 — Autorización y roles

**Archivos:** `RolesSistema.java`, `PoliticaAcceso.java`, `ConfiguracionAccesosServicio.java`, `LoginDao.java`, `src/Vista/Sistema.java`, `src/Vista/FrmLogin.java`, pruebas `test/Servicio` y `test/Controlador`.

- [ ] **Manejo Explícito del Rol `Cocinero`:**
  - Aunque las marchas de cocina ya persisten, aún no existe una pantalla operativa dedicada al rol `Cocinero`; evitar que entre a una pantalla vacía o rota.
  - Al autenticar un usuario `Cocinero`, mostrar diálogo informativo:
    *"El módulo interactivo de cocina está en desarrollo. Este perfil no cuenta con permisos para operar terminales de sala ni caja."*
  - Deshabilitar temporalmente la asignación del rol `Cocinero` en el formulario de creación de usuarios de `Sistema.java` hasta la entrega del módulo de cocina.
- [ ] **Unificación de Revocación de Sesión en Caliente:**
  - Auditar todos los controladores y eventos en `Sistema.java` para que las acciones sensibles invoquen obligatoriamente el método centralizado `autorizar(Accion)`.
  - Asegurar que si una cuenta es desactivada o degradada de rol mientras mantiene la ventana abierta, el siguiente clic invoque `autorizar()`, destruya la sesión (`dispose()`), informe al usuario y retorne a `FrmLogin`.
- [ ] **Inmutabilidad del Administrador:**
  - Probar que ningún cambio en la matriz de permisos de `configuracion_accesos` pueda revocar permisos al rol `Administrador`.
- [ ] **Fail-Closed en Configuración:**
  - Si la tabla de permisos está vacía o inaccesible, el sistema debe denegar el acceso a toda acción protegida (fail-closed) salvo para el `Administrador` maestro hardcodeado en código.

**Gate:** Acciones sensibles protegidas en controlador y servicio; degradación de rol en caliente cierra la sesión de forma consistente; rol Cocinero no causa confusión operativa.

---

## Fase 6 — Separar pruebas de desarrollo y pruebas de producción

**Archivos:** `build.xml`, `docker-compose.integration.yml`, `test/`, `e2e_virtual.sh`, `e2e_dia_completo_virtual.sh`, `test/Vista/DiaE2EFixture.java`.

- [x] **Blindaje de suites contra bases productivas:**
  - Asegurar que ninguna prueba unitaria, de integración o E2E pueda ejecutarse si la URL JDBC apunta a la base `restaurante` en 3306.
  - Las suites deben fallar de inmediato si `E2E_BD_DESECHABLE != 'SI'` o si el puerto no es el aislado 3307.
- [x] **Pre-siembra controlada en entorno de pruebas:**
  - Garantizar que el seed `db/integration/003-seed-integration.sql` inserte la cuenta de prueba `admin/admin` y `bootstrap_inicial_completado='1'` exclusivamente en `restaurante_test`; `DiaE2EFixture` exige por configuración que los E2E apunten a esa BD y hacen login por UI.
  - Esto permite los flujos de prueba sin preparar usuarios ni datos mediante código de negocio dentro de los robots.
- [x] **Corrección del test de columnas:**
  - En la rama `feature/persistencia-columnas-jtable`, refactorizar `RedimensionarColumnasJTableE2EVirtual.java` para eliminar llamadas a JDBC y DAOs, basando la prueba en la inspección visual de anchos de columna vía UI.
- [x] **Ejecución y registro de suites:**
  - Ejecutar `ant -q compile jar`.
  - Ejecutar `ant -q compile-test`.
  - Ejecutar `ant integration-test` en puerto 3307.
  - Ejecutar los E2E mediante `./e2e_dia_completo_virtual.sh`, que delega en `e2e_virtual.sh` con Xvfb `:99`, MySQL efímero e interacción UI-only.

**Gate:** El destino de las suites está blindado y toda suite ejecutada usa la BD desechable; el gate integral de release aún requiere los E2E restantes listados en Fase 7.

---

## Fase 7 — Ensayo de release y aceptación

**Archivos:** scripts de empaquetado, `Restaurante_Ejecutable/`, documentación operativa y notas de release.

- [x] Construir desde un worktree limpio: `ant clean jar` terminó correctamente en el commit `b6ded11`.
- [x] Generar paquete Linux con `generar_restaurante_ejecutable.sh`; el empaquetador `.ps1` requiere runner Windows y sigue pendiente.
- [x] Generar paquete Linux con `empaquetar_produccion.sh`.
- [x] Inspeccionar ambos ZIP Linux (34 entradas cada uno) con scanner de lista prohibida; comprobó ausencia de `.env`, claves, volcados SQL comprimidos y logs, y presencia de `.env.example`, JAR y las seis librerías:
  - Comprobar ausencia de `.env`, claves privadas, volcados SQL y logs.
  - Comprobar presencia de `.env.example` y librerías completas en `librerias/`.
- [ ] Realizar instalación limpia de prueba en máquina o contenedor virgen:
  - Completar el asistente `FrmBootstrapInicial` definiendo la primera contraseña de Administrador.
  - Conectar con usuario runtime de privilegios acotados (`restaurante_app`).
  - Ejecutar smoke test de UI: login, apertura de mesa, comanda, cobro y cierre de caja.
  - Validar salida física en tickera térmica de prueba.
- [ ] Simular recuperación: disparar respaldo firmado, verificar checksum con HMAC y restaurar en base secundaria.
- [ ] Actualizar `CONTEXT.md` y documentar el procedimiento de rollback operativo.

**Gate final:** Cero secretos en artefactos; bootstrap seguro operativo; instalación y restauración verificadas; todas las suites de prueba aprobadas.

---

## Mejoras posteriores, fuera del bloqueo inicial

- El diario persistente de marchas de cocina ya está implementado en la feature; falta integrar el cambio y completar los gates generales de producción.
- Migrar gradualmente componentes de `Sistema.java` a controladores/paneles modulares independientes.
- Añadir telemetría de cola de impresión y auditoría histórica de cambios de roles RBAC.

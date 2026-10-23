# AGENTS.md — Proyecto Restaurante 2026

> Contexto de sistema (arquitectura, BD, build): ver `CONTEXT.md`.
> Stack: Java 21 + Swing, MySQL 8.4 (Docker `restaurante-db-1`, `admin/admin`), Ant. Display `DISPLAY=:0.0`.
> Git: nunca commitear scratch (`Test*.java`, `test_tab*`, `debug.log`, `target.txt`).

## Build / Deploy / Test (Linux, repo root)

| Comando | Uso |
|---|---|
| `ant -q compile jar && cp -f dist/Restaurante.jar Restaurante_Ejecutable/Restaurante.jar` | Flujo estándar de compilación + despliegue |
| `./generar_restaurante_ejecutable.sh` | Regenera `Restaurante_Ejecutable/` (jar + librerías + zip reproducible). Exige árbol git limpio |
| `./empaquetar_produccion.sh` | Genera `../Restaurante_2026_Produccion.zip` |
| `ant test` | Suite unitaria (679 tests; usa BD del `.env` — con `.env` operativo aborta por guardia). Para BD desechable 3307: `ant test -DDB_URL="jdbc:mysql://127.0.0.1:3307/restaurante_test" -DDB_NAME=restaurante_test -DMYSQL_DATABASE=restaurante_test -DDB_USER=restaurante_test_app -DDB_PASSWORD=test_only_app_password` (+ mismas `-Dtest-sys-prop.*` con URL completa `?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`), previo `docker compose -p <proyecto> -f docker-compose.e2e.yml up -d` + `./inicializar_bd_test.sh --datos` |
| `ant integration-test` | Suite integra contra MySQL efímero (`restaurante_test` 3307); levanta/baja compose solo (liberar 3307 antes) |
| `./e2e_virtual.sh [clase]` · `./e2e_bootstrap_virtual.sh` · `./e2e_dia_completo_virtual.sh [clase]` | E2E en Xvfb `:99` por UI real sobre BD desechable `restaurante_test` (bootstrap/seed por interfaz; `E2E_SEED_PLATOS=108` siembra combinatoria) |
| `./generar_bd_test.sh` · `./cargar_bd_test.sh` · `./inicializar_bd_test.sh [--datos]` · `./verificar_bd_test.sh` | Ciclo del fixture `BD-test.sql` (esquema completo con salas id=1 ×4 mesas, 109 platos, config 40/16) en BD desechable 3307 |
| `./drill_restauracion_bd.sh` / `..._secundaria.sh` / `./redteam_autoreparacion_bd.sh` | Drills efímeros: respaldo/restauración, restauración heterogénea (`RESTAURACION_SECUNDARIA_OK`) y autoreparación de esquema (`REDTEAM_AUTOREPARACION_OK`) |

## Arranque (producción)

| Acción | Cómo |
|---|---|
| Arranque manual | `cd Restaurante_Ejecutable && DISPLAY=:0.0 java -jar Restaurante.jar` |
| Servicios útiles sin abrir la app | `java -jar Restaurante.jar --migrate-db` · `--respaldar [dir]` · `--verificar-respaldo <sql>` · `--restaurar-respaldo <sql>` |
| Login demo | `admin` / `admin` · instancia única por puerto **49200** |

## Base de datos

- `./inicio_fresco.sh [--forzar]`: **destructivo** — backup → vacía 16 tablas → siembra maestra/demos (probar con `MYSQL_DB=restaurante_prueba ... --forzar`).
- `./limpiar_para_comenzar_hoy.sh` / `.bat`: limpieza operativa de pedidos/pagos/cierres para iniciar servicio (backup previo; preserva maestros).
- `BD.sql` + `actualizar_bd.sql` + `db/migrations/actualizar_bd.sql` — los dos últimos **deben ser byte-idénticos** (lo exige `InstaladoresSeguridadTest`).

## Instaladores Windows (un clic, requieren Admin)

`instalacion_completa_un_clic.bat` (todo en uno) y variantes por pieza: `instalar_mysql_y_bd`, `generar_restaurante_ejecutable`, `instalar_acceso_directo`, `iniciar_restaurante`, `listar_impresoras`, `configurar_mysql.ps1`.

## Vocabulario de dominio (usarlo sin excepciones)

- **Comanda** (`ServicioComandaCocina`): ronda enviada a cocina/barra; se imprime solo el incremento de la ronda en curso, nunca se reimprime lo servido. Cero impacto contable.
- **Cuenta de Mesa / Pedido** (`pedidos`/`detalle_pedidos` en `PENDIENTE`): consumo acumulado; pre-cuenta informativa no fiscal, modificable.
- **Factura** (`FINALIZADO`): venta definitiva fiscal (correlativo, IVA, `pedido_pagos`); libera mesa y alimenta el Cierre Z inmutable.
- **3 anulaciones**: ① ítem/comanda (cocina, no toca dinero); ② fuga/cuenta no pagada (sin pagos, sin caja); ③ factura cobrada (contra-asiento formal en `pedido_saldos_movimientos` y reversión en caja para cuadrar Cierre Z).

## Notas para agentes

- **Rama de trabajo: `main`**: commitear y pushear directamente a `main` (sin ramas feature ni PR). Antes de cambios: `git checkout main && git pull --ff-only origin main`.
- **Secretos fuera de git — regla**: TODO archivo que pueda contener usuarios/contraseñas, HMAC o firmas, volcados de BD con datos reales (respaldos `*.sql`, dumps `pre_migracion_*`, emergencias) o logs con credenciales va a `.gitignore` **aunque sea de pruebas/test en scratch**; nunca commitear secretos reales (el `.env` real nunca se rastrea, solo `.env.example`). Antes de cada commit: `git status --short` + `git ls-files | grep -iE "env|respaldo|log|key|hmac|cred|sql"` para verificar que no se haya rastreado algo sensible; si existe y es sensible, `git rm --cached` y añadir el patrón a `.gitignore` (patrones preferentes bona fide: `**/respaldo_*`, `**/pre_migracion_*`, `**/restaurante_*.sql`, `*.hmac`, `*.pem/`claves). En scripts/tests que generen credenciales temporales: escribir a `/tmp/opencode` o a rutas ya ignoradas.
- **DB prod**: `docker exec restaurante-db-1 mysql -uadmin -padmin restaurante -e "..."` (no hay `mysql` CLI en host). Toda BD efímera se levanta/baja con proyecto Compose **SIEMPRE explícito**: `docker compose -f docker-compose.integration.yml -p <proyecto> up -d` / `down --volumes` — jamás sin `-p`.
- **E2E**: solo por la interfaz (botones, campos, tablas), con teclado/mouse virtuales propios en Xvfb `:99`; PROHIBIDO invocar DAO/servicios o tocar BD para preparar/verificar estado. Wrapper solo acepta `restaurante_test` en 3307. Bootstrap en BD virgen: `./e2e_bootstrap_virtual.sh` (privilegios mínimos, no ampliar). Impresión en E2E: `ACCION_IMPRESION=IMPRIMIR` con el `lp` falso del runner (nunca `VISOR`: bloquea al Robot).
- **Reinicio desde agente**: jamás mezclar en una llamada `pkill -f "[R]estaurante.jar"` con texto plano `Restaurante.jar` (la bash propia se auto-mata): `pkill` en una llamada, arranque en otra. Arranque con `systemd-run --user --collect --unit=restaurante-app --working-directory=<Restaurante_Ejecutable> --setenv=DISPLAY=:0.0 --setenv=XAUTHORITY=~/.Xauthority java -jar Restaurante.jar`; si la unit está cargada: `systemctl --user stop restaurante-app; sleep 2; systemctl --user reset-failed restaurante-app`. No reiniciar en horario de servicio sin pedirlo.
- **Tests unitarios** usan la BD del `.env`: no correr `ant test` tras sembrar datos limpios, salvo necesario. Para un test único en scratch 3307: `ant test-single(-method) -Dtest.class=… -Dtest.includes=… -Djavac.includes=…` + neutralizar `.env` con `-DDB_URL/-DDB_NAME/-DMYSQL_DATABASE` apuntando a `restaurante_test` y pasar las sysprops como `-Dtest-sys-prop.DB_URL/DB_USER/DB_PASSWORD`.
- **Evidencia** (`bitacora-contexto-produccion.md`): encabezado `snapshot DD/MM/AAAA HH:MM`; formato `comando → archivo de salida → PASS/FLAKY/FAIL` con alcance real demostrado; un PASS parcial no cierra un gate integral.
- **Dinero — política**: 2 decimales de punta a punta (`DECIMAL(*,2)`, setters y `CalculoMonetarioPedido.validarMontoCalculado` rechazan `scale > 2`); aritmética intermedia con más decimales (ESCALA_INTERMEDIA 8, `ROUND(...,2)` por pedido en SQL) y redondeo único HALF_UP al límite de línea/pedido: así `Σ desglose == cabecera`. No subir decimales a la BD para "cuadrar" reportes.
- **Esquema vs clave/valor**: antes de añadir columna (sobre todo en `config`), evaluar resolverlo con `configuracion_sistema` (`ConfigClaves` / `ConfiguracionRepositorio`) y evitar DDL innecesario.
- **Transacciones JDBC**: toda conexión pooled devuelta en `autocommit=false` se revierte como protección y, si hubo escrituras, `close()` registra SEVERE y propaga la excepción: todo flujo propietario debe confirmar o revertir explícitamente.
- **DI por constructor** para dependencias nuevas/modificadas; ensamblaje en el punto de composición (`restaurante.Restaurante`); no instanciar DAOs/servicios dentro de vistas. Migración incremental, sin frameworks DI ni refactors masivos sin necesidad.
- **Diseñador Swing**: `src/Vista/Sistema.java` tiene edición manual fuera de `Sistema.form` (scroll lateral, `btnDashboard`, contrastes). No regenerar desde Matisse; si se edita el `.form`, remodelar el `JScrollPane` allí.
- **Scripts Windows** (`.bat`/`.cmd`/`.ps1`/`.vbs`): guardar en **CRLF** (`sed -i 's/\r$//; s/$/\r/' <archivo>`; `.gitattributes` fija `eol=crlf`). `.bat`: sin `chcp 65001`, ASCII puro sin BOM (sin acentos/ñ/¿). `.ps1`: UTF-8 **con BOM** + `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` al inicio. `.vbs`: ASCII puro sin BOM.

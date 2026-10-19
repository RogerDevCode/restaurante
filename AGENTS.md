# AGENTS.md — Scripts del proyecto Restaurante 2026

> Para contexto del sistema (arquitectura, BD, build): ver `CONTEXT.md`.
> Convenciones: Java 21, Ant (`ant -q compile jar`), MySQL vía Docker (`restaurante-db-1`, `admin/admin`), display `DISPLAY=:0.0`. No commitear scratch (`Test*.java`, `test_tab*`, `debug.log`, `target.txt`).

## Build / Deploy (Linux, repo root)

| Script / comando | Uso |
|---|---|
| `ant -q compile jar && cp -f dist/Restaurante.jar Restaurante_Ejecutable/Restaurante.jar` | Compilar + desplegar jar ejecutable (flujo estándar) |
| `./generar_restaurante_ejecutable.sh` | Regenera `Restaurante_Ejecutable/` (compila con Ant, copia jar+`librerias`, zip). Requiere JDK 21 + Ant |
| `./empaquetar_produccion.sh` | Genera `../Restaurante_2026_Produccion.zip` para distribuir |
| `ant test` | Suite unitaria (~398 tests, necesita display) |
| `ant integration-test` | Suite contra MySQL Docker 3307 (`restaurante_test`); levanta/baja compose solo |
| `./e2e_virtual.sh [clase]` | E2E en Xvfb `:99` (1366x711) con mouse/teclado independientes de `:0`; rechaza destinos distintos de MySQL local `restaurante_test` en 3307 con `E2E_BD_DESECHABLE=SI`. Para jornada aislada usar `./e2e_dia_completo_virtual.sh`. |
| `./e2e_dia_completo_virtual.sh [clase] [args]` | Crea MySQL efímero aislado en `restaurante_test` (proyecto Compose único), ejecuta la clase indicada (por defecto `Vista.DiaCompletoFiscalE2EVirtual`) en Xvfb `:99` mediante `e2e_virtual.sh` y elimina solo su proyecto/volumen al terminar; falla si 3307 o `:99` ya están ocupados. Requiere Docker, Java 21, Ant, Xvfb y xdotool |
| `ant integration-test` | Suite contra MySQL Docker 3307 (`restaurante_test`); levanta/baja compose solo |

## Arranque (producción)

| Script / comando | Uso |
|---|---|
| `cd Restaurante_Ejecutable && DISPLAY=:0.0 java -jar Restaurante.jar` | Arranque manual |
| `./iniciar_restaurante.sh` (root o `Restaurante_Ejecutable/`) | Lanza `Restaurante.jar` del dir actual; exige Java 21 |
| `java -jar Restaurante.jar --migrate-db` | Solo valida migración de esquema y sale |
| Login demo: `admin` / `admin`. Instancia única por puerto 49200 |

## Base de datos

| Script / comando | Uso |
|---|---|
| `./inicio_fresco.sh [--sin-semana] [--forzar]` | **Destructivo**: backup → vacía 16 tablas (preserva `schema_migrations`) → siembra maestra (4 salas, 6 categorías, 20 platos, 6 mesoneros, clientes, config, admin) → semana demo 7 días + cierres → verifica. Vars: `CONTENEDOR/MYSQL_USER/MYSQL_PASS/MYSQL_DB`. Pide `SI` salvo `--forzar` |
| `MYSQL_DB=restaurante_prueba ./inicio_fresco.sh --forzar` | Probar el script en BD scratch (no toca producción) |
| `actualizar_bd.bat` | Migración segura Windows (JDBC, con postcondiciones) |
| `limpiar_para_comenzar_hoy.bat` / `.sh` | Limpieza operativa para iniciar servicio: backup previo → vacía pedidos, consumos, pagos y cierres → contador a #1 → mesas 100% libres → actualiza platos a hoy → preserva intactos 87 platos reales, clientes, salas, categorías, mesoneros, usuarios y config |
| `BD.sql` + `actualizar_bd.sql` + `db/migrations/actualizar_bd.sql` | Schema fresco + migraciones idempotentes (los 2 últimos **deben ser byte-idénticos**: lo exige `InstaladoresSeguridadTest`) |

## Instalación Windows (un clic, requieren Admin)

| Script | Uso |
|---|---|
| `instalacion_completa_un_clic.bat` | Todo en uno: MySQL + BD + ejecutable |
| `instalar_mysql_y_bd.bat` / `.ps1` | Solo MySQL + base nueva |
| `generar_restaurante_ejecutable.bat` / `.ps1` / `.sh` | Recompila y reempaqueta `Restaurante_Ejecutable/` por plataforma |
| `instalar_acceso_directo.bat` / `.ps1` | Crea acceso directo del sistema |
| `iniciar_restaurante.bat` | Arranque en Windows |
| `listar_impresoras.bat` | Lista impresoras (nombre, default, puerto, driver) para configurar tickera |
| `configurar_mysql.ps1` | Configura/eleva MySQL en Windows |

## Modelo de Dominio: Vocabulario Unificado (Comanda vs. Pedido/Cuenta vs. Factura)

Para evitar confusiones entre el lenguaje coloquial ("el cliente hizo un pedido") y la arquitectura técnica del sistema, se fija la siguiente terminología canónica:

| Concepto | Qué representa | Alcance / Impresión | Impacto Fiscal y Caja |
|---|---|---|---|
| **Comanda** (Ronda de cocina / Marcha) | Orden operativa enviada a cocina o barra. Una mesa puede generar múltiples comandas durante su estancia (ronda 1: bebidas/entradas; ronda 2: platos fuertes; ronda 3: postres/cafés). | **Solo ítems nuevos:** Al imprimir comanda en cocina (`ServicioComandaCocina`), se imprime **únicamente el incremento de la ronda en curso**. Los pedidos/ítems anteriores ya fueron marchados y servidos; nunca se reimprimen para no duplicar platos en cocina. | Cero impacto contable o de caja. Es una orden de producción interna. |
| **Cuenta de Mesa / Pedido** (Consumo acumulado) | El folio o contenedor que acumula todas las comandas/rondas de la mesa (`pedidos` y `detalle_pedidos` en estado `PENDIENTE`). | **Pre-cuenta:** Ticket de revisión informativo no fiscal que se entrega a los comensales para verificar el consumo antes de pagar ("la cuenta, por favor"). | No es venta definitiva. Puede modificarse, corregirse o cancelarse si la mesa se desocupa. |
| **Factura** (Venta definitiva / Cobro) | El documento fiscal y contable formal emitido al momento del cobro (`FINALIZADO`). | **Factura / Ticket Fiscal:** Documento con correlativo, fecha/hora de cobro, desglose estricto de Base Imponible, Exento, IVA y métodos de pago. | **Vinculante:** Registra ingreso en caja (`pedido_pagos`), devenga IVA, libera la mesa y alimenta de forma inmutable el Cierre Z del día. |

### Los 3 Tipos de Anulación (Diferenciación estricta)
1. **Anulación de Ítem / Comanda (Cocina):** Cancelación de un plato antes o durante su preparación (por falta de insumo o cambio de opinión inmediato). Resta el plato de la cuenta de la mesa y notifica a cocina. No toca dinero ni caja.
2. **Cancelación de Cuenta / Fuga (Mesa Abierta):** Mesa que se retira sin pagar o servicio abortado antes de facturar. Se libera la mesa y se anula el pedido pendiente sin registrar pagos. Cero impacto en caja o impuestos.
3. **Anulación de Factura (Venta Cobrada):** Venta que ya fue pagada e ingresada en caja. Su anulación requiere contra-asiento formal (`pedido_saldos_movimientos`), reversión en caja y ajuste contable para que el Cierre Z y la auditoría cuadren al centavo.

## Notas para agentes
- DB prod: `docker exec restaurante-db-1 mysql -uadmin -padmin restaurante -e "..."`. No hay `mysql` CLI en el host.
- **Tests E2E**: SIEMPRE deben ejecutarse en una ventana virtual aislada (mediante `./e2e_virtual.sh [clase]` sobre Xvfb `:99`), con teclado y mouse virtuales propios, sin interferir jamás con el escritorio ni el cursor del operador. El wrapper solo acepta la BD local desechable `restaurante_test` en `127.0.0.1:3307`; no se ejecutan E2E contra producción.
- **Tests E2E — obligatorio: solo a través de la interfaz**: todo test E2E DEBE interactuar **exclusivamente con la UI de la aplicación**, simulando exactamente lo que haría un operador humano: pulsar botones, marcar/desmarcar checkboxes y radios, escribir en campos de texto, hacer clic, seleccionar en combos/tablas y navegar entre paneles. **PROHIBIDO** invocar directamente el código de negocio (DAO, repositorios, servicios, controladores, modelos) ni tocar la BD para preparar o forzar el estado: la preparación, la acción y la verificación deben ocurrir por la interfaz (efectos observables en pantalla). La intención del E2E es validar el sistema tal como lo usa el operador, por lo que cualquier atajo por código invalida la prueba.
- **Instalación limpia y permisos mínimos en E2E**: para probar bootstrap en una BD virgen, usar `./e2e_bootstrap_virtual.sh`; crea y elimina su propio MySQL efímero y no debe reutilizar la instancia de servicio. El smoke de runtime concede únicamente `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES` sobre `restaurante_test`; no ampliar privilegios globales para hacer pasar la prueba.
- **Salida de impresión en E2E**: usar `ACCION_IMPRESION=IMPRIMIR` junto al `lp` falso preparado por el runner. No usar `VISOR` en jornadas automatizadas: el visor externo puede quedar delante de la UI y bloquear la interacción del Robot. Esto solo simula aceptación del spooler; no acredita impresión física.
- **Evidencia y gates de producción**: en `bitacora-contexto-produccion.md`, encabezar cada actualización con `snapshot DD/MM/AAAA HH:MM` y registrar toda evidencia como `comando → archivo de salida → PASS/FLAKY/FAIL`, con el alcance que realmente demostró. Un PASS parcial no cierra un gate integral; los requisitos de Windows, impresora física, restauración u otros entornos externos permanecen pendientes hasta contar con evidencia del entorno correspondiente. Registrar decisiones y hallazgos verificables, no razonamiento interno privado.
- No reiniciar la app en horario de servicio sin pedirlo; el jar desplegado se activa al próximo arranque.
- Los tests unitarios tocan la BD real (vía `.env`); no correr `ant test` tras sembrar datos limpios salvo necesario.
- `pkill -f "[R]estaurante.jar"` (con corchetes; el patrón plano se auto-matchea).
- **Esquema de BD vs Clave/Valor**: Antes de agregar una columna a la base de datos (especialmente en `config`), evaluar primero si se puede resolver la necesidad con el modelo clave/valor de configuración (`configuracion_sistema` vía `ConfigClaves` y `ConfiguracionRepositorio`), evitando alteraciones de esquema (DDL) innecesarias.
- **Diseñador Swing**: `src/Vista/Sistema.java` tiene edición manual fuera de `Sistema.form` (scroll lateral `scrollBarraLateral`, `btnDashboard`, retoques de contraste/mesoneros). No regenerar desde Matisse o se pierde; si se edita el `.form`, remodelar el `JScrollPane` allí.
- **Precisión monetaria (política)**: la unidad del dinero es **2 decimales** de punta a punta (BD `DECIMAL(*,2)`, setters de dominio, `PedidosDao.importePersistible` y `CalculoMonetarioPedido.validarMontoCalculado` fallan si `scale > 2`). La aritmética intermedia usa **más decimales** (`CalculoMonetarioPedido.ESCALA_INTERMEDIA = 8`, y en SQL `ROUND(..., 2)` en cada `total * tasa`) y se redondea **una sola vez en el límite de línea/pedido** (HALF_UP). Regla derivada: cualquier conversión tasa-aplicada debe redondearse **por pedido** (`ROUND(total*tasa,2)`) antes de acumular; así `Σ desglose == cabecera` (métodos y salas) por partición. La presentación sigue en 2 decimales (`%.2f` / `setScale(2)`). No subir decimales a la BD para "cuadrar" reportes: el descuadre es de agregación, no de precisión.
- **Transacciones JDBC**: al devolver una conexión pooled en `autocommit=false`, se revierte como protección; si se detectaron escrituras desde el último `commit()`/`rollback()`, se registra `SEVERE` y `close()` propaga `SQLException`. Todo flujo propietario de una transacción debe confirmar o revertirla explícitamente.
- **Inyección de dependencias (DI)**: de ahora en adelante, las dependencias nuevas o modificadas se inyectan por constructor; el ensamblaje de implementaciones concretas se hace en el punto de composición (`restaurante.Restaurante`). Evitar instanciar DAOs, repositorios o servicios dentro de vistas y lógica de negocio. Preferir interfaces cuando ayuden a sustituir una dependencia o aislarla; migrar el código legado de forma incremental, sin introducir un framework DI ni hacer refactorizaciones masivas sin necesidad concreta.
- **Scripts de Windows (.bat, .ps1, .vbs) — Reglas estrictas de generación**:
  - **Terminaciones de línea (CRLF obligatorio)**: TODO archivo `.bat`, `.cmd`, `.ps1` y `.vbs` DEBE guardarse con terminaciones de línea Windows **CRLF (`\r\n`)**. En Linux/Git, guardar con LF causa que `cmd.exe` descalibre el puntero de archivo y mutile comandos al inicio de cada línea (`echo` $\rightarrow$ `ho` $\rightarrow$ `o`, `set` $\rightarrow$ `t`, `if not` $\rightarrow$ `not`). `.gitattributes` fija `eol=crlf`. Al generar o editar desde Linux, ejecutar siempre: `sed -i 's/\r$//; s/$/\r/' <archivo>`.
  - **Scripts Batch (`.bat` / `.cmd`)**:
    - **PROHIBIDO `chcp 65001`**: no incluir cambio de página de códigos dentro de scripts `.bat`. En `cmd.exe` desincroniza el buffer de lectura de líneas.
    - **ASCII puro sin BOM**: texto en inglés o español sin acentos (`á`, `é`, `í`, `ó`, `ú`, `ñ`, `¡`, `¿`). Sin Byte Order Mark.
  - **Scripts PowerShell (`.ps1`)**:
    - **UTF-8 CON BOM obligatorio (`\xef\xbb\xbf`)**: Windows PowerShell 5.1 (por defecto en Windows 10/11) asume ANSI/Windows-1252 si el archivo carece de BOM, corrompiendo tildes y caracteres especiales en comparaciones y salidas.
    - **Salida UTF-8 en consola**: incluir al inicio:
      ```powershell
      [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
      $OutputEncoding = [System.Text.Encoding]::UTF8
      ```
  - **Scripts VBScript (`.vbs`)**:
    - **ASCII puro sin BOM**: `wscript.exe` / `cscript.exe` no soporta UTF-8 sin BOM. Todo diálogo (`MsgBox`) y texto debe ser ASCII plano sin caracteres multibyte.


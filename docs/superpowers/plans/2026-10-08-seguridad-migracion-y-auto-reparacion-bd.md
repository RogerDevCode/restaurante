# Plan de implementación: seguridad de migración y auto-reparación de la base de datos

> **Para agentes que ejecuten este plan:** trabajar tarea por tarea, conservar el respaldo de revisión entre etapas y no combinar la ruta de instalación nueva con la de actualización existente.

**Objetivo:** Evitar que la instalación o auto-migración borre datos del restaurante, permitir actualizaciones reintentables y conservar la configuración al pasar de columnas a clave/valor.

**Arquitectura:** `BD.sql` será solo la inicialización de una base nueva y vacía. Las instalaciones existentes avanzarán mediante migraciones incrementales registradas, precedidas por un respaldo verificable; la aplicación no reparará una base desconocida reconstruyéndola. La migración `config` → `configuracion_sistema` preservará el origen, verificará los valores y tendrá una fuente de verdad definida.

**Tecnologías:** Java 21, JDBC/MySQL, SQL, Windows Batch y PowerShell, Ant/JUnit y pruebas de integración MySQL existentes.

## Restricciones globales

- Ninguna ruta automática de instalación, actualización o arranque importará `BD.sql` en una base que ya tenga datos.
- Antes de ejecutar cambios DDL en una BD existente, el proceso debe crear y validar un respaldo; esta barrera se implementa antes que cualquier cambio de migraciones.
- Ninguna migración eliminará ni truncará tablas de negocio (`platos`, `pedidos`, `detalle_pedidos`, `clientes`, `usuarios`, `salas`).
- Si no se puede verificar o crear un respaldo, la actualización se detendrá antes de cambiar el esquema.
- Los errores SQL no se presentarán como una actualización exitosa ni dispararán una inicialización destructiva de reserva.
- Los cambios DDL de MySQL no se tratarán como transacciones reversibles; el plan de recuperación será respaldo restaurable más migraciones repetibles.
- Mantener sincronizados `actualizar_bd.sql` y `db/migrations/actualizar_bd.sql`, o establecer cuál es el artefacto fuente y cómo se genera el otro.

## Mapa de archivos

- `instalacion_completa_un_clic.bat`, `instalar_acceso_directo.ps1`: orquestación de la instalación y selección inequívoca entre instalación nueva y actualización.
- `instalar_mysql_y_bd.bat`: inicialización de una base realmente nueva; protección contra reimportar `BD.sql` sobre datos.
- `actualizar_bd.bat`: respaldo, aplicación incremental, comprobación de errores y resultado honesto.
- `BD.sql`: esquema/semillas de instalación limpia; revisar que su uso quede restringido al caso nuevo, no convertirlo en reparador.
- `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `db/migrations/014_configuracion_clave_valor.sql`: artefactos legacy a inventariar, migrar a la fuente canónica y retirar del flujo si quedan duplicados.
- `src/infraestructura/MigradorEsquemaJdbc.java`: arranque, detección de esquema incompleto/desconocido, ledger y migración JDBC segura.
- `src/Servicio/ServicioRespaldoBaseDatos.java`: crear/verificar respaldo antes de DDL y mantener restauración explícita.
- `src/restaurante/Restaurante.java`: orden de inicio y manejo de modo mantenimiento cuando migrar falla.
- `src/Modelo/ConfiguracionSistemaDao.java`, `src/Servicio/ConfiguracionServicio.java` y `src/Modelo/ConfiguracionRepositorio.java`: política de fuente de verdad y compatibilidad temporal para claves migradas.
- `test/infraestructura`, `test/Servicio`, `test/integracion/MySqlIntegrationIT.java`: pruebas unitarias, de scripts/orquestación e integración de preservación de datos.
- `build.xml`: usar los targets ya existentes `test` e `integration-test`; no agregar dependencias salvo que la implementación lo requiera.

---

## Fase 0: inventario y línea base

### Tarea 0.1: confirmar comportamiento y artefactos publicados

**Archivos:** leer los scripts y clases del mapa; no modificar `BD.sql` todavía.

- [ ] Comparar `actualizar_bd.sql` con `db/migrations/actualizar_bd.sql` y anotar cuál usa el `.bat`, cuál empaqueta la aplicación y si ambos son idénticos.
- [ ] Enumerar todos los caminos que ejecutan `BD.sql`; incluir fallos de autenticación, error SQL parcial y ausencia de `mysql.exe`.
- [ ] Identificar cuándo `Restaurante.main` llama al migrador y qué servicios pueden llamarlo también.
- [ ] Ejecutar `ant -q test` y registrar resultado de referencia.
- [ ] Ejecutar `ant -q integration-test` solo con el entorno MySQL aislado ya configurado; registrar si no está disponible.

**Aceptación:** queda documentado el flujo de cada instalador, las versiones reales de los scripts y la línea base de pruebas antes de los cambios.

### Tarea 0.2: cerrar primero el camino de DDL al inicio y definir el respaldo pre-migración

**Archivos:** `src/restaurante/Restaurante.java`, `src/infraestructura/MigradorEsquemaJdbc.java`, `src/Servicio/ServicioRespaldoBaseDatos.java`, pruebas correspondientes.

- [ ] Cambiar primero el orden de arranque: hoy `Restaurante.main` ejecuta el migrador sincrónicamente y el respaldo diario después, en otro hilo. Hasta que exista una barrera previa, el migrador no debe ejecutar DDL en una base existente.
- [ ] Hacer que el migrador ejecute una operación de respaldo después de detectar migraciones pendientes y antes del primer DDL; una BD realmente nueva y vacía puede seguir el flujo de inicialización controlada.
- [ ] Si el respaldo falta o falla, abortar inicio sin modificar el esquema. El respaldo diario posterior no satisface este requisito.
- [ ] Corregir nombres para que nunca colisionen/sobrescriban; crear a archivo temporal y publicar el respaldo definitivo solo tras cerrarlo y validarlo.
- [ ] Precisar cobertura: tablas y motores soportados, vistas, triggers, rutinas y eventos. No llamarlo “completo” si omite objetos presentes.
- [ ] Un tamaño/footer correctos no prueban consistencia: añadir checksum y restauración periódica en BD desechable; verificar engines no transaccionales y detener escrituras o fallar si no se puede garantizar consistencia.
- [ ] Probar el orden `migración pendiente → respaldo validado → primer DDL` y probar que un error de backup deja cero DDL ejecutados.

**Aceptación:** ninguna ruta Java puede ejecutar DDL en una base existente antes de pasar por la barrera de respaldo. Completar esta tarea antes de cambiar migraciones.

## Fase 1: blindar los instaladores Windows

### Tarea 1.1: introducir detección segura de base existente

**Archivos:** `instalar_acceso_directo.ps1`, `instalacion_completa_un_clic.bat`.

- [ ] Antes de cualquier importación, comprobar si la base ya existía y enumerar sus objetos; no clasificarla como nueva solo porque sus tablas no tengan filas.
- [ ] Permitir ruta nueva únicamente si la base no existía, o si hay un marcador inequívoco de inicialización incompleta creado por este instalador y no quedan objetos de usuario. Ante ambigüedad, detenerse.
- [ ] Para una BD existente, elegir actualización explícitamente; ningún conteo de filas autoriza importar `BD.sql`.
- [ ] Impedir que errores al aplicar `actualizar_bd.sql` pasen al bloque que importa `BD.sql`.
- [ ] En actualizaciones, si falla backup, conexión, migración o validación, terminar con código distinto de cero, conservar logs y no continuar al siguiente paso.
- [ ] El éxito solo se declara después de comprobar versión de esquema y consultas de integridad mínimas.

**Aceptación:** sobre una BD existente con platos/pedidos, ningún camino del instalador ejecuta el SQL de `BD.sql`, incluyendo cuando una migración falla a mitad de ejecución.

### Tarea 1.2: proteger el instalador directo de MySQL

**Archivos:** `instalar_mysql_y_bd.bat`, `BD.sql`.

- [ ] Hacer que el instalador directo consulte y clasifique la BD antes de importar.
- [ ] Permitir importar `BD.sql` únicamente cuando `restaurante` no existía, o cuando un marcador inequívoco de este instalador confirma que la creación quedó incompleta y no hay objetos de usuario.
- [ ] Si existe una BD con cualquier tabla/dato, no ofrecer silenciosamente inicialización; encaminar a actualización o terminar sin escribir.
- [ ] Revisar privilegios y manejo de credenciales: no cambiar la clave de una instalación existente como efecto colateral de actualizar; no imprimir secretos en consola/logs.
- [ ] Propagar al `.bat` el código de salida real de PowerShell y evitar el mensaje de éxito si falla cualquier etapa.

**Aceptación:** contra una BD existente (poblada o vacía pero con objetos), el instalador nuevo rechaza o deriva a actualización y se comprueba que nunca invoca `BD.sql`; una BD realmente nueva sí crea el esquema esperado.

### Tarea 1.3: volver el actualizador una operación segura y auditable

**Archivos:** `actualizar_bd.bat`, `instalar_acceso_directo.ps1`.

- [ ] Invocar el modo CLI de migración de la aplicación (ver Tarea 2.1), que usa la misma implementación JDBC que el arranque; no ejecutar una segunda secuencia SQL independiente.
- [ ] Crear respaldo no sobrescribible antes del primer DDL con `ServicioRespaldoBaseDatos`; si la aplicación o el respaldo JDBC no están disponibles, abortar.
- [ ] Validar cobertura e integridad del respaldo según Tarea 0.2; el tamaño por sí solo no es prueba suficiente.
- [ ] Detenerse ante el primer error SQL y comprobar el código de salida de cada proceso hijo.
- [ ] Eliminar el texto fijo “SIN PERDIDA DE DATOS”; reportar “migración aplicada y validaciones aprobadas” solo después de validaciones, y mostrar ruta del respaldo.
- [ ] Eliminar el reintento que cambia a `root` y el aprovisionamiento inesperado de usuarios de la ruta de actualización; credenciales/permisos deben resolverse antes de modificar datos.

**Aceptación:** un error SQL o credenciales incorrectas detienen el flujo, no generan éxito y no ejecutan `BD.sql` como recuperación.

## Fase 2: migraciones rastreables, reintentables y compatibles

### Tarea 2.1: agregar ledger de migraciones

**Archivos:** nuevos scripts en `db/migrations/`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `src/infraestructura/MigradorEsquemaJdbc.java`.

- [ ] Usar un único motor canónico: `MigradorEsquemaJdbc` con migraciones JDBC versionadas; convertir también los cambios requeridos actualmente por `actualizar_bd.sql` (incluido su procedimiento delimitado) a esos pasos. Añadir un modo CLI de mantenimiento en `src/restaurante/Restaurante.java` que permita a `.bat` y PowerShell invocar exactamente esa entrada Java sin abrir la interfaz.
- [ ] Definir `schema_migrations` con versión, nombre, checksum, estado, paso actual, fecha de inicio y aplicada; un checksum diferente para una migración aplicada falla cerrado y se corrige con una migración nueva.
- [ ] Cada paso tiene ID estable, operación repetible y postcondición consultable. Al reanudar, verificar postcondiciones para cubrir una caída entre DDL y escritura del ledger.
- [ ] Adquirir un lock en MySQL para serializar migraciones de procesos/JVM distintos; `synchronized` solo protege una JVM.
- [ ] Confirmar una versión solo tras validar todos sus pasos; no depender de caché en memoria como historial.
- [ ] Retirar `actualizar_bd.sql` del flujo de ejecución o generarlo desde la fuente canónica; añadir una verificación para impedir que se mantenga como segundo runner divergente.

**Aceptación:** la segunda ejecución no cambia datos; interrupción entre DDL y ledger se recupera por postcondiciones; dos procesos se serializan; checksum alterado falla cerrado.

### Tarea 2.2: impedir reparación sobre una base desconocida

**Archivos:** `src/infraestructura/MigradorEsquemaJdbc.java`, `src/restaurante/Restaurante.java`.

- [ ] Al inicio, clasificar la BD como nueva, compatible y conocida, o existente pero no soportada/desconocida; cero filas no prueba que sea nueva.
- [ ] Ejecutar inicialización base solo si no existía la BD o hay un marcador inequívoco de instalación nueva y no hay objetos de usuario. Para existente, permitir únicamente migraciones enumeradas.
- [ ] Si falta una tabla de negocio esencial o hay versión futura/desconocida, detener inicio con error accionable; no crear tablas de negocio vacías encima de datos parcialmente existentes.
- [ ] Mantener la sincronización concurrente/reintentos seguros, pero retirar dependencia de una caché en memoria como registro de qué versión fue aplicada.
- [ ] Evitar que el fallo de auto-migración quede absorbido por un DAO o que la aplicación abra pantallas que escriban con esquema parcial.

**Aceptación:** BD nueva se inicializa; BD válida se actualiza; BD desconocida/incompleta no se reconstruye automáticamente ni acepta operaciones de escritura.

## Fase 3: migrar la configuración sin perder valores

### Tarea 3.1: definir fuente de verdad y compatibilidad

**Archivos:** `src/Modelo/ConfiguracionSistemaDao.java`, `src/Servicio/ConfiguracionServicio.java`, `src/Modelo/ConfiguracionRepositorio.java`, `src/infraestructura/MigradorEsquemaJdbc.java`.

- [ ] Definir una política de precedencia para instalaciones antiguas, donde solo exista `config`, y para instalaciones ya migradas, donde existe `configuracion_sistema` con cambios posteriores.
- [ ] Hacer la copia inicial solo de claves ausentes y nunca reemplazar una clave KV válida con valores por defecto o valores legacy obsoletos durante el arranque.
- [ ] Mantener las columnas y tabla legacy durante la ventana de compatibilidad; no borrarlas en esta entrega.
- [ ] Hacer que las escrituras de transición actualicen ambas representaciones dentro de una operación JDBC coherente, o migrar lectores/escritores completamente a KV en una sola versión. No permitir escritura parcial silenciosa.
- [ ] Validar tipos y valores antes de importar (decimal, booleano, texto), y preservar valores válidos como tasa/IVA/impresora/nombre/documento/retención.

**Aceptación:** actualización repetida conserva el valor KV posterior a la migración; copia inicial conserva el valor cargado por el dueño en `config`; las claves faltantes reciben defaults sin reemplazar las existentes.

### Tarea 3.2: validar la migración SQL de configuración

**Archivos:** `db/migrations/014_configuracion_clave_valor.sql` y scripts empaquetados.

- [ ] Corregir el camino actual: `actualizar_bd.sql` crea KV e inserta defaults pero no copia los valores existentes de `config`; esos defaults podrían impedir la copia posterior del migrador JDBC. Copiar valores legacy válidos primero y aplicar defaults solo a claves aún ausentes.
- [ ] Evitar referencias a columnas opcionales inexistentes; detectar columnas y mapearlas condicionalmente, o asegurarlas en una migración anterior.
- [ ] Comprobar tabla `config` llena, vacía, con columnas opcionales ausentes y ya migrada, con los mismos pasos que usa `.bat` y arranque.
- [ ] Añadir validaciones de filas/claves requeridas y dejar `config` intacta.
- [ ] Ejecutar el mismo SQL dos veces y verificar que valores no cambian en la segunda ejecución.

**Aceptación:** los ocho parámetros mapeados se copian de forma determinista y ninguna sentencia de la migración elimina/modifica platos, pedidos ni datos financieros.

## Fase 4: respaldo, recuperación y pruebas de regresión

### Tarea 4.1: reforzar el respaldo previo a migraciones

**Archivos:** `src/Servicio/ServicioRespaldoBaseDatos.java`, `test/Servicio/ServicioRespaldoBaseDatosTest.java`.

- [ ] Exponer una operación usada por el flujo de migración para crear un respaldo obligatorio y validar checksum/cobertura; esta tarea complementa, no precede, la barrera inicial de Tarea 0.2.
- [ ] Crear archivos con nombre único y creación exclusiva; nunca abrir/truncar un respaldo existente.
- [ ] Comprobar engines no transaccionales y definir cómo se detienen escrituras o cómo se obtiene respaldo consistente.
- [ ] Mantener restauración como acción explícita; nunca restaurar un snapshot automáticamente al detectar un error ordinario de arranque.
- [ ] Probar recuperación desde respaldo con tablas y filas representativas en un schema de integración desechable.

**Aceptación:** el sistema no inicia una migración sin respaldo válido y puede restaurar el respaldo en una base aislada verificando filas.

### Tarea 4.2: añadir pruebas adversariales de scripts y migración

**Archivos:** ampliar `test/infraestructura`, `test/Servicio`, `test/Vista/ExhaustivoDashboardUsuarioTest.java`, `test/integracion/MySqlIntegrationIT.java`; agregar prueba de contrato para scripts de Windows si no existe harness.

- [ ] Cubrir DB nueva vacía, DB existente poblada, DB existente sin filas pero con esquema, DB desconocida, error de permisos, MySQL caído y error en migración intermedia.
- [ ] En cada caso poblado, insertar platos, pedidos, detalles, clientes y configuración centinela; comparar sus claves/valores antes y después.
- [ ] Simular error después de una sentencia DDL y verificar que no se importa `BD.sql`, no se marca versión aplicada y el reintento converge.
- [ ] Ejecutar dos actualizaciones concurrentes y verificar que no duplican seeds ni ejecutan dos veces cambios de datos.
- [ ] Probar migración `config` → KV en primera corrida, segunda corrida, conflicto (KV más nuevo), valor inválido y columna ausente.
- [ ] Ejecutar scripts con stubs de `mysql.exe`, `mysqldump.exe` y `java` que registren argumentos y códigos de salida: rama upgrade nunca invoca `BD.sql`; solo la rama fresh autorizada puede hacerlo.
- [ ] Probar el flujo real de `instalar_acceso_directo.ps1` y el empaquetado en `generar_restaurante_ejecutable.ps1`, incluida la propagación de errores PowerShell al `.bat`; el análisis estático no detecta `$LASTEXITCODE` obsoleto ni todos los errores de cmdlets.
- [ ] Añadir pruebas de consistencia entre `actualizar_bd.sql` y el árbol `db/migrations` o comprobar el artefacto generado en CI.

**Aceptación:** los casos de preservación y fallo pasan; las pruebas existentes siguen pasando; pruebas MySQL usan únicamente una BD aislada y nunca la instalación local del dueño.

- [ ] Antes de permitir limpieza `docker compose down --volumes`, verificar en `build.xml` que el proyecto y volumen de `integration-test` son exclusivos y desechables.

## Fase 5: despliegue gradual

### Tarea 5.1: probar actualización realista antes de liberar

**Archivos:** documentación de despliegue, plantilla de caso de prueba; scripts finales.

- [ ] Preparar una copia de una BD representativa con platos, ventas históricas, configuración legacy y usuarios; eliminar o anonimizar datos personales.
- [ ] Correr el instalador de un clic como actualización, interrumpirlo en puntos controlados y reintentar.
- [ ] Restaurar el respaldo en otra instancia y confirmar conteos, importes, claves de configuración y relaciones principales.
- [ ] Publicar el procedimiento de recuperación: detener app, conservar logs/respaldo, restaurar en copia o instancia controlada, verificar y luego reabrir.

**Aceptación:** evidencia de actualización/reintento/restauración aprobada; el despliegue nunca apunta a `BD.sql` para actualizar datos existentes.

## Comandos de verificación

- `ant -q test` — todas las pruebas unitarias deben terminar con `BUILD SUCCESSFUL`.
- `ant -q integration-test` — debe terminar con `BUILD SUCCESSFUL` usando solo el MySQL de integración desechable.
- Antes de integración, revisar que `docker compose down --volumes` solo pueda eliminar recursos efímeros de tests.
- Revisar scripts SQL y ramas de instalación para ausencia de `DROP`, `TRUNCATE` y `DELETE` no acotados en rutas de actualización.
- Comparar snapshots de datos centinela antes/después; el conteo aislado no basta para probar preservación de valores y relaciones.

## Criterios de salida

1. Un fallo de actualización jamás deriva en importar `BD.sql` sobre una BD existente.
2. Un respaldo completo y verificable precede cualquier cambio DDL de una instalación existente.
3. Las migraciones son versionadas, repetibles y detectan historial alterado o esquema desconocido.
4. La migración de configuración conserva los valores del dueño y el origen legacy durante la transición.
5. Los datos de platos, pedidos, detalles, clientes, usuarios y cierres se verifican antes/después en integración.
6. Todo fallo deja código de salida no exitoso, versión no confirmada y mensaje/log con el siguiente paso seguro.

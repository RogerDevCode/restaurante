# Plan de QA y estabilización — Restaurante

## Objetivo

Estabilizar el entorno de base de datos y corregir los riesgos que pueden provocar errores de conexión, datos incompletos o inconsistentes en pedidos, credenciales expuestas y fallos de interfaz. El plan registra tanto los cambios implementados como la verificación pendiente en ejecución.

## Requisitos transversales añadidos

- **Criterio transversal de errores, literal y verificable:** se revisará el 100 % del código de producción Java y cada ruta de ejecución (incluidos `catch`, `finally`, callbacks Swing, hilos, tareas asíncronas y límites JDBC/archivo/PDF/configuración). Ningún `Exception` ni `Error` puede descartarse, ignorarse, convertirse en éxito, lista vacía, `null` o valor por defecto, ni dejar una operación a medio confirmar. Solo se permite recuperación explícita con resultado coherente, o propagación con tipo, contexto y causa preservados hasta una única frontera responsable. Esa frontera registra una sola vez el incidente con stack trace/cadena causal y contexto operativo, devuelve/muestra un resultado claro y deja estado consistente. La severidad distingue validaciones, credenciales rechazadas, conflictos de negocio y cero filas (`INFO`/`WARNING`) de fallos técnicos (`SEVERE`); nunca se registran secretos. Un fallo del logger debe informarse por un canal de emergencia durable; si no hay canal utilizable, el arranque/operación falla de forma visible y no se declara el evento registrado. La aceptación requiere inventario de rutas, revisión adversarial y pruebas inducidas que demuestren propagación, causa, log único, severidad, resultado presentado y estado consistente. No se considera completo por una búsqueda estática ni por compilar.
- **Archivos de logs:** generar archivos separados por día calendario y mantenerlos durante un mes calendario. La purga debe usar fechas de calendario (incluyendo cambios de mes/año y años bisiestos), no una retención fija de 30 días.
- **Conversión MVC estrictamente archivo Java por archivo Java:** migrar cada archivo Java de producción individualmente siguiendo la lista de Fase 8; cada unidad de trabajo puede modificar como máximo un archivo `src/**/*.java` de producción (los tests/documentación/configuración sí pueden acompañar ese archivo). Antes de tocarlo se anota responsabilidad origen/destino y dependencias; después se compila y ejecutan pruebas pertinentes, se inspeccionan errores/logs y se actualiza su estado en la lista. No se inicia el archivo siguiente hasta aceptar el actual. Las vistas Swing solo presentan y navegan; controladores coordinan; servicios contienen casos de uso/reglas; repositorios/DAO contienen persistencia; modelos expresan dominio; infraestructura queda fuera de MVC. Las dependencias se inyectan por constructor. Renombres/eliminaciones cuentan como la migración de ese archivo y se validan sus referencias.
- **Pruebas de MySQL:** además de pruebas unitarias puras sin BD, incluir pruebas que ejerciten la base MySQL real. Por terminología QA, esas pruebas se clasifican como **integración** (no unitarias), porque dependen de JDBC, MySQL y esquema reales; se ejecutan con un target explícito `ant integration-test`. Unitarias reproducibles con Ant usan dobles/fakes y no necesitan Docker/MySQL. Las de MySQL usan una BD y volumen exclusivos, validan de forma cerrada host/puerto/nombre antes de conectar y fallan sin tocar nada si la configuración no corresponde al entorno de prueba. Nunca ejecutar `BD.sql` destructivo sobre la BD de desarrollo/usuario.

## Estado de ejecución

- Compose quedó `healthy` con MySQL 8.4.11; el usuario de aplicación consultó las seis tablas y se importaron los datos iniciales.
- `Conexion.java` ahora toma configuración de variables de entorno o `.env` y usa el usuario de aplicación. También acepta `DB_URL`, `DB_USER` y `DB_PASSWORD` para sobreescritura. Comprobación JDBC: conexión al catálogo `restaurante`, el DAO devuelve 3 platos de hoy y el usuario demo autentica con rol Administrador.
- La consulta de platos usa parámetros y cierra recursos; la fecha del menú se calcula al momento de consultar y registrar.
- El guardado de pedidos ahora inserta encabezado y detalles en una sola transacción, recupera el ID con `getGeneratedKeys()` y propaga fallos. `ant clean compile` fue exitoso tras el cambio; falta probar el flujo con la interfaz y verificar rollback ante una falla real de detalle.
- La base activa no tenía mesas duplicadas con pedido pendiente. Se aplicó la migración de unicidad por sala/mesa; el índice y las columnas generadas están presentes. La compilación estática pasó; queda probar dos intentos concurrentes desde sesiones separadas.
- Los DAO de usuarios, platos, salas y pedidos ya no comparten conexiones/sentencias/resultados como campos; cada consulta los cierra con `try-with-resources`. Los fallos SQL se envuelven en excepciones de aplicación que conservan y registran la causa; login y alta de pedidos muestran mensajes de error. `ant clean compile` pasó. Falta propagar estos errores a todas las acciones/interfaz y eliminar manejos silenciosos en el resto del código.
- Se añadió un manejador global de excepciones Swing/hilos no atendidas, se pasó el arranque principal al EDT y las operaciones CRUD conocidas ya verifican su resultado antes de mostrar éxito o limpiar formularios. La compilación limpia pasó; falta revisión de todos los paquetes y manejo específico por flujo/controlador.
- Se configuró `java.util.logging` con archivos `logs/restaurante-AAAA-MM-DD.log`, apertura/rotación diaria y purga de archivos anteriores a `hoy.minusMonths(1)` (la fecha límite se conserva). `logs/` está excluido de Git. Pendiente prueba unitaria de los límites de calendario dentro de la fase de tests.
- `ManejadorErroresSwing` ya registra/muestra excepciones no atendidas en eventos y otros hilos; los eventos CRUD comprueban resultados y conservan datos ante fallos. Inventario de `catch` y llamadas DAO realizado en `src`; la compilación limpia fue exitosa. Falta trasladar el manejo específico a controladores y cubrir servicios con pruebas.
- Las cargas de tablas y del pedido para finalizar ahora consultan antes de reemplazar el contenido visible, por lo que un fallo SQL conserva el último estado válido. Si el pedido solicitado ya no existe, el DAO propaga y registra un error explícito en lugar de devolver un objeto vacío. Se retiró el limpiador de tabla obsoleto. Falta continuar el mismo patrón en todos los flujos y agregar pruebas.
- La consulta de configuración ahora falla de forma explícita cuando no hay registro; la generación de PDF también rechaza configuración o detalles ausentes. La validación de login ya informa campos vacíos, se corrigieron condiciones de campos obligatorios y se agregaron guardas para selección de filas y finalización sin pedido. El manejador de eventos Swing registra también `Error` antes de propagarlo, y un fallo al iniciar el logger se registra por el logger JUL disponible antes de abortar el arranque. `ant clean test` verifica en unit tests las fechas límite; faltan escenarios de error en ejecución.
- Se añadió rechazo registrado para argumentos nulos/inválidos en los DAO y el error de FK al borrar una sala se traduce a un mensaje concreto. Las escrituras que no afectan exactamente una fila ahora dejan una advertencia en el log y conservan el resultado booleano para que la UI informe que no hubo cambios. Las ediciones de sala/plato ya no terminan sin aviso cuando faltan campos.
- Los intentos de login rechazados se registran como advertencia sin incluir la contraseña ni otros datos sensibles. El guardado transaccional valida que la BD entregue un ID generado positivo antes de asociar los detalles.
- Se integró JUnit 4.13.2 con Hamcrest 1.3 en el classpath de pruebas de Ant, sin agregarlos al runtime de la aplicación. `ant clean test` ejecutó 6 pruebas unitarias sin MySQL: registro de validaciones, trazabilidad de escrituras sin filas afectadas, rechazo de argumentos inválidos antes de conectar, límites de retención mensual (cambio de año y febrero bisiesto), creación/rotación del archivo diario y purga conservando la fecha límite. Queda ampliar cobertura de errores JDBC/UI y reglas de negocio cuando se extraigan servicios MVC.
- La revisión adversarial de 31 archivos detectó consumo de `RuntimeException` en EDT, posible doble log para `Error`, clasificación `SEVERE` de un conflicto esperado y ausencia de canal durable cuando falla el handler. Esta sesión aplicó correcciones parciales descritas abajo; faltan pruebas dinámicas adversariales para aceptar la Fase 7.
- **Corrección de esta sesión:** `Conexion` convierte errores de lectura, ruta o sintaxis de `.env` en una `SQLException` con la causa original. `ManejadorErroresSwing.instalar()` solo queda marcado como instalado después de completar la configuración; ante un fallo permite reintentar. La nueva prueba de `.env` mal formado y las existentes pasan con `ant clean test` (7 pruebas, cero fallos/errores).
- **Módulos MVC migrados:** login usa `LoginControlador` → `AutenticacionServicio` → `AutenticacionRepositorio`; el registro de pedidos usa `PedidosControlador` → `PedidoServicio` → `PedidosRepositorio`. Los DAO JDBC implementan los repositorios, y `Sistema` delega el caso de uso de registrar pedidos. Las pruebas unitarias cubren validación, delegación, rechazo/autenticación y propagación sin MySQL. Validación final: `ant clean test` (13 pruebas, cero fallos/errores).
- **Auditoría adversarial (solo lectura):** el agente revisó los 31 archivos Java y confirmó propagación/log para los DAO JDBC y los dos entrypoints. Encontró que `ManejadorErroresSwing.dispatchEvent` consume `RuntimeException` en el límite UI; un `Error` de EDT puede registrarse dos veces; y el conflicto de mesa esperable se registra como `SEVERE` aunque la UI lo clasifica como advertencia. Si falla el archivo diario, el aviso depende de `ErrorManager`/stderr, por lo que no existe garantía de persistencia en el archivo en ese escenario. `ant clean test` pasa 13 pruebas, pero no prueba estos escenarios dinámicos.
- **Proveedor JDBC extraído archivo por archivo:** se añadió `infraestructura.ProveedorConexionJdbc` con unit tests de prioridad de configuración y `.env` inválido; después, `Modelo.Conexion` quedó como fachada compatible y `ConexionTest` se ubicó en el paquete de infraestructura. La integración contra MySQL sigue pasando por esa ruta. Se añadió el modelo `Usuario` con prueba unitaria; todavía falta migrar consumidores y dejar `login` como fachada transitoria.
- **Correcciones posteriores a la auditoría, aún pendientes de aceptación adversarial:** `dispatchEvent` vuelve a propagar `Error` para evitar el log duplicado del uncaught handler; las excepciones de validación y el conflicto de mesa usan `WARNING`; `ArchivoLogDiario` implementa archivo diario de emergencia con stack trace, purga mensual y fallo cerrado si tampoco puede escribir el canal de emergencia; la selección de configuración JDBC permite sobreescritura explícita por propiedades de sistema para pruebas aisladas. `ConfiguracionLogsTest` fuerza un destino inválido y confirma que la inicialización propaga una excepción antes del arranque. `ArchivoLogDiarioTest` fuerza evento vencido y excepción unchecked del formateador; `ErrorAplicacionExceptionTest` verifica severidad/registro único y causa técnica. `ant clean test` pasó 24 pruebas unitarias, sin fallos.
- **Hallazgos de segunda revisión adversarial:** no encontró `catch` vacíos; señaló posible recreación de log vencido por eventos retrasados, unchecked del formateador fuera del canal de emergencia, Look & Feel antes de inicializar el logger, falta de prueba end-to-end Swing, conflictos SQL comunes con `SEVERE` y algunos `boolean false` tras registrar una advertencia. Los dos huecos de `ArchivoLogDiario` ya se corrigieron y tienen pruebas inducidas. Look & Feel, pruebas de Swing, clasificación del catálogo SQL y contrato booleano siguen abiertos.
- **Suite MySQL aislada verificada:** se añadió el guardia Ant antes de Docker para admitir únicamente la URL/credenciales fijas de `restaurante_test`, y comprobé que una URL de desarrollo aborta antes de invocar Docker. `ant integration-test` pasó 7 pruebas contra MySQL 8.4.11; verifica schema/catalog, autenticación/menu, CRUD de salas/platos, pedido/finalización, rollback con causa/log único, conflicto concurrente, conexión detenida y clave incorrecta. Compose retiró su contenedor, red y volumen de prueba al terminar. Se corrigió en `BD.sql` el `INSERT` de `pedidos` para excluir columnas generadas, y el privilegio requerido para el trigger de fallo se habilita solo en el servicio desechable de integración. La suite usa un trigger para forzar fallo de detalle; aún faltan otras rutas dinámicas de UI y fallos del propio sistema de logs.
- `BD.sql` ahora asigna fecha actual a los platos de ejemplo. En la BD activa se actualizaron únicamente las tres filas de demostración confirmadas con fecha antigua.
- La compilación limpia con `ant clean compile` terminó con `BUILD SUCCESSFUL`. Ant 1.10.15 está instalado. `ant run` mantiene activo `restaurante.Restaurante` sin errores de arranque; la base sigue `healthy`. Quedó una advertencia por compilar con Java 17 usando source/target 11 y notas de operaciones sin tipos genéricos. Se hicieron comprobaciones de humo JDBC y login DAO; falta interactuar visualmente con Swing y hacer regresión.
- `.env` está excluido por `.gitignore` y sus permisos locales se ajustaron a `600`. `.env.example` contiene valores ficticios.

## Diagnóstico priorizado

### Alta prioridad

1. **Guardado de pedidos sin transacción y asociación insegura de detalles (corregido).** El encabezado y los detalles se guardan ahora en una transacción y el ID se obtiene con `getGeneratedKeys()`. Queda validar importes con `BigDecimal` y probar fallos/concurrencia.
2. **Manejo de errores de conexión (corregido en acceso de datos).** `Conexion.getConnection()` propaga el fallo; todos los DAO JDBC envuelven las `SQLException` con contexto, causa y log. La capa Swing presenta errores no atendidos. Falta validación dinámica de los escenarios de desconexión y permisos.
3. **Ciclo de vida de conexiones (corregido en DAO revisados).** `LoginDao`, `PlatosDao`, `SalasDao` y `PedidosDao` abren recursos por operación y usan `try-with-resources`; no dependen de una llamada previa a otra pantalla.
4. **Contraseñas de usuario y permisos inseguros (pendiente).** La contraseña JDBC salió del código y ahora se obtiene de `.env`, pero el formulario aún precarga credenciales y las contraseñas de usuarios siguen almacenadas en texto plano. Además, el acceso de asistentes se restringe deshabilitando solo algunos botones de la interfaz.
5. **Fecha del menú diario (corregido).** La consulta conserva el filtro por fecha, pero ahora la fecha se recalcula al consultar/registrar; el SQL y las filas demo actuales tienen fecha vigente.
6. **Configuración de compilación no portable (corregido).** `nbproject/project.properties` usa ahora un único conector JDBC local, sin referencias absolutas de Windows.

### Media prioridad

7. **Confirmaciones de éxito sin validar el resultado (corregido en eventos CRUD revisados).** Los eventos consultados comprueban el booleano del DAO y avisan si no se modificó ninguna fila. Falta completar la comprobación funcional desde Swing.
8. **Validaciones incompletas (parcialmente corregido).** Se corrigieron condiciones de campos obligatorios y el login informa los campos vacíos; faltan validaciones de rango y formato en todos los formularios y reglas de negocio para cantidades/precios.
9. **Acciones de tablas sin validar selección (corregido en acciones revisadas).** Los eventos de borrar productos temporales, comentar y seleccionar filas verifican que exista una selección; falta regresión visual.
10. **Eliminación de salas con historial.** La clave foránea de pedidos puede impedir borrar una sala que tenga pedidos. La interfaz no comunica claramente el motivo ni el resultado.
11. **SQL de instalación no seguro para reutilizar en una BD con datos.** `BD.sql` contiene `DROP TABLE` e inserta datos de ejemplo. También desactiva temporalmente las comprobaciones de FK y crea tablas en un orden que conviene validar con la versión de motor elegida. No usar como script de actualización de una BD con datos reales.
12. **Bloqueo de interfaz.** Las consultas JDBC se ejecutan en acciones Swing, por lo que una BD lenta puede congelar la ventana.
13. **Búsqueda de platos (parametrizada).** El texto y la fecha usan parámetros SQL; falta definir si los caracteres `%` y `_` deben tratarse literalmente o como comodines de búsqueda.

## Opinión sobre Docker Compose

Compose es una buena opción para reproducir el entorno de desarrollo: fija la versión del motor, crea la base y permite conservar los datos en un volumen. Como se quiere usar MySQL, recomiendo fijar una versión MySQL LTS, por ejemplo MySQL 8.4, y probar desde el inicio la importación de `BD.sql` en una base desechable. El dump fue generado por MariaDB 10.4, por lo que hay que verificar colaciones, sintaxis, tipos y claves foráneas antes de considerarlo compatible.

MariaDB 10.4 puede servir como referencia temporal si MySQL 8.4 revela incompatibilidades, pero no lo elegiría como destino: su mantenimiento comunitario terminó el 18 de junio de 2024. La fecha consta en el ciclo de vida publicado por [MariaDB Foundation](https://mariadb.org/about/). MySQL 8.4 pertenece a la serie LTS según la [documentación oficial de MySQL](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html). No conviene cambiar el motor y reparar el flujo de pedidos simultáneamente.

La configuración de desarrollo en `docker-compose.yml`:

- Fijar una versión concreta de la imagen del motor, sin usar `latest`.
- Definir base, usuario y contraseñas por variables de entorno (por ejemplo, mediante `.env`, excluido del control de versiones).
- Publicar el puerto solo para desarrollo local, si se necesita acceso desde el host.
- Montar un volumen nombrado para persistir datos.
- Montar el SQL de inicialización en el directorio de init de la imagen, entendiendo que estos scripts suelen correr solo al inicializar un volumen vacío.
- Incluir un healthcheck y hacer que la aplicación espere a que la BD esté lista; `depends_on` por sí solo no garantiza disponibilidad.
- Usar un usuario de aplicación con permisos limitados, no `root`.

La configuración Java también debe leer URL, usuario y contraseña desde variables de entorno o un archivo local no versionado. Docker Compose no sustituye esa modificación: el host JDBC dentro de la red Compose será el nombre del servicio, mientras que desde una aplicación Java ejecutada directamente en el host normalmente se usará `localhost` y el puerto publicado.

## Plan de acción

### Fase 1 — Entorno reproducible

1. **Implementado:** crear `docker-compose.yml` para MySQL 8.4.11, con usuario de aplicación, variables locales, volumen persistente, healthcheck y montaje de `BD.sql` para inicialización.
2. **Validado en el entorno del usuario:** MySQL 8.4.11 inicializó `BD.sql` en un volumen nuevo, quedó `healthy` y el usuario de aplicación pudo consultar las seis tablas. Conteos observados: usuarios 1, salas 2, platos 3, pedidos 4, detalle_pedidos 8, config 1. No se detectaron errores de importación en los logs.
3. **Implementado:** limpiar `nbproject/project.properties`: quitada la ruta absoluta de Windows, retirado el conector duplicado del classpath y conservada una sola versión JDBC local.
4. **Parcialmente implementado:** `Conexion.java` lee la configuración de variables de entorno o `.env`, permite sobreescribir la URL JDBC y conecta con el usuario de aplicación. Conexión y login DAO confirmados; falta probar el flujo desde Swing.

**Archivos añadidos:** `docker-compose.yml`, `.env.example` y `.gitignore` para excluir el `.env` local.

**Salida esperada:** un desarrollador puede levantar la BD desde cero y conectarse sin editar rutas o secretos en Java. La conexión JDBC ya usa la cuenta de aplicación desde `.env`; queda probar el flujo desde la aplicación.

### Fase 2 — Integridad del ciclo de pedidos

1. **Implementado:** el encabezado y sus detalles se guardan con una sola conexión y transacción; ante errores SQL se ejecuta rollback.
2. **Implementado:** el ID se recupera mediante `getGeneratedKeys()`; se eliminaron los métodos de guardado separados y la consulta `MAX(id)`.
3. **Implementado:** se comprueban filas afectadas y el resultado se propaga a la interfaz; el carrito solo se limpia y se confirma después de un commit correcto.
4. **Implementado:** columnas generadas e índice único impiden más de un pedido pendiente por sala/mesa; pedidos finalizados quedan fuera de esa clave. Se aplicó la migración a la base activa después de confirmar que no había duplicados. Falta verificar el rechazo concurrente desde dos sesiones.
5. Usar `BigDecimal` para importes y validar cantidades/precios positivos.

**Salida esperada:** un pedido queda guardado completo o no queda guardado; nunca quedan detalles huérfanos, incompletos o vinculados a otro pedido.

### Fase 3 — Acceso a datos y errores

1. **Implementado:** conexiones, sentencias y resultados ya no son estado compartido en los DAO de usuarios, platos, salas y pedidos.
2. **Implementado:** `try-with-resources` cierra recursos JDBC en cada operación. También se corrigió `ModificarDatos`, que intentaba usar una conexión que no abría.
3. **Parcial:** los DAO propagan excepciones de aplicación con causa y log; la UI informa errores de login/pedidos y el manejador global cubre eventos Swing y otros hilos. Falta mover el manejo específico a controladores y verificar cada flujo en ejecución.
4. **Revisado:** las consultas de estos DAO usan sentencias preparadas; la búsqueda de platos conserva parámetros para el texto y fecha.
5. **Parcial:** las altas, modificaciones, eliminaciones y finalización de pedido en `Sistema` comprueban el resultado antes de mostrar éxito o limpiar; falta validación funcional completa desde Swing.

**Salida esperada:** los fallos se informan claramente, no dependen del orden de navegación y no dejan conexiones abiertas.

### Fase 7 — Política de errores en toda la aplicación

1. **Inventario estático inicial completado; aceptación pendiente:** se revisaron 31 archivos Java y no se encontraron `catch` vacíos ni `printStackTrace`/`System.err` en el código de negocio. Repetir y ampliar el inventario por cada migración MVC y al final; revisar también valores por defecto/retornos ambiguos, excepciones tragadas en callbacks y recuperaciones incompletas.
2. **Parcial:** SQL, validaciones, configuración, PDF y `.env` propagan causa y registran; faltan pruebas inducidas de cada límite, restricciones/permisos y desconexiones reales.
3. **Parcial, con cambios aplicados:** `dispatchEvent` registra excepciones de evento en el límite UI y vuelve a propagar `Error` para su gestión por handler global, evitando el doble log conocido. Falta demostrar con pruebas EDT/hilos que cada caso tiene un único registro, presentación segura y recuperación/estado coherente; validar el ciclo para errores y excepciones.
4. **Corrección aplicada:** validaciones y `PedidoPendienteExistenteException` se clasifican `WARNING`; `DataAccessException` conserva severidad técnica `SEVERE`. Revisar y probar el catálogo completo de excepciones de dominio/técnicas y comprobar ausencia de secretos.
5. **Parcial, con canal de emergencia añadido:** `ArchivoLogDiario` rota y purga por mes calendario y ahora intenta guardar fallos del handler en archivo diario de emergencia; falta simular fallo de ambos canales y comprobar que el arranque/operación falla visiblemente cuando no hay canal durable.
6. **Parcial:** hay salida visible para errores Swing y resultados CRUD; faltan pruebas dinámicas de cada flujo UI, conservación de datos/estado y recuperación de EDT/hilos.
7. **Pendiente de ejecutar:** suite MySQL aislada para desconexión, credenciales/permisos, FK/índice, rollback, concurrencia y log único con stack trace/cadena causal.

**Criterio literal de aceptación (no se reduce a compilar ni a inspección estática):** la lista de control cubre el 100 % de los archivos/rutas y cada `catch`/callback/límite; para todo fallo inyectado se identifica origen, contexto y causa, hay exactamente una traza completa en el canal esperado, severidad correcta, mensaje/resultado de usuario inequívoco y ningún éxito falso, pérdida de datos o estado parcial. Toda recuperación está documentada y probada; si no es recuperable, la excepción se propaga sin perder causa hasta el único límite terminal. Fallas del logger quedan informadas por canal durable de emergencia; si ambos canales fallan, el programa no continúa aceptando trabajo como si el evento estuviera registrado. La revisión adversarial final no deja hallazgos abiertos y las pruebas demuestran esos puntos en unidad, interfaz/hilos y MySQL.

### Fase 8 — Separación gradual a MVC

La migración se hará **archivo Java de producción por archivo Java de producción**. En cada turno/unidad se modifica como máximo un `src/**/*.java` de producción; sus tests y documentos/configuración de soporte pueden cambiar juntos. Registrar antes su función y dependencias, después su destino MVC, pruebas, controles y decisión. Ejecutar `ant clean test`, revisar propagación/log único y límites de capa, y marcar `[x]` solo al aceptar. Ante fallo, el mismo archivo sigue en curso hasta corregirlo; no se avanza al siguiente. Las clases transversales se asignan explícitamente a infraestructura y las retiradas/renombradas se verifican por referencias. La lista es el registro de secuencia y evidencia; cada cambio debe anotar fecha, prueba ejecutada y resultado para mantener trazabilidad archivo por archivo.

Orden e inventario actual (33 archivos de producción):

1. `[x]` `src/restaurante/Restaurante.java` — punto de entrada Swing: instalar manejo/logs antes del UI y abrir la vista en EDT; sin SQL/reglas de negocio.
2. `[x]` `src/infraestructura/ConfiguracionLogs.java` — configura handler diario antes del arranque; errores de inicialización quedan registrados y abortan antes de aceptar operaciones. Prueba añadida para ruta no válida.
3. `[x]` `src/infraestructura/ArchivoLogDiario.java` — purga con fecha actual aun en eventos atrasados; no recrea archivos vencidos; canal de emergencia durable captura IO y RuntimeException del formateador; si ambos canales fallan, propaga fallo visible. Pruebas inducen los tres escenarios.
4. `[x]` `src/Modelo/ErrorAplicacionException.java` — conserva causa, registra una vez al crear y separa validaciones `WARNING` de fallos técnicos `SEVERE`; pruebas para ambos niveles y causa preservada.
5. `[x]` `src/Modelo/DataAccessException.java` — mantiene causa SQL y un único registro `SEVERE`; cubierto por prueba unitaria de política.
6. `[x]` `src/Modelo/PedidoPendienteExistenteException.java` — conflicto esperado `WARNING` con SQLState/causa preservados; cubierto por prueba unitaria y prueba concurrente MySQL.
7. `[x]` `src/Modelo/Conexion.java` — quedó como fachada compatible para los DAO existentes; delega la apertura JDBC al proveedor de infraestructura.
7a. `[x]` `src/infraestructura/ProveedorConexionJdbc.java` — concentra la configuración `.env`/entorno/propiedades y apertura JDBC; pruebas unitarias para precedencia y error `.env`; los DAO siguen conectando a través de la fachada compatible.
8. `[x]` `src/Modelo/Config.java` — bean de configuración de dominio sin SQL, Swing ni efectos secundarios; compilado dentro de ambas suites.
9. `[x]` `src/Modelo/DetallePedido.java` — bean de dominio sin Swing/SQL; la regla de precios/cantidades reside en servicio. Uso de `double` en precio queda pendiente de la migración monetaria Fase 2.
10. `[x]` `src/Modelo/Pedidos.java` — entidad/DTO sin Swing/SQL; `double total` queda pendiente de Fase 2 (migración a `BigDecimal`).
11. `[x]` `src/Modelo/Platos.java` — bean de dominio sin estado de interfaz ni acceso a datos; `double precio` queda pendiente de Fase 2.
12. `[x]` `src/Modelo/Salas.java` — entidad sala/mesa sin UI ni SQL; reglas de acceso se quedan en servicio/repositorio al extraer CRUD.
13. `[~]` `src/Modelo/login.java` — compatibilidad legacy; retirar cuando DAO/servicio/controlador/vistas migren a `Usuario` uno por uno.
13a. `[x]` `src/Modelo/Usuario.java` — entidad de dominio con prueba unitaria de construcción/accesores; consumidores y autenticación aún usan el tipo legacy.
14. `[x]` `src/Modelo/AutenticacionRepositorio.java` — contrato inyectable de autenticación ya añadido; verificar paquete/responsabilidad al cerrar arquitectura.
15. `[x]` `src/Modelo/PedidosRepositorio.java` — contrato de persistencia de registro completo ya añadido.
16. `[~]` `src/Modelo/LoginDao.java` — parcialmente adaptado al contrato de autenticación; separar autenticación, administración de usuarios y configuración de empresa.
17. `[~]` `src/Modelo/PedidosDao.java` — parcialmente adaptado al repositorio; separar consultas/escrituras de pedidos de la generación/apertura de PDF.
18. `[ ]` `src/Modelo/PlatosDao.java` — definir contrato/repositorio de platos e implementar acceso JDBC.
19. `[ ]` `src/Modelo/SalasDao.java` — definir contrato/repositorio de salas e implementar acceso JDBC.
20. `[ ]` `src/Modelo/Eventos.java` — mover reglas de teclado al límite de vista; no usar como validador de dominio.
21. `[ ]` `src/Modelo/Tables.java` — mover renderer Swing al paquete de vista.
22. `[ ]` `src/Modelo/ConnImpl.java` — comprobar ausencia de referencias y retirar esta clase vacía/obsoleta.
23. `[ ]` `src/Modelo/PreparedStatement.java` — comprobar ausencia de referencias y retirar la clase vacía que sombrea el nombre JDBC.
24. `[ ]` `src/Modelo/ResultSet.java` — comprobar ausencia de referencias y retirar la clase vacía que sombrea el nombre JDBC.
25. `[x]` `src/Servicio/AutenticacionServicio.java` — servicio de autenticación inyectable, con validación y propagación ya probado.
26. `[x]` `src/Servicio/PedidoServicio.java` — validación y orquestación de registro completo, ya probado sin MySQL.
27. `[x]` `src/Controlador/LoginControlador.java` — coordinación del caso de login ya usada por la vista.
28. `[x]` `src/Controlador/PedidosControlador.java` — coordinación del registro de pedidos ya usada por la vista.
29. `[~]` `src/Vista/FrmLogin.java` — login delegado al controlador; completar vistas de error, accesibilidad y dejar solo presentación/navegación.
30. `[~]` `src/Vista/Sistema.java` — registro de pedidos delegado; extraer y migrar cada flujo restante (CRUD, consultas, finalización, PDF) a controladores/servicios.
31. `[ ]` `src/Vista/ManejadorErroresSwing.java` — mantenerlo como frontera UI o moverlo a infraestructura UI; definir captura terminal, una sola bitácora y recuperación de EDT.

`[x]` refleja cortes ya existentes, no aprobación de arquitectura final; `[~]` indica migración parcial. Después de los 31 archivos, ejecutar el inventario completo de dependencias y una pasada adicional de revisión adversarial.

**Criterio de aceptación:** cada archivo del inventario termina `[x]` con registro individual de cambios/evidencia; ninguna vista ejecuta SQL o contiene reglas/casos de uso, ningún controlador consulta JDBC ni concentra lógica de negocio, servicios/repositorios se prueban con dependencias inyectadas, y la revisión de dependencias confirma separación de capas. La migración completa no se declara por haber añadido solo los primeros controladores/servicios.

### Fase 9 — Pruebas unitarias y automatización

1. **Implementado:** JUnit 4.13.2 y Hamcrest 1.3 están en `librerias/` y se agregaron solo al classpath de pruebas; Ant ejecuta el target `test` por separado de `compile`.
2. **Parcial:** hay pruebas para excepciones/logs/filas afectadas/argumentos inválidos, retención de logs, autenticación y validaciones/propagación del servicio de pedidos. Falta cubrir roles, cálculo de totales y flujo transaccional con dependencias falsas o mocks.
3. **Parcial:** se prueba propagación y registro en la política de errores; falta automatizar mensajes Swing y conservación de estado al fallar una carga.
4. **Unidad (sin BD):** ampliar cobertura de cada servicio y controlador usando repositorios falsos; cubrir resultados correctos, validaciones, errores de negocio, excepciones técnicas, causa/log único y estado previo preservado.
5. **Verificado:** `docker-compose.integration.yml` usa servicio/volumen exclusivos, BD `restaurante_test`, puerto host `3307` y credenciales ficticias; importa `BD.sql` solo en el contenedor desechable. No consume `.env` ni volumen de desarrollo.
6. **Verificado:** `test/integracion/MySqlIntegrationIT.java` y target Ant `integration-test` se separan de `test`. La URL/usuario/clave deben coincidir exactamente con valores de test antes de levantar Compose; el contenedor espera healthcheck y el target lo elimina con su volumen exclusivo al acabar.
7. **Parcial, 7 pruebas ejecutadas en MySQL 8.4.11:** conexión/esquema, usuario/claves válidos e inválidos, consultas de menú, CRUD de salas/platos, pedido/detalles/finalización, rollback inducido y concurrencia. Pendientes: cobertura completa de FK/unicidad separada, historial exhaustivo y generación de PDF sin aplicación externa.
8. Para cada escenario de error, comprobar excepción final, causa, una sola entrada de log y estado de BD consistente. Capturar logs en directorio temporal y verificar niveles/contexto sin contraseña.
9. Documentar comandos reproducibles y limpieza explícita exclusiva del proyecto Compose de integración, por ejemplo `docker compose -p restaurante-integration -f docker-compose.integration.yml down -v`; nunca usar `down -v` sobre el Compose normal.
10. **Verificado:** `ant clean test` (25 unit tests, cero fallos/errores) y `ant integration-test` (7 integration tests MySQL 8.4.11, cero fallos/errores; ejecutado antes de añadir `Usuario`, que no participa en la integración). Se ejecutó adicionalmente `ant -Dintegration.db.url=jdbc:mysql://127.0.0.1:3306/restaurante integration-test` y abortó en el guardia antes de Docker.
- **Inicio de Fase 8:** `src/restaurante/Restaurante.java` revisado como punto de entrada Swing: instala manejo/logs antes de aceptar eventos y abre `FrmLogin` en el EDT; no contiene SQL ni reglas de negocio. Aceptado para esta responsabilidad con `ant clean test` y `ant integration-test` exitosos. La inyección del controlador en `FrmLogin` se valida al migrar ese archivo.

**Criterio de aceptación:** `ant clean test` pasa repetidamente sin Docker/MySQL para unitarias con dobles. `ant integration-test` pasa en MySQL desechable aislado y valida consultas, CRUD, restricciones, transacciones y concurrencia; aborta antes de conectar si URL/base/credencial no son del entorno de prueba; verifica errores, logs, estados y limpieza. Reportar ambos grupos por separado como unidad e integración. La BD/volumen de desarrollo nunca se monta, borra ni recibe `BD.sql` desde el target de prueba.

### Fase 4 — Seguridad y validaciones

1. Eliminar credenciales precargadas y secretos del código/dump.
2. Almacenar contraseñas con un algoritmo de hash apropiado y migrar o restablecer las credenciales de prueba.
3. Aplicar permisos por rol de forma consistente para cada acción administrativa.
4. Corregir validación de campos, contraseñas vacías, valores numéricos y selecciones de tabla.
5. Definir el comportamiento esperado al intentar borrar salas con pedidos históricos y comunicar el resultado.

**Salida esperada:** no se crean usuarios/catálogos con datos vacíos o inválidos y cada rol queda limitado a sus operaciones autorizadas.

### Fase 5 — Menú y respuesta de interfaz

1. **Definido e implementado:** los platos siguen siendo diarios, acorde al filtro existente por `fecha`.
2. **Implementado:** calcular la fecha actual en cada consulta/alta y usar `CURRENT_DATE()` para los platos de ejemplo en una instalación nueva.
3. **Aplicado a la BD activa:** actualicé solo las tres filas de demostración verificadas cuya fecha era 2022; ahora tienen la fecha local actual del servidor.
4. Ejecutar operaciones JDBC fuera del hilo de eventos de Swing y actualizar la interfaz de forma segura.

**Salida esperada:** el menú corresponde al día vigente y la ventana sigue respondiendo ante consultas lentas.

### Fase 6 — Regresión y aceptación

Probar en una BD desechable:

- Inicialización limpia del esquema y arranque/parada del Compose conservando el volumen.
- Conexión correcta, credenciales inválidas y BD detenida.
- CRUD de salas y platos, incluyendo campos vacíos, límites y FK con pedidos históricos.
- Pedido con uno y varios platos, cantidades, comentarios y total.
- Fallos forzados durante el guardado y dos altas concurrentes para la misma mesa.
- Finalización, historial y generación del PDF.
- Permisos de administrador/asistente y manejo del cambio de fecha.

**Criterios para dar por estabilizado:** importación reproducible en entorno limpio; no hay excepciones sin controlar por errores de BD; no hay confirmaciones falsas; integridad de pedidos comprobada bajo fallos y concurrencia; regresión crítica aprobada.

## Orden recomendado

**Compose y conexión reproducible → integridad transaccional de pedidos → manejo de conexiones/errores → seguridad y validaciones → menú e interfaz → regresión completa.**

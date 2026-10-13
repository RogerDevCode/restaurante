# Plan de mitigación de actualización y autorecuperación de la base de datos

> **Para agentes que ejecuten este plan:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` o `superpowers:executing-plans`; ejecutar una tarea a la vez y revisar el resultado antes de continuar.

**Objetivo:** Evitar escrituras perdidas y dejar la base de datos recuperable si una migración o restauración falla, se interrumpe o se reinicia el proceso.

**Arquitectura:** Separar la migración de esquema de la restauración de datos; ambas deben entrar en modo de mantenimiento antes del respaldo y las escrituras deben quedar bloqueadas en la propia BD. Los respaldos llevarán integridad y cobertura verificables; las migraciones serán reanudables por paso y la restauración tendrá un diario durable fuera de las tablas que reemplaza.

**Tech Stack:** Java 21, Swing, JDBC, MySQL 8.4, Apache Ant, JUnit 4 y Docker para integración aislada.

## Restricciones globales

- No ejecutar pruebas contra la BD de producción ni reiniciar la aplicación de servicio durante la implementación.
- No importar `BD.sql`, vaciar tablas ni reconstruir una BD existente como respuesta a un error de migración/restauración.
- No iniciar DDL de actualización sin bloquear escrituras, verificar un respaldo previo y registrar la versión/paso.
- Mantener el control de mantenimiento fuera del contenido restaurable, para que la restauración no quite el bloqueo que la protege.
- En errores o recuperaciones incompletas, conservar la BD en mantenimiento y no permitir que la interfaz opere contra un esquema parcial.
- Usar el MySQL de integración aislado; verificar primero en `build.xml` y `docker-compose.integration.yml` el alcance de cualquier limpieza de volúmenes.
- Mantener `actualizar_bd.sql` y `db/migrations/actualizar_bd.sql` idénticos mientras ambos sigan publicados.

## Mapa de archivos

- `src/infraestructura/MigradorEsquemaJdbc.java`: versiones, pasos, checksum y postcondiciones de las migraciones.
- `src/infraestructura/ProveedorConexionJdbc.java`: acceso JDBC compartido; incorporar el control de mantenimiento sin dejar rutas de escritura fuera de la barrera.
- `src/Servicio/ServicioRespaldoBaseDatos.java`: formato, integridad, cobertura y recuperación de respaldos.
- `src/restaurante/Restaurante.java`: orden de recuperación/migración antes de abrir login y ventana.
- `src/Vista/Sistema.java`: confirmación de restauración, aviso de mantenimiento y refresco tras recuperación.
- `src/Modelo/*Dao.java`: transacciones que escriben datos; cada ruta debe respetar el bloqueo de mantenimiento.
- `actualizar_bd.bat`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`: dejar una sola ruta de migración canónica y propagar fallos.
- `inicio_fresco.sh`: mantenerlo como operación destructiva explícita, nunca como autorecuperación.
- `test/Servicio/ServicioRespaldoBaseDatosTest.java`, `test/infraestructura`, `test/integracion/MySqlIntegrationIT.java`: fallos, concurrencia y preservación centinela en MySQL aislado.
- `build.xml`, `docker-compose.integration.yml`: confirmar que las pruebas solo afectan la instancia y el volumen efímeros.

---

## Fase 0: poner una barrera de seguridad y fijar el comportamiento actual

### Tarea 0.1: inventariar toda escritura de negocio

**Archivos:** solo lectura sobre `src/Modelo/*Dao.java`, `src/Servicio/*`, `src/Vista/Sistema.java`.

- [ ] Enumerar cada `INSERT`, `UPDATE`, `DELETE`, `REPLACE`, `TRUNCATE`, DDL, `executeUpdate` y `executeBatch` del código de aplicación; clasificar operación y transacción propietaria.
- [ ] Enumerar conexiones adicionales, jobs, cierres automáticos, hilos virtuales y procesos que puedan escribir mientras se restaura o migra.
- [ ] Registrar las rutas que llaman a `MigradorEsquemaJdbc.migrarSiEsNecesario` y a `ServicioRespaldoBaseDatos.restaurarRespaldo`.
- [ ] Comprobar que `actualizar_bd.bat` invoca la ruta Java y que ninguna ruta de fallo cae en `BD.sql`.

**Verificación:** `rg -n "executeUpdate|executeBatch|TRUNCATE|DROP TABLE|ALTER TABLE" src` y `rg -n "migrarSiEsNecesario|restaurarRespaldo|crearRespaldo" src` producen una lista revisada, no una modificación.

**Aceptación:** cada ruta que escribe queda identificada para aplicar el bloqueo de mantenimiento; no hay una segunda ruta de actualización activa sin inventariar.

### Tarea 0.2: añadir bloqueo de mantenimiento en la BD

**Archivos:** crear `src/infraestructura/ControlMantenimientoBD.java`; modificar `ProveedorConexionJdbc.java`, las DAO escritoras identificadas en 0.1 y pruebas JDBC.

- [ ] Crear la tabla de control fuera del backup restaurable:

```sql
CREATE TABLE IF NOT EXISTS restaurante_control (
    id TINYINT NOT NULL PRIMARY KEY,
    estado VARCHAR(20) NOT NULL,
    operacion_id CHAR(36) NULL,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_restaurante_control_estado
        CHECK (estado IN ('OPERATIVO', 'MANTENIMIENTO'))
) ENGINE=InnoDB;
INSERT IGNORE INTO restaurante_control (id, estado) VALUES (1, 'OPERATIVO');
```

- [ ] En cada transacción de escritura, leer la fila `id=1` con `SELECT ... FOR SHARE`; rechazar con error claro si `estado <> 'OPERATIVO'`. Mantener ese bloqueo hasta el commit/rollback de la escritura.
- [ ] Para comenzar una operación de mantenimiento, cambiar la fila a `MANTENIMIENTO` dentro de una transacción con `SELECT ... FOR UPDATE`; este update espera que terminen las escrituras que ya habían tomado el bloqueo compartido.
- [ ] Mantener `MANTENIMIENTO` como estado durable durante todo el DDL/restauración; no depender de un bloqueo SQL que el DDL de MySQL pueda liberar al hacer commit implícito.
- [ ] En una salida normal y validada, regresar a `OPERATIVO`; si hay error o incertidumbre, mantener `MANTENIMIENTO` hasta recuperación explícita.
- [ ] Excluir `restaurante_control` de exportación e importación del respaldo, y verificar que los respaldos antiguos que no la conocen tampoco puedan reemplazarla.
- [ ] Cuando la BD ya está en mantenimiento, bloquear nuevas operaciones desde todas las instancias de la aplicación. Documentar que clientes SQL con credenciales de escritura fuera de la aplicación deben desconectarse durante mantenimiento.

**Pruebas:** con dos conexiones de integración, demostrar que el cambio a mantenimiento espera una transacción de escritura ya iniciada y que una escritura nueva falla sin insertar; demostrar que un `DROP/CREATE` del contenido del respaldo no modifica el estado de control.

**Aceptación:** una restauración/migración no puede competir con una escritura iniciada por otra instancia de la aplicación; la barrera sobrevive a DDL y reinicio de JVM.

## Fase 1: hacer que el respaldo sea completo y comprobable

### Tarea 1.1: definir un paquete de respaldo verificable

**Archivos:** `src/Servicio/ServicioRespaldoBaseDatos.java`, `test/Servicio/ServicioRespaldoBaseDatosTest.java`.

- [ ] Cada respaldo incluirá: SQL, SHA-256 obligatorio, catálogo de tablas exportadas, definición de columnas por tabla y cantidad de filas exportadas; registrar versión de formato, BD origen y fecha.
- [ ] Generar primero archivos temporales con creación exclusiva. Publicar el SQL y manifiesto solo después de cerrar, volver a leer, validar SHA-256 y comprobar que el pie final está presente.
- [ ] Hacer que un checksum ausente, incorrecto, duplicado o perteneciente a otro nombre de archivo invalide el respaldo. No interpretar la firma mágica como autenticidad ni como prueba de cobertura.
- [ ] La validación comparará el manifiesto con sentencias `DROP/CREATE/INSERT` parseadas; exigirá las tablas de negocio y controlará que no falten tablas existentes al momento de capturar el snapshot.
- [ ] Si se encuentran views, triggers, rutinas, eventos o un engine no soportado, cancelar antes de publicar respaldo y antes de cualquier migración/restauración.
- [ ] El respaldo previo a migración/restauración deberá pasar las mismas reglas y quedar con checksum antes de que el llamador continúe.

**Pruebas:** archivos truncados; footer añadido a mano; SQL incompleto con firma correcta; checksum faltante/incorrecto; tabla o lote `INSERT` omitido; tabla fuera del manifiesto; engine no InnoDB; fallo de disco entre cada publicación temporal.

**Aceptación:** ningún archivo incompleto se entrega como respaldo válido y un respaldo válido representa todas las tablas/datos visibles en el snapshot.

### Tarea 1.2: probar restauración del respaldo en una BD desechable

**Archivos:** `src/Servicio/ServicioRespaldoBaseDatos.java`, `test/integracion/MySqlIntegrationIT.java`, `build.xml`, `docker-compose.integration.yml`.

- [ ] Añadir una operación de verificación que restaure el paquete en una BD/schema desechable aislada antes de permitirlo como fuente de restauración.
- [ ] Comparar tablas, columnas, claves, filas centinela, relaciones y valores de pedidos/configuración con el manifiesto; la igualdad de tamaño del archivo no cuenta como verificación.
- [ ] Destruir solo el schema temporal creado por el test/operación, nunca ejecutar `docker compose down --volumes` sin comprobar el volumen destino.
- [ ] Hacer que una falla de verificación impida tocar la BD viva.

**Pruebas:** snapshot con platos, pedidos, detalles, clientes, usuarios, configuración, cierres y pagos; restaurar en schema temporal y comparar valores exactos, no solo conteos.

**Aceptación:** el respaldo se prueba restaurable antes de considerarse apto; un respaldo parcial no llega a la fase de reemplazo de la BD viva.

### Tarea 1.3: evitar que un archivo diario corrupto suprima el siguiente respaldo

**Archivos:** `src/Servicio/ServicioRespaldoBaseDatos.java`, `test/Servicio/ServicioRespaldoBaseDatosTest.java`.

- [ ] Al comprobar si ya existe backup del día, validar checksum, footer y manifiesto del archivo encontrado.
- [ ] Si existe un `.sql` sin manifiesto o con validación fallida, conservarlo con nombre de incidente y crear un respaldo nuevo con nombre único; nunca sobrescribirlo silenciosamente.
- [ ] Solo depurar por retención respaldos diarios que pasen validación y estén fuera del período retenido; preservar respaldos `pre_migracion` y `pre_restauracion` hasta una política de retención explícita.

**Aceptación:** un `.sql` inválido del día no evita un backup nuevo ni se reporta como backup exitoso.

## Fase 2: convertir la actualización automática en migraciones reanudables

### Tarea 2.1: registrar cada migración y cada paso sin alterar historia aplicada

**Archivos:** `src/infraestructura/MigradorEsquemaJdbc.java`, `test/infraestructura`, `test/integracion/MySqlIntegrationIT.java`.

- [ ] Modelar cada migración con versión, checksum estable, paso actual y resultado. No cambiar el checksum ni el contenido de una versión aplicada; corregir mediante una versión nueva.
- [ ] Separar `asegurarEsquema` en pasos con precondición y postcondición consultables. Al reiniciar desde `EN_CURSO`, verificar la postcondición y continuar sin repetir una transformación ya confirmada.
- [ ] Adquirir el estado de mantenimiento de la Fase 0 antes de respaldo/DDL; crear y validar respaldo después de bloquear escrituras, y mantener el estado hasta validar la versión final.
- [ ] Si un paso falla, detener inicio y conservar el estado `EN_CURSO`, logs y respaldo; no declarar éxito, no cargar login y no importar `BD.sql`.
- [ ] Validar todas las tablas/columnas/tipos/nullability/índices que el código actual usa, incluyendo `pedidos.tipo_pedido`, `pedidos.id_sala`, `pedidos.num_mesa`, columnas generadas y claves de configuración.
- [ ] Clasificar esquema vacío, soportado, futuro y desconocido. A un esquema existente no soportado no aplicarle una auto-reconstrucción.

**Pruebas:** falla inyectada después de cada paso DDL, caída simulada antes de actualizar el ledger, segunda ejecución, dos procesos migrando a la vez y pérdida de conexión; comparar centinelas antes/después.

**Aceptación:** cada caída tiene un estado durable y una siguiente acción segura; ninguna migración incompleta se presenta como aplicada.

### Tarea 2.2: preservar estados y valores de negocio durante ALTER

**Archivos:** `src/infraestructura/MigradorEsquemaJdbc.java`, `test/integracion/MySqlIntegrationIT.java`.

- [ ] Antes de cambiar `pedidos.estado`, leer su definición actual y valores distintos existentes. Ampliar el enum preservando valores anteriores y agregar `ANULADO`; si hay valores o tipos desconocidos, detener migración con reporte antes del DDL.
- [ ] Confirmar que cualquier cambio a `NOT NULL`, longitud, collation o tipo no trunca valores: consultar valores nulos, longitudes máximas, colisiones de índices únicos y conversiones inválidas antes de modificar.
- [ ] Para los índices únicos, listar los grupos duplicados con sus IDs y detenerse sin eliminar ni combinar filas.
- [ ] En configuración, conservar la fuente con valor explícito del negocio; sembrar defaults solo cuando falte la clave y registrar conflictos sin sobrescribir la configuración del dueño.

**Pruebas:** estados extra, strings al límite de longitud, nulos, duplicados de correo/mesa y configuración legacy/KV en conflicto; la migración debe detenerse sin pérdida o mantener todos los valores válidos.

**Aceptación:** no se transforma ni elimina un valor de negocio sin una regla explícita aprobada y cubierta por prueba.

## Fase 3: hacer restauración segura frente a concurrencia e interrupciones

### Tarea 3.1: agregar diario durable de restauración

**Archivos:** crear `src/infraestructura/DiarioRecuperacionBD.java`; modificar `src/Servicio/ServicioRespaldoBaseDatos.java` y `src/restaurante/Restaurante.java`.

- [ ] Antes del primer `DROP`, escribir en disco durable un diario con `operacion_id`, tipo, ruta/checksum del respaldo previo, checksum del respaldo destino, fase y fecha; publicar el diario por renombrado atómico tras `fsync`.
- [ ] Avanzar fases antes y después de validar respaldo, marcar mantenimiento, aplicar sentencias, validar esquema/datos, terminar recuperación y liberar mantenimiento.
- [ ] En el arranque, consultar el diario antes del migrador y del login. Si hay operación incompleta, mantener bloqueadas las escrituras, verificar respaldo previo y reanudar recuperación desde ese respaldo; si no se puede, abortar el inicio con la ruta del diario/respaldo y dejar la BD en mantenimiento.
- [ ] Borrar o archivar el diario solo luego de comprobar la recuperación y restablecer el estado de control a `OPERATIVO`.
- [ ] Registrar el error original y el error de recuperación por separado; no mostrar “recuperación completada” sin validar la BD restaurada.

**Pruebas:** terminar el proceso en cada fase del diario y arrancar de nuevo; simular archivo faltante, checksum incorrecto, MySQL desconectado y fallo al reimportar. En ningún caso abrir la aplicación con estado incierto.

**Aceptación:** una caída de JVM/host durante la restauración es detectada antes del login y se recupera o queda bloqueada con instrucciones accionables.

### Tarea 3.2: encapsular el reemplazo de tablas y su finalización

**Archivos:** `src/Servicio/ServicioRespaldoBaseDatos.java`, `src/Vista/Sistema.java`, tests de integración Swing/servicio.

- [ ] Mantener la BD en modo mantenimiento desde antes del respaldo previo hasta validación final o recuperación del snapshot previo.
- [ ] Ejecutar importación fail-fast; después verificar contra manifiesto y postcondiciones de aplicación antes de dar éxito.
- [ ] Ante fallo capturado, recuperar con el respaldo previo validado y revalidar datos antes de cambiar estado a `OPERATIVO`; ante fallo de recuperación, mantener `MANTENIMIENTO`.
- [ ] En UI, mostrar que otras instancias deben detener operaciones, bloquear acciones locales durante restauración y mostrar resultado/ID/rutas sin etiquetar como éxito una restauración parcial.
- [ ] No cambiar `restaurante_control` durante un restore y no restaurar un backup cuya BD origen sea incompatible con el esquema/versiones permitidos sin migrarlo primero en el schema temporal.

**Pruebas:** lanzar dos operaciones de escritura simultáneas desde otra conexión; comprobar que terminan antes del mantenimiento o reciben rechazo. Forzar error a mitad de importación y verificar que vuelven íntegros los centinelas del pre-respaldo.

**Aceptación:** ninguna operación concurrente se pierde silenciosamente y el estado `OPERATIVO` solo se publica tras restauración y comprobación completas.

## Fase 4: unificar rutas y proteger la operación destructiva separada

### Tarea 4.1: dejar un motor de actualización

**Archivos:** `actualizar_bd.bat`, `actualizar_bd.sql`, `db/migrations/actualizar_bd.sql`, `src/restaurante/Restaurante.java`, `test/infraestructura/InstaladoresSeguridadTest.java`.

- [ ] Hacer que todos los actualizadores soportados llamen al modo de mantenimiento/migración Java con la misma configuración y salida de error.
- [ ] Marcar el SQL legacy como no ejecutable manualmente o generarlo de la fuente canónica; no mantener dos definiciones editables de la migración.
- [ ] Si se conservan los dos archivos por compatibilidad, generar uno del otro y verificar igualdad en build.
- [ ] Eliminar mensajes absolutos como “sin pérdida de datos”; reportar versión aplicada, postcondiciones verificadas y ruta del backup.

**Aceptación:** wrapper y arranque ejecutan el mismo migrador; error de SQL, credenciales o backup termina con código distinto de cero sin importar `BD.sql`.

### Tarea 4.2: mantener `inicio_fresco.sh` fuera de la ruta de recuperación

**Archivos:** `inicio_fresco.sh`, pruebas estáticas/de scripts.

- [ ] Dejar explícito en consola y documentación que `inicio_fresco.sh` borra datos y no es mecanismo de recuperación.
- [ ] Antes de cualquier `TRUNCATE`, verificar que `mysqldump` y `gzip` terminaron bien, que el gzip se descomprime, que el dump contiene tablas esperadas y que se puede importar en una BD temporal.
- [ ] Mantener la confirmación `SI` y exigir `MYSQL_DB` distinto de producción en pruebas automatizadas.
- [ ] Si el vaciado falla a mitad, no sembrar ni declarar éxito; conservar backup, detener el proceso y reportar el paso exacto.

**Aceptación:** ningún fallo de migración/restauración deriva en `inicio_fresco.sh`; este script solo corre con autorización destructiva explícita y respaldo verificable.

## Fase 5: pruebas adversariales y liberación gradual

### Tarea 5.1: ejecutar matriz en MySQL aislado

**Archivos:** `test/Servicio/ServicioRespaldoBaseDatosTest.java`, `test/infraestructura`, `test/integracion/MySqlIntegrationIT.java`, `build.xml`, `docker-compose.integration.yml`.

- [ ] Antes de correr la suite, confirmar URL, credenciales, nombre de BD, container y volumen de integración; abortar si apuntan a `restaurante`.
- [ ] Poblar fixtures centinela de platos, pedidos, detalles, pagos, clientes, usuarios, cierres e IVA/configuración; guardar valores exactos y relaciones.
- [ ] Probar migración normal, segunda ejecución, schema desconocido, fallo de permisos, error en cada DDL, dos migradores concurrentes y reinicio tras `EN_CURSO`.
- [ ] Probar restore válido, archive truncado, checksum malo, respaldo semánticamente incompleto, tabla faltante, error de importación y recuperación interrumpida en cada fase.
- [ ] Comparar valores y claves antes/después; no aceptar solo iguales cantidades de filas.
- [ ] Confirmar que la BD de integración queda desechable y que ningún test abre la configuración `.env` de producción.

**Comandos:** `ant -q compile`; pruebas unitarias dirigidas con `ant -q test-single -Djavac.includes=Servicio/ServicioRespaldoBaseDatosTest.java -Dtest.includes=Servicio/ServicioRespaldoBaseDatosTest.java`; y `ant -q integration-test` solo después de validar el volumen aislado.

**Aceptación:** matriz sin pérdida de centinelas, sin falsos éxitos, reintentos convergentes y ninguna escritura/prueba sobre la BD real.

### Tarea 5.2: despliegue controlado

**Archivos:** documentación de operación y procedimiento de rollback.

- [ ] Restaurar primero el último backup en una instancia separada y comparar datos con la BD de origen.
- [ ] Probar una copia anonimizada de BD realista; mantener la aplicación bloqueada hasta confirmar versión y postcondiciones.
- [ ] Publicar pasos de mantenimiento, migración, recuperación del backup y reapertura; incluir cómo recuperar manualmente si la recuperación automática falla.
- [ ] Habilitar la nueva migración solo después de revisar logs, manifiesto, comparación centinela y salida del modo mantenimiento.

**Aceptación:** el dueño puede identificar el backup, el estado de operación, la versión de esquema y la siguiente acción segura sin inferirlos de un mensaje genérico.

## Criterios de salida

1. No hay restauración ni migración con escrituras de aplicación activas.
2. Ninguna migración empieza sin un backup completo, íntegro y restaurable en aislamiento.
3. Un proceso interrumpido no vuelve a abrir la BD hasta completar recuperación o dejarla explícitamente bloqueada.
4. Las migraciones preservan los valores existentes y detectan duplicados/conversiones incompatibles antes del DDL.
5. El esquema se valida completo antes de anunciar éxito y liberar mantenimiento.
6. Los backups incompletos, modificados o incompatibles son rechazados antes de reemplazar la BD viva.
7. `BD.sql` y `inicio_fresco.sh` nunca se usan para recuperarse de un error de actualización.

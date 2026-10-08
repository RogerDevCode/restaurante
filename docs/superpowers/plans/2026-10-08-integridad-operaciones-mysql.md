# Corrección de integridad de operaciones MySQL — Implementation Plan

> Plan de ejecución para corregir los hallazgos de atomicidad, migración y persistencia parcial.

**Goal:** Evitar que operaciones SQL informen éxito con datos incompletos, hacer recuperables las migraciones parciales y compensar los archivos generados cuando falle su registro en MySQL.

**Architecture:** Cuatro frentes independientes, ordenados por riesgo: migraciones, finalización de pedidos, persistencia de configuración y cierre PDF/SQL. Las operaciones que modifican varias filas usarán una conexión y una transacción; el DDL MySQL se tratará como una secuencia idempotente y reintentable, porque MySQL puede hacer commits implícitos.

**Tech Stack:** Java 21, JDBC, MySQL 8.4, Apache Ant, JUnit 4 y Docker Compose para integración.

## Restricciones globales

- No informar éxito si falta guardar un dato requerido por la operación.
- No envolver DDL MySQL en una transacción con la promesa de rollback: las migraciones deben poder reintentarse de forma segura.
- Mantener compatibilidad del esquema mediante las migraciones existentes; ante un esquema incompleto, fallar de manera explícita y dejar la operación de negocio sin aplicar.
- No modificar reglas fiscales, importes, datos históricos ni permisos en este trabajo.
- Conservar la suite de integración aislada en `restaurante_test` y el puerto `127.0.0.1:3307`.

---

## Mapa de archivos

- `src/infraestructura/MigradorEsquemaJdbc.java`: resultado, reintentos y estado por base de datos de la migración.
- `src/Modelo/PedidosDao.java`: finalización y estrategia de compatibilidad de esquema.
- `src/Modelo/LoginDao.java`: transacción de actualización de configuración empresarial.
- `src/Modelo/ConfiguracionRepositorio.java` y `src/Modelo/ConfiguracionSistemaDao.java`: escritura de pares clave-valor usando la conexión transaccional recibida.
- `src/Servicio/CierreCajaServicio.java`: compensación del PDF si no se registra el cierre.
- `src/Modelo/CierreCajaDao.java`: inserción del cierre; el `INSERT` existente ya es de una sola fila y no necesitó cambio.
- `test/Vista/`, `test/Modelo/`, `test/Servicio/`: pruebas unitarias de fallos y rollback.
- `test/integracion/MySqlIntegrationIT.java`: escenarios reales de MySQL 8.4 para rollback, errores de esquema y persistencia.

## Fase 1: hacer confiables las migraciones

**Archivos:** `src/infraestructura/MigradorEsquemaJdbc.java`, pruebas en `test/infraestructura/` y `test/integracion/MySqlIntegrationIT.java`.

- [x] Reemplazar el indicador global `MIGRADO` por estado asociado a URL/catálogo; no saltar la migración de otra base en la misma JVM.
- [x] Hacer que las verificaciones de columnas/tablas/índices requeridos propaguen el error si no pudieron crearse. No marcar migración completa por errores absorbidos.
- [x] Marcar migrada una base solo después de que terminen todas las verificaciones y sincronizaciones requeridas.
- [x] Mantener cada cambio DDL idempotente y tolerar carreras de columna/índice ya creados.
- [x] Cubrir fallo de DDL, reintento y dos catálogos distintos en la misma JVM.
- [x] Ejecutar `ant test` y `ant integration-test`.

**Aceptación:** un fallo de DDL no deja el migrador en estado “completo”; el siguiente intento vuelve a comprobar y completar el esquema. Una segunda base no hereda el estado de la primera.

## Fase 2: impedir finalizaciones incompletas

**Archivos:** `src/Modelo/PedidosDao.java`, pruebas en `test/Modelo/AdversarialFase2ConcurrenciaYOptimizacionTest.java` y `test/integracion/MySqlIntegrationIT.java`.

- [x] Eliminar el fallback que, ante error 1054, actualizaba solo `estado` y devolvía éxito.
- [x] Si faltan columnas requeridas, propagar un error; el pedido permanece `PENDIENTE`.
- [x] Mantener en un solo `UPDATE` condicional el cambio de estado y los campos de cliente/pago.
- [x] Añadir pruebas unitarias e integración MySQL de columna desconocida; conservar prueba de finalización concurrente.
- [x] Ejecutar `ant test` y `ant integration-test`.

**Aceptación:** un pedido solo puede aparecer finalizado si los datos requeridos de cliente y pago quedaron persistidos; los errores de esquema se muestran como fallos y dejan el pedido pendiente.

## Fase 3: guardar configuración en una sola transacción

**Archivos:** `src/Modelo/LoginDao.java`, `src/Modelo/ConfiguracionRepositorio.java`, `src/Modelo/ConfiguracionSistemaDao.java` y pruebas de `test/Servicio/RedTeamConfiguracionClaveValorTest.java` más integración MySQL.

- [x] Definir en el repositorio una operación que guarda el lote usando una `Connection` existente.
- [x] Actualizar `config` y hacer upsert de `configuracion_sistema` en una transacción común.
- [x] Confirmar una vez; ante cualquier error, hacer rollback y propagarlo.
- [x] Restaurar autocommit original sin informar falsamente que falló una transacción ya confirmada.
- [x] Añadir prueba MySQL de rollback ante trigger y verificar el caso exitoso con ambas tablas sincronizadas.
- [x] Ejecutar `ant test` y `ant integration-test`.

**Aceptación:** la configuración empresarial y sus claves operativas se guardan juntas o ninguna queda actualizada; el llamador recibe error cuando se revierte.

## Fase 4: compensar el fallo entre PDF y registro SQL

**Archivos:** `src/Servicio/CierreCajaServicio.java`, `src/Modelo/CierreCajaDao.java`, `test/Servicio/CierreCajaTest.java` y `test/integracion/MySqlIntegrationIT.java`.

- [x] Generar nombres únicos para evitar que dos cierres del mismo tipo y segundo se sobrescriban; borrar PDFs incompletos cuando falla la composición.
- [x] Si el `INSERT` del cierre falla o informa que no guardó filas, eliminar el PDF y propagar el error. Si falla la limpieza, registrar la ruta y agregar la causa de limpieza al error original.
- [x] No añadir restricción única por fecha/tipo; la regla de múltiples cierres se conserva.
- [x] Añadir pruebas para fallo de `guardarCierre` por retorno falso y por excepción, y para nombres únicos de PDF.
- [x] Ejecutar `ant test` y `ant integration-test`.

**Aceptación:** los fallos observables de sincronización archivo/BD quedan compensados o registrados con ruta y error explícitos; nunca se informa un cierre exitoso sin PDF y fila registrada.

## Cierre de ejecución

- [x] Ejecutar la suite unitaria completa: `ant -q test`.
- [x] Ejecutar la suite MySQL aislada: `ant -q integration-test`.
- [x] Revisar `git diff --check`; no se alteraron reglas fiscales ni datos de producción.
- [x] Confirmar resultados en `build/test/results/integration`; las pruebas usaron el contenedor aislado de integración.
- [x] Entregar resumen de archivos cambiados, pruebas ejecutadas y resultados.

## Riesgos y límites

- MySQL hace commits implícitos en DDL. Si una migración falla a mitad, puede quedar aplicada parcialmente; la garantía prevista es reintento seguro y detección explícita, no rollback total del DDL.
- No existe una transacción JDBC atómica común a MySQL y al sistema de archivos. La fase del PDF compensa fallos SQL observados; una caída abrupta del proceso entre crear el PDF y guardar la fila todavía puede dejar un archivo huérfano.
- La prueba de integración levanta y elimina el volumen Docker de pruebas según `build.xml`; mantener las credenciales y URL de pruebas fijas.

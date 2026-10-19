# Diseño: serializar mutaciones del último Administrador entre instancias

Fecha: 19/10/2026

## Problema observado

`LoginDao` protege las mutaciones de usuarios con `LOCK_MUTACION_ADMIN`, que es un monitor Java estático. Ese monitor evita carreras entre hilos de una misma JVM, pero no coordina dos procesos de Restaurante conectados a la misma base. La comprobación transaccional del número de Administradores activos no demuestra por sí sola la exclusión mutua entre procesos.

## Resultado esperado

Desactivar o eliminar un Administrador debe volver a comprobar la regla del último Administrador dentro de una sección crítica compartida por todas las instancias que usan la base MySQL. El administrador primario `admin` y el último Administrador activo deben seguir protegidos. Si no se obtiene el lock, la mutación falla cerrada y no cambia datos.

## Enfoques considerados

1. **Lock asesor de MySQL**: usar `GET_LOCK` con timeout acotado, en la misma conexión usada por la transacción, y liberar con `RELEASE_LOCK` en `finally`. Es explícito, no requiere DDL y coordina instancias distintas. Recomendado.
2. **Locks de filas/tabla solamente**: depender de `SELECT ... FOR UPDATE` y del aislamiento InnoDB. Evita lock asesor, pero el conjunto de filas bloqueado depende de índices y estado; es menos claro para proteger el conteo global de Administradores.
3. **Mantener solo el monitor JVM**: no requiere cambios, pero deja abierta la carrera entre procesos y no satisface el requisito.

## Cambio propuesto

En `LoginDao.ejecutarMutacionUsuarioProtegida`, adquirir un lock asesor con nombre fijo dedicado a mutaciones de usuarios Administradores antes de iniciar la transacción. Ante timeout, error o resultado distinto de adquirido, abortar sin mutar. Una vez adquirido, ejecutar en la transacción actual la lectura bloqueada del usuario, la protección del primario, el conteo de otros Administradores activos y la mutación solicitada. Confirmar o revertir explícitamente; liberar el lock asesor en `finally` antes de devolver la conexión al pool. Mantener el monitor local como optimización de contención intraproceso.

## Prueba y límites

Agregar una prueba de integración MySQL que adquiera el lock desde una conexión independiente, lance la mutación por `LoginDao` en otro hilo/conexión y compruebe que no termina antes de liberar el lock; tras liberarlo, comprobar el resultado y limpiar fixtures. La prueba debe usar solo `restaurante_test` efímera. Esto demuestra coordinación mediante MySQL entre conexiones; la restauración en servidor secundario y la validación completa del release siguen siendo gates separados.

## Criterios de aceptación

- Un lock no adquirido produce rechazo seguro y no ejecuta actualización/eliminación.
- Una mutación espera el lock compartido aunque provenga de otra conexión.
- La regla de no desactivar/eliminar al último Administrador activo continúa pasando.
- Errores JDBC revierten la transacción y la conexión vuelve al pool con el estado restaurado.
- Compilación, tests de ciclo de vida del DAO e integración MySQL pasan.

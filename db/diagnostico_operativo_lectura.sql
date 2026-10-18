-- Diagnostico de solo lectura para estado de mantenimiento y contadores.
-- Ejecutar conectado a la BD que se desea revisar. No cambia datos ni esquema.

SELECT DATABASE() AS base_actual, @@hostname AS servidor, @@port AS puerto, VERSION() AS version_mysql;

SELECT id, estado, operacion_id, actualizado_en
FROM restaurante_control
WHERE id = 1;

SELECT TABLE_NAME AS tabla, ENGINE AS motor, AUTO_INCREMENT AS proximo_id
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND AUTO_INCREMENT IS NOT NULL
ORDER BY TABLE_NAME;

-- Para cada tabla reportada arriba cuyo AUTO_INCREMENT parezca menor que MAX(id),
-- ejecutar estas consultas sustituyendo <tabla> por el nombre inspeccionado:
-- SHOW CREATE TABLE <tabla>;
-- SELECT MAX(id) AS max_id FROM <tabla>;
-- Confirmar mismo servidor, esquema, motor InnoDB y columna antes de cualquier ajuste.
-- No se corrige AUTO_INCREMENT automáticamente: si se confirma el desfase,
-- planificar el ajuste manual al menos a MAX(id)+1 con respaldo y ventana controlada.

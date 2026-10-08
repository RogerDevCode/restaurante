-- Migración 012: Opción para activar o desactivar la impresión del logo en tickera (80mm)
SET @existe_col = (
    SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'imprimir_logo_ticket'
);

SET @sql = IF(@existe_col = 0,
    'ALTER TABLE config ADD COLUMN imprimir_logo_ticket TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''1: Imprimir logo en ticket, 0: Omitir logo en ticket para ahorrar papel y tiempo''',
    'SELECT "Columna imprimir_logo_ticket ya existe"'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

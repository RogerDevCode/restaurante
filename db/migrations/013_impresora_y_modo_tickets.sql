-- Migración 013: Soporte para selector de impresora de Windows y modo de salida de tickets (Térmica 80mm, PDF, PDF24 Creator)
SET @existe_imp = (
    SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'impresora_tickets'
);

SET @sql_imp = IF(@existe_imp = 0,
    'ALTER TABLE config ADD COLUMN impresora_tickets VARCHAR(150) NOT NULL DEFAULT ''DEFAULT'' COMMENT ''Nombre de la impresora de Windows para tickets de 80mm''',
    'SELECT "Columna impresora_tickets ya existe"'
);

PREPARE stmt_imp FROM @sql_imp;
EXECUTE stmt_imp;
DEALLOCATE PREPARE stmt_imp;

SET @existe_modo = (
    SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'modo_salida_tickets'
);

SET @sql_modo = IF(@existe_modo = 0,
    'ALTER TABLE config ADD COLUMN modo_salida_tickets VARCHAR(50) NOT NULL DEFAULT ''TERMICA_DIRECTA'' COMMENT ''Modo de salida: TERMICA_DIRECTA, VISOR_PDF, PDF24_CREATOR''',
    'SELECT "Columna modo_salida_tickets ya existe"'
);

PREPARE stmt_modo FROM @sql_modo;
EXECUTE stmt_modo;
DEALLOCATE PREPARE stmt_modo;

-- =====================================================================
-- SCRIPT DE ACTUALIZACIÓN SEGURA DE ESQUEMA (MIGRACIÓN SIN PÉRDIDA DE DATOS)
-- =====================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS upgrade_restaurante_schema$$
CREATE PROCEDURE upgrade_restaurante_schema()
BEGIN
    DECLARE v_cierre_generado_correcto INT DEFAULT 0;
    DECLARE v_fk_pago VARCHAR(64) DEFAULT NULL;
    DECLARE v_sql TEXT;
    -- 1. Ampliación de columnas en config y usuarios
    ALTER TABLE config MODIFY COLUMN ruc VARCHAR(30) COLLATE utf8_spanish_ci NOT NULL;
    ALTER TABLE config MODIFY COLUMN telefono VARCHAR(30) COLLATE utf8_spanish_ci NOT NULL;
    ALTER TABLE usuarios MODIFY COLUMN pass VARCHAR(255) COLLATE utf8_spanish_ci NOT NULL;

    -- 2. Columna logo_path en config
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'logo_path'
    ) THEN
        ALTER TABLE config ADD COLUMN logo_path VARCHAR(255) NULL DEFAULT NULL;
    END IF;

    -- 3. Columna tasa_dolar en config
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'tasa_dolar'
    ) THEN
        ALTER TABLE config ADD COLUMN tasa_dolar DECIMAL(12,4) NOT NULL DEFAULT 36.5000;
    END IF;

    -- 4. Columna iva_porcentaje en config
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'iva_porcentaje'
    ) THEN
        ALTER TABLE config ADD COLUMN iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 16.00;
    END IF;

    -- 5. Columnas bimonetarias en pedidos
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'tasa_cambio'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN tasa_cambio DECIMAL(12,4) NULL DEFAULT NULL AFTER total;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'tasa_es_estimada'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN tasa_es_estimada TINYINT(1) NOT NULL DEFAULT 0 AFTER tasa_cambio;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'total_bs'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN total_bs DECIMAL(14,2) NULL DEFAULT NULL AFTER tasa_cambio;
    END IF;

    -- 6. Columnas fiscales en pedidos
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'subtotal'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER total_bs;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'iva_porcentaje'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 16.00 AFTER subtotal;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'iva_monto'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN iva_monto DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER iva_porcentaje;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'subtotal_bs'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN subtotal_bs DECIMAL(14,2) NOT NULL DEFAULT 0.00 AFTER iva_monto;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'iva_bs'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN iva_bs DECIMAL(14,2) NOT NULL DEFAULT 0.00 AFTER subtotal_bs;
    END IF;

    -- 7. Correo único en usuarios
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND index_name = 'uq_usuarios_correo'
    ) THEN
        ALTER TABLE usuarios ADD UNIQUE KEY uq_usuarios_correo (correo);
    END IF;

    -- 8. Un único pedido pendiente por mesa (columnas generadas NULL para finalizados)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'id_sala_pendiente'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN id_sala_pendiente INT
          GENERATED ALWAYS AS (CASE WHEN estado = 'PENDIENTE' THEN id_sala ELSE NULL END) STORED;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'num_mesa_pendiente'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN num_mesa_pendiente INT
          GENERATED ALWAYS AS (CASE WHEN estado = 'PENDIENTE' THEN num_mesa ELSE NULL END) STORED;
    END IF;

    -- 9. Cliente predeterminado en config
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'cliente_predeterminado_nombre'
    ) THEN
        ALTER TABLE config ADD COLUMN cliente_predeterminado_nombre VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'cliente_predeterminado_documento'
    ) THEN
        ALTER TABLE config ADD COLUMN cliente_predeterminado_documento VARCHAR(30) NOT NULL DEFAULT 'V-00000000';
    END IF;

    -- 10. Datos de cliente en pedidos
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'cliente_nombre'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN cliente_nombre VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'cliente_documento'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN cliente_documento VARCHAR(30) NOT NULL DEFAULT 'V-00000000';
    END IF;

    -- 11. Tabla clientes
    CREATE TABLE IF NOT EXISTS clientes (
        id INT AUTO_INCREMENT PRIMARY KEY,
        documento VARCHAR(30) NOT NULL UNIQUE,
        nombre VARCHAR(150) NOT NULL,
        telefono VARCHAR(30) NULL,
        direccion TEXT NULL,
        creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    INSERT IGNORE INTO clientes (documento, nombre) VALUES ('V-00000000', 'Consumidor Final');

    -- 12. Método de pago en pedidos
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'metodo_pago'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN metodo_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO' AFTER cliente_documento;
    END IF;

    -- 13. Soft delete en platos (activo y desactivado_en)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'platos' AND column_name = 'activo'
    ) THEN
        ALTER TABLE platos ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'platos' AND column_name = 'desactivado_en'
    ) THEN
        ALTER TABLE platos ADD COLUMN desactivado_en DATETIME NULL DEFAULT NULL;
    END IF;

    -- 14. Política de retención de registros en config
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'meses_retencion_pedidos'
    ) THEN
        ALTER TABLE config ADD COLUMN meses_retencion_pedidos INT NOT NULL DEFAULT 24
          COMMENT 'Pedidos finalizados más antiguos que este valor en meses podrán ser purgados';
    END IF;

    -- 15. Índices de rendimiento para reportes, filtros y purga histórica
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND index_name = 'idx_pedidos_estado_fecha'
    ) THEN
        ALTER TABLE pedidos ADD INDEX idx_pedidos_estado_fecha (estado, fecha);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND index_name = 'idx_pedidos_cliente_doc'
    ) THEN
        ALTER TABLE pedidos ADD INDEX idx_pedidos_cliente_doc (cliente_documento);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'detalle_pedidos' AND index_name = 'idx_detalle_pedidos_nombre'
    ) THEN
        ALTER TABLE detalle_pedidos ADD INDEX idx_detalle_pedidos_nombre (nombre);
    END IF;

    -- 16. Opción de impresión de logo en tickera térmica (80mm)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'imprimir_logo_ticket'
    ) THEN
        ALTER TABLE config ADD COLUMN imprimir_logo_ticket TINYINT(1) NOT NULL DEFAULT 1
          COMMENT '1: Imprimir logo en tickera, 0: Omitir logo en tickera';
    END IF;

    -- 17. Impresora del sistema para tickets de 80mm
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'impresora_tickets'
    ) THEN
        ALTER TABLE config ADD COLUMN impresora_tickets VARCHAR(150) NOT NULL DEFAULT 'DEFAULT'
          COMMENT 'Nombre de impresora física o virtual para tickets';
    END IF;

    -- 18. Modo de salida de tickets (TERMICA_DIRECTA, VISOR_PDF, PDF24_CREATOR)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'modo_salida_tickets'
    ) THEN
        ALTER TABLE config ADD COLUMN modo_salida_tickets VARCHAR(50) NOT NULL DEFAULT 'TERMICA_DIRECTA'
          COMMENT 'Modo de salida predeterminado para tickets y facturas';
    END IF;

    -- 19. Tabla clave-valor para parámetros de configuración del sistema
    CREATE TABLE IF NOT EXISTS configuracion_sistema (
        clave VARCHAR(80) NOT NULL PRIMARY KEY,
        valor TEXT NOT NULL,
        descripcion VARCHAR(255) NULL,
        actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    -- 19b. Estado de intentos de login acotado al ID del usuario existente.
    CREATE TABLE IF NOT EXISTS login_intentos (
        usuario_id INT NOT NULL PRIMARY KEY,
        intentos INT NOT NULL DEFAULT 0,
        bloqueado_hasta_ms BIGINT NOT NULL DEFAULT 0,
        actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        CONSTRAINT fk_login_intentos_usuario FOREIGN KEY (usuario_id)
            REFERENCES usuarios(id) ON DELETE CASCADE,
        CONSTRAINT chk_login_intentos_no_negativos CHECK (intentos >= 0 AND bloqueado_hasta_ms >= 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
    -- Repara referencias mal configuradas, incluida una FK correcta solo en apariencia pero sin CASCADE.
    BEGIN
        DECLARE terminado BOOLEAN DEFAULT FALSE;
        DECLARE nombre_fk VARCHAR(64);
        DECLARE cursor_fk CURSOR FOR
            SELECT k.CONSTRAINT_NAME
            FROM information_schema.KEY_COLUMN_USAGE k
            JOIN information_schema.REFERENTIAL_CONSTRAINTS r
              ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
             AND r.TABLE_NAME = k.TABLE_NAME
             AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
            WHERE k.TABLE_SCHEMA = DATABASE()
              AND k.TABLE_NAME = 'login_intentos'
              AND k.COLUMN_NAME = 'usuario_id'
              AND NOT (k.REFERENCED_TABLE_NAME = 'usuarios'
                       AND k.REFERENCED_COLUMN_NAME = 'id'
                       AND r.DELETE_RULE = 'CASCADE');
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET terminado = TRUE;
        OPEN cursor_fk;
        bucle_fk: LOOP
            FETCH cursor_fk INTO nombre_fk;
            IF terminado THEN LEAVE bucle_fk; END IF;
            SET @sql_drop_fk = CONCAT('ALTER TABLE login_intentos DROP FOREIGN KEY `', REPLACE(nombre_fk, '`', '``'), '`');
            PREPARE stmt_drop_fk FROM @sql_drop_fk;
            EXECUTE stmt_drop_fk;
            DEALLOCATE PREPARE stmt_drop_fk;
        END LOOP;
        CLOSE cursor_fk;
    END;
    DELETE li FROM login_intentos li LEFT JOIN usuarios u ON u.id = li.usuario_id WHERE u.id IS NULL;
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.KEY_COLUMN_USAGE k
        JOIN information_schema.REFERENTIAL_CONSTRAINTS r
          ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
         AND r.TABLE_NAME = k.TABLE_NAME
         AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
        WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'login_intentos'
          AND k.COLUMN_NAME = 'usuario_id' AND k.REFERENCED_TABLE_NAME = 'usuarios'
          AND k.REFERENCED_COLUMN_NAME = 'id' AND r.DELETE_RULE = 'CASCADE'
    ) THEN
        ALTER TABLE login_intentos ADD CONSTRAINT fk_login_intentos_usuario
            FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;
    END IF;
    DELETE FROM configuracion_sistema WHERE LEFT(clave, 11) = '_sec_login_';

    -- Reconciliar defaults insertados por versiones anteriores con los valores legacy del dueño.
    -- Solo se reemplaza una clave KV que conserve el default original; un valor KV personalizado prevalece.
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'tasa_dolar', CAST(tasa_dolar AS CHAR), 'Tasa de cambio USD / Bs' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = '36.5000', VALUES(valor), configuracion_sistema.valor);
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'iva_porcentaje', CAST(iva_porcentaje AS CHAR), 'Porcentaje de IVA' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = '16.00', VALUES(valor), configuracion_sistema.valor);
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'impresora_tickets', impresora_tickets, 'Impresora predeterminada para tickets' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = 'DEFAULT', VALUES(valor), configuracion_sistema.valor);
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'modo_salida_tickets', modo_salida_tickets, 'Modo de salida de tickets' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = 'TERMICA_DIRECTA', VALUES(valor), configuracion_sistema.valor);
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'imprimir_logo_ticket', IF(imprimir_logo_ticket = 1, 'true', 'false'), 'Imprimir logo en ticket térmico 80mm' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = 'true', VALUES(valor), configuracion_sistema.valor);
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'cliente_predeterminado_nombre', cliente_predeterminado_nombre, 'Nombre de cliente por defecto en facturación' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = 'Consumidor Final', VALUES(valor), configuracion_sistema.valor);
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'cliente_predeterminado_documento', cliente_predeterminado_documento, 'Documento de cliente por defecto en facturación' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = 'V-00000000', VALUES(valor), configuracion_sistema.valor);
    INSERT INTO configuracion_sistema (clave, valor, descripcion)
    SELECT 'meses_retencion_pedidos', CAST(meses_retencion_pedidos AS CHAR), 'Meses de retención de pedidos' FROM config LIMIT 1
    ON DUPLICATE KEY UPDATE valor = IF(configuracion_sistema.valor = '24', VALUES(valor), configuracion_sistema.valor);

    INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion) VALUES
    ('tasa_dolar', '36.5000', 'Tasa de cambio USD / Bs'),
    ('iva_porcentaje', '16.00', 'Porcentaje de IVA'),
    ('impresora_tickets', 'DEFAULT', 'Impresora predeterminada para tickets'),
    ('modo_salida_tickets', 'TERMICA_DIRECTA', 'Modo de salida de tickets'),
    ('imprimir_logo_ticket', 'true', 'Imprimir logo en ticket térmico 80mm'),
    ('cliente_predeterminado_nombre', 'Consumidor Final', 'Nombre de cliente por defecto en facturación'),
    ('cliente_predeterminado_documento', 'V-00000000', 'Cédula/RIF de cliente por defecto en facturación'),
    ('meses_retencion_pedidos', '24', 'Meses de retención de pedidos');

    -- 20. Tabla de auditoría de pedidos (anulaciones y reimpresiones)
    CREATE TABLE IF NOT EXISTS auditoria_pedidos (
        id INT AUTO_INCREMENT PRIMARY KEY,
        id_pedido INT NOT NULL,
        accion VARCHAR(50) NOT NULL,
        motivo VARCHAR(255) NOT NULL,
        usuario VARCHAR(100) NOT NULL,
        fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_auditoria_pedidos_pedido (id_pedido),
        INDEX idx_auditoria_pedidos_fecha (fecha_hora)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    -- 21. Tabla de persistencia para Cierres de Caja y Arqueo de Efectivo
    CREATE TABLE IF NOT EXISTS cierres_caja (
        id INT AUTO_INCREMENT PRIMARY KEY,
        tipo VARCHAR(20) NOT NULL,
        fecha_jornada DATE NOT NULL,
        fecha_hora_emision DATETIME NOT NULL,
        usuario_emisor VARCHAR(100) NOT NULL,
        total_ventas_usd DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        total_ventas_bs DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        efectivo_esperado_bs DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        efectivo_declarado_bs DECIMAL(12,2) NULL DEFAULT NULL,
        diferencia_bs DECIMAL(12,2) NULL DEFAULT NULL,
        estado_conciliacion_bs VARCHAR(20) NULL DEFAULT NULL,
        efectivo_esperado_usd DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        efectivo_declarado_usd DECIMAL(12,2) NULL DEFAULT NULL,
        diferencia_usd DECIMAL(12,2) NULL DEFAULT NULL,
        estado_conciliacion_usd VARCHAR(20) NULL DEFAULT NULL,
        tasa_cambio DECIMAL(12,4) NOT NULL DEFAULT 36.5000,
        ruta_pdf VARCHAR(255) NULL,
        creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_cierres_fecha (fecha_jornada),
        INDEX idx_cierres_tipo (tipo)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    -- 22. Desglose de efectivo en pedidos para pagos mixtos y arqueo multi-moneda
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'efectivo_bs'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN efectivo_bs DECIMAL(14,2) NULL DEFAULT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'efectivo_usd'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN efectivo_usd DECIMAL(14,2) NULL DEFAULT NULL;
    END IF;

    -- 23. Tabla de mesoneros y columnas de mesonero en pedidos
    CREATE TABLE IF NOT EXISTS mesoneros (
        id INT AUTO_INCREMENT PRIMARY KEY,
        nombre_completo VARCHAR(150) NOT NULL,
        cedula VARCHAR(30) NOT NULL,
        telefono VARCHAR(30) NULL,
        activo TINYINT(1) NOT NULL DEFAULT 1,
        eliminado TINYINT(1) NOT NULL DEFAULT 0,
        creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_mesoneros_activo (activo),
        INDEX idx_mesoneros_eliminado (eliminado)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'id_mesonero'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN id_mesonero INT NULL DEFAULT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'mesonero_nombre'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN mesonero_nombre VARCHAR(150) NULL DEFAULT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND index_name = 'idx_pedidos_mesonero'
    ) THEN
        ALTER TABLE pedidos ADD INDEX idx_pedidos_mesonero (id_mesonero);
    END IF;

    -- 24. Soporte para IVA por plato y salas de tipo Barra
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'platos' AND column_name = 'aplica_iva'
    ) THEN
        ALTER TABLE platos ADD COLUMN aplica_iva TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'platos'
          AND column_name = 'aplica_iva' AND is_nullable = 'YES'
    ) THEN
        IF EXISTS (SELECT 1 FROM platos WHERE aplica_iva IS NULL) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'platos.aplica_iva contiene NULL: clasifique esos platos antes de actualizar.';
        END IF;
        ALTER TABLE platos MODIFY COLUMN aplica_iva TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'salas' AND column_name = 'tipo'
    ) THEN
        ALTER TABLE salas ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'SALON';
    END IF;

    -- 25. Preferencias de ancho de columnas por usuario y tabla (persisten el layout)
    CREATE TABLE IF NOT EXISTS preferencias_tabla (
        usuario_id INT NOT NULL,
        tabla_clave VARCHAR(64) NOT NULL,
        layout_json VARCHAR(2000) NOT NULL,
        actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (usuario_id, tabla_clave)
    );

    -- 26. Desglose de cada medio y moneda aplicado a pagos mixtos.
    CREATE TABLE IF NOT EXISTS pedido_pagos (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        id_pedido INT NOT NULL,
        metodo_pago VARCHAR(30) NOT NULL,
        moneda CHAR(3) NOT NULL,
        monto DECIMAL(14,2) NOT NULL,
        tasa_cambio DECIMAL(12,4) NOT NULL,
        monto_bs DECIMAL(14,2) NOT NULL,
        monto_usd DECIMAL(14,2) NOT NULL,
        creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_pedido_pagos_pedido (id_pedido),
        INDEX idx_pedido_pagos_metodo_fecha (metodo_pago, creado_en),
        CONSTRAINT fk_pedido_pagos_pedido FOREIGN KEY (id_pedido)
            REFERENCES pedidos(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    -- 27. Pedidos rápidos sin asociar una mesa física.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'tipo_pedido'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN tipo_pedido VARCHAR(16) NOT NULL DEFAULT 'MESA';
    END IF;
    ALTER TABLE pedidos MODIFY COLUMN id_sala INT NULL DEFAULT NULL;
    ALTER TABLE pedidos MODIFY COLUMN num_mesa INT NULL DEFAULT NULL;

    -- 28. Identidad normalizada de platos, categorías, favoritos y estado ANULADO
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'platos' AND column_name = 'nombre_clave'
    ) THEN
        ALTER TABLE platos ADD COLUMN nombre_clave VARCHAR(200)
          GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'platos' AND index_name = 'idx_platos_nombre_clave'
    ) THEN
        ALTER TABLE platos ADD INDEX idx_platos_nombre_clave (nombre_clave);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'detalle_pedidos' AND column_name = 'nombre_clave'
    ) THEN
        ALTER TABLE detalle_pedidos ADD COLUMN nombre_clave VARCHAR(200)
          GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'detalle_pedidos' AND index_name = 'idx_detalle_pedidos_nombre_clave'
    ) THEN
        ALTER TABLE detalle_pedidos ADD INDEX idx_detalle_pedidos_nombre_clave (nombre_clave);
    END IF;

    CREATE TABLE IF NOT EXISTS categorias (
        id INT AUTO_INCREMENT PRIMARY KEY,
        nombre VARCHAR(60) NOT NULL UNIQUE,
        color CHAR(7) NOT NULL DEFAULT '#6B7280',
        orden INT NOT NULL DEFAULT 0,
        creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    INSERT IGNORE INTO categorias (nombre, color, orden) VALUES ('General', '#6B7280', 0);

    CREATE TABLE IF NOT EXISTS plato_categoria (
        nombre_clave VARCHAR(200) NOT NULL,
        id_categoria INT NOT NULL,
        actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (nombre_clave),
        INDEX idx_plato_categoria_categoria (id_categoria)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    CREATE TABLE IF NOT EXISTS plato_favorito (
        nombre_clave VARCHAR(200) NOT NULL,
        creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (nombre_clave)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    ALTER TABLE pedidos MODIFY COLUMN estado
        ENUM('PENDIENTE','FINALIZADO','ANULADO') COLLATE utf8_spanish_ci NOT NULL DEFAULT 'PENDIENTE';

    -- 29. Condición fiscal por línea y nombres de usuario únicos/formateados.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'detalle_pedidos' AND column_name = 'aplica_iva'
    ) THEN
        ALTER TABLE detalle_pedidos ADD COLUMN aplica_iva TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    -- Endurecer detalle_pedidos.aplica_iva en instalaciones heredadas (solo si no hay NULL históricos).
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'detalle_pedidos'
          AND column_name = 'aplica_iva' AND is_nullable = 'YES'
    ) THEN
        IF NOT EXISTS (SELECT 1 FROM detalle_pedidos WHERE aplica_iva IS NULL) THEN
            ALTER TABLE detalle_pedidos MODIFY COLUMN aplica_iva TINYINT(1) NOT NULL DEFAULT 1;
        END IF;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND column_name = 'nombre_unico'
    ) THEN
        ALTER TABLE usuarios ADD COLUMN nombre_unico VARCHAR(200)
          GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED;
    END IF;

    IF EXISTS (SELECT 1 FROM usuarios WHERE LOWER(TRIM(nombre)) = 'admin') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Nombre de usuario reservado: admin. Renombre esa cuenta antes de actualizar.';
    END IF;
    UPDATE usuarios SET nombre = TRIM(nombre);

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND index_name = 'uq_usuarios_nombre'
    ) THEN
        ALTER TABLE usuarios ADD UNIQUE KEY uq_usuarios_nombre (nombre_unico);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND constraint_name = 'chk_usuarios_nombre_formato'
    ) THEN
        ALTER TABLE usuarios DROP CHECK chk_usuarios_nombre_formato;
    END IF;
    ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_nombre_formato CHECK
      (CHAR_LENGTH(nombre) BETWEEN 3 AND 60 AND LOWER(TRIM(nombre)) <> 'admin'
       AND REGEXP_LIKE(nombre, '^[[:alnum:]][[:alnum:] ._-]{1,58}[[:alnum:]]$'));

    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'detalle_pedidos' AND constraint_name = 'chk_detalle_aplica_iva'
    ) THEN
        ALTER TABLE detalle_pedidos DROP CHECK chk_detalle_aplica_iva;
    END IF;
    ALTER TABLE detalle_pedidos ADD CONSTRAINT chk_detalle_aplica_iva
      CHECK (aplica_iva IN (0,1));

    -- No se completan campos fiscales heredados desde el catálogo actual y no se
    -- recalculan total/subtotal/IVA ni equivalentes de pedidos ya registrados.

    -- 30. Barrera de control de mantenimiento concurrente
    CREATE TABLE IF NOT EXISTS restaurante_control (
        id TINYINT NOT NULL PRIMARY KEY,
        estado VARCHAR(20) NOT NULL,
        operacion_id VARCHAR(100) NULL,
        actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        CONSTRAINT chk_restaurante_control_estado CHECK (estado IN ('OPERATIVO', 'MANTENIMIENTO'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    INSERT IGNORE INTO restaurante_control (id, estado) VALUES (1, 'OPERATIVO');

    -- 31. Columna activo en usuarios para ciclo de vida y desactivación segura
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND column_name = 'activo'
    ) THEN
        ALTER TABLE usuarios ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    -- 32. Sal y hash PBKDF2 independiente para usuario de integración info@angelsifuentes.com
    UPDATE usuarios
    SET pass = 'pbkdf2-sha256$600000$kAoKN9v1KVjpT8qQvuYJFA$Jg2StMqHqNR0L2y94jRioh1DRX0zdH8u2cfulCgjur4'
    WHERE correo = 'info@angelsifuentes.com'
      AND pass = 'pbkdf2-sha256$600000$4rRaMK7S7lSdpTKgN6YRiA$O4BlX32Y2+lu3IjnBmn08WaE3waaVtdvv7xiuszXxNM';

    -- 33. Clave modo_atencion en configuracion_sistema (diccionario clave/valor)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'config' AND column_name = 'modo_atencion'
    ) THEN
        ALTER TABLE config DROP COLUMN modo_atencion;
    END IF;

    INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
    VALUES ('modo_atencion', 'OPCION_B', 'Modo predeterminado de atención de mesas');

    -- 34. Columna tasa_es_estimada en pedidos para marcar tasas de cambio estimadas por migración
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'tasa_es_estimada'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN tasa_es_estimada TINYINT(1) NOT NULL DEFAULT 0
            COMMENT 'Indica que la tasa_cambio fue asignada por la migración y no es exacta';
    END IF;

    -- 35. Precio primario explícito, snapshot fiscal y cierre independiente del pago.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='platos' AND column_name='moneda_base') THEN
        ALTER TABLE platos ADD COLUMN moneda_base CHAR(3) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='platos' AND column_name='precio_usd') THEN
        ALTER TABLE platos ADD COLUMN precio_usd DECIMAL(14,2) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='platos' AND column_name='precio_ves') THEN
        ALTER TABLE platos ADD COLUMN precio_ves DECIMAL(14,2) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='platos' AND column_name='precio_clasificado') THEN
        ALTER TABLE platos ADD COLUMN precio_clasificado TINYINT(1) NOT NULL DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='platos' AND column_name='precio_incluye_iva') THEN
        ALTER TABLE platos ADD COLUMN precio_incluye_iva TINYINT(1) NULL DEFAULT NULL;
    END IF;
    ALTER TABLE platos MODIFY COLUMN precio DECIMAL(10,2) NULL DEFAULT NULL;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='id_plato') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN id_plato INT NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='moneda_base') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN moneda_base CHAR(3) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='precio_incluye_iva') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN precio_incluye_iva TINYINT(1) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='iva_porcentaje') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN iva_porcentaje DECIMAL(5,2) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='precio_congelado') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN precio_congelado TINYINT(1) NOT NULL DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='congelado_motivo') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN congelado_motivo VARCHAR(500) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='congelado_por') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN congelado_por VARCHAR(100) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='congelado_en') THEN
        ALTER TABLE detalle_pedidos ADD COLUMN congelado_en DATETIME NULL DEFAULT NULL;
    END IF;
    ALTER TABLE detalle_pedidos MODIFY COLUMN precio DECIMAL(14,2) NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='cerrada_en') THEN
        ALTER TABLE pedidos ADD COLUMN cerrada_en DATETIME NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='version_precio') THEN
        ALTER TABLE pedidos ADD COLUMN version_precio INT NOT NULL DEFAULT 1;
    END IF;
    ALTER TABLE pedidos MODIFY COLUMN subtotal DECIMAL(14,2) NOT NULL DEFAULT 0.00,
        MODIFY COLUMN iva_monto DECIMAL(14,2) NOT NULL DEFAULT 0.00,
        MODIFY COLUMN total DECIMAL(14,2) NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='platos' AND constraint_name='chk_platos_precio_clasificado') THEN
        ALTER TABLE platos ADD CONSTRAINT chk_platos_precio_clasificado CHECK
          (precio_clasificado=0 OR (moneda_base IS NOT NULL AND moneda_base IN ('USD','VES')
           AND ((moneda_base='USD' AND precio_usd>0) OR (moneda_base='VES' AND precio_ves>0))
           AND precio_incluye_iva IS NOT NULL AND precio_incluye_iva IN (0,1)));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='platos' AND constraint_name='chk_platos_precio_iva_coherente') THEN
        ALTER TABLE platos ADD CONSTRAINT chk_platos_precio_iva_coherente CHECK (aplica_iva=1 OR precio_incluye_iva IS NULL OR precio_incluye_iva=0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='detalle_pedidos' AND constraint_name='chk_detalle_precio_iva_coherente') THEN
        ALTER TABLE detalle_pedidos ADD CONSTRAINT chk_detalle_precio_iva_coherente CHECK (aplica_iva=1 OR precio_incluye_iva IS NULL OR precio_incluye_iva=0);
    END IF;

    -- Clasificar platos heredados: USD como primera moneda y precio no incluye IVA
    UPDATE platos
    SET moneda_base = 'USD',
        precio_usd = COALESCE(precio_usd, precio),
        precio_ves = COALESCE(precio_ves, ROUND(COALESCE(precio_usd, precio) * (SELECT COALESCE(MAX(tasa_dolar), 36.5000) FROM config WHERE id = 1), 2)),
        precio_clasificado = 1,
        precio_incluye_iva = 0
    WHERE precio_clasificado = 0 OR moneda_base IS NULL OR precio_incluye_iva IS NULL;

    -- La identidad del producto es explícita; no se vinculan pedidos heredados por nombre.
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='detalle_pedidos' AND index_name='idx_detalle_pedidos_plato') THEN
        ALTER TABLE detalle_pedidos ADD INDEX idx_detalle_pedidos_plato (id_plato);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND table_name='detalle_pedidos' AND column_name='id_plato' AND referenced_table_name='platos') THEN
        ALTER TABLE detalle_pedidos ADD CONSTRAINT fk_detalle_pedidos_plato FOREIGN KEY (id_plato) REFERENCES platos(id) ON DELETE SET NULL;
    END IF;

    -- El cierre libera la mesa física; cerrada_en permanece NULL en toda venta histórica.
    SELECT COUNT(*) INTO v_cierre_generado_correcto FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='pedidos'
        AND column_name IN ('id_sala_pendiente','num_mesa_pendiente') AND LOWER(generation_expression) LIKE '%cerrada_en%';
    IF v_cierre_generado_correcto <> 2 THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pedidos' AND index_name='uq_pedidos_mesa_pendiente') THEN
            ALTER TABLE pedidos DROP INDEX uq_pedidos_mesa_pendiente;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='id_sala_pendiente') THEN
            ALTER TABLE pedidos DROP COLUMN id_sala_pendiente;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='num_mesa_pendiente') THEN
            ALTER TABLE pedidos DROP COLUMN num_mesa_pendiente;
        END IF;
        ALTER TABLE pedidos ADD COLUMN id_sala_pendiente INT GENERATED ALWAYS AS
          (CASE WHEN estado='PENDIENTE' AND cerrada_en IS NULL THEN id_sala ELSE NULL END) STORED;
        ALTER TABLE pedidos ADD COLUMN num_mesa_pendiente INT GENERATED ALWAYS AS
          (CASE WHEN estado='PENDIENTE' AND cerrada_en IS NULL THEN num_mesa ELSE NULL END) STORED;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pedidos' AND index_name='uq_pedidos_mesa_pendiente') THEN
        ALTER TABLE pedidos ADD UNIQUE INDEX uq_pedidos_mesa_pendiente (id_sala_pendiente,num_mesa_pendiente);
    END IF;

    CREATE TABLE IF NOT EXISTS auditoria_cambios_precio (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, accion VARCHAR(32) NOT NULL,
        producto_id INT NULL, pedido_id INT NULL, detalle_pedido_id INT NULL, moneda_base CHAR(3) NULL,
        precio_usd_anterior DECIMAL(14,2) NULL, precio_usd_nuevo DECIMAL(14,2) NULL,
        precio_ves_anterior DECIMAL(14,2) NULL, precio_ves_nuevo DECIMAL(14,2) NULL,
        tasa_anterior DECIMAL(12,4) NULL, tasa_nueva DECIMAL(12,4) NULL,
        total_anterior_ves DECIMAL(14,2) NULL, total_nuevo_ves DECIMAL(14,2) NULL,
        actor VARCHAR(100) NOT NULL, motivo VARCHAR(500) NULL, idempotency_key VARCHAR(100) NULL,
        creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_auditoria_precios_idempotencia(idempotency_key),
        KEY idx_auditoria_precios_producto_fecha(producto_id,creado_en),
        KEY idx_auditoria_precios_pedido_fecha(pedido_id,creado_en)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    CREATE TABLE IF NOT EXISTS pedido_liquidaciones (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, id_pedido INT NOT NULL, version_precio INT NOT NULL,
        saldo_restante_ves DECIMAL(14,2) NOT NULL, aplicado_ves DECIMAL(14,2) NOT NULL DEFAULT 0.00,
        aplicado_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00, tasa_cambio DECIMAL(12,4) NOT NULL,
        ajuste_cambio_bs DECIMAL(14,2) NOT NULL DEFAULT 0.00, diferencia_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00,
        version_regla VARCHAR(40) NOT NULL, actor VARCHAR(100) NOT NULL, idempotency_key VARCHAR(100) NOT NULL,
        creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_pedido_liquidacion_version(id_pedido,version_precio),
        UNIQUE KEY uq_pedido_liquidacion_idempotencia(idempotency_key),
        CONSTRAINT fk_pedido_liquidaciones_pedido FOREIGN KEY(id_pedido) REFERENCES pedidos(id) ON DELETE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    CREATE TABLE IF NOT EXISTS pedido_saldos_movimientos (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, id_pedido INT NOT NULL, id_liquidacion BIGINT NULL,
        tipo VARCHAR(20) NOT NULL, moneda CHAR(3) NOT NULL, monto_origen DECIMAL(14,2) NOT NULL,
        equivalente_ves DECIMAL(14,2) NOT NULL, tasa_cambio DECIMAL(12,4) NOT NULL,
        actor VARCHAR(100) NOT NULL, motivo VARCHAR(500) NOT NULL, idempotency_key VARCHAR(100) NOT NULL,
        creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_pedido_saldos_idempotencia(idempotency_key), KEY idx_pedido_saldos_pedido_fecha(id_pedido,creado_en),
        CONSTRAINT chk_pedido_saldos_tipo CHECK(tipo IN ('CREDITO','DEVOLUCION','ANULACION')),
        CONSTRAINT fk_pedido_saldos_pedido FOREIGN KEY(id_pedido) REFERENCES pedidos(id) ON DELETE RESTRICT,
        CONSTRAINT fk_pedido_saldos_liquidacion FOREIGN KEY(id_liquidacion) REFERENCES pedido_liquidaciones(id) ON DELETE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    -- Marchas de cocina inmutables y diferencias de preparación por pedido.
    CREATE TABLE IF NOT EXISTS comandas_marchas (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        id_pedido INT NOT NULL,
        secuencia INT NOT NULL,
        tipo VARCHAR(20) NOT NULL,
        actor VARCHAR(100) NOT NULL,
        creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_comandas_marchas_pedido_secuencia (id_pedido,secuencia),
        KEY idx_comandas_marchas_pedido_fecha (id_pedido,creado_en),
        CONSTRAINT chk_comandas_marchas_tipo CHECK (tipo IN ('INICIAL','INCREMENTO','AJUSTE','BASE_LEGADA')),
        CONSTRAINT fk_comandas_marchas_pedido FOREIGN KEY (id_pedido) REFERENCES pedidos(id) ON DELETE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    CREATE TABLE IF NOT EXISTS comandas_marchas_detalle (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        id_marcha BIGINT NOT NULL,
        accion VARCHAR(12) NOT NULL,
        nombre VARCHAR(200) NOT NULL,
        cantidad INT NOT NULL,
        comentario TEXT NULL,
        id_plato INT NULL,
        moneda_base CHAR(3) NULL,
        precio DECIMAL(14,2) NOT NULL DEFAULT 0.00,
        aplica_iva TINYINT(1) NULL,
        precio_incluye_iva TINYINT(1) NULL,
        iva_porcentaje DECIMAL(5,2) NULL,
        KEY idx_comandas_marchas_detalle_marcha (id_marcha),
        CONSTRAINT chk_comandas_marchas_accion CHECK (accion IN ('PREPARAR','RETIRAR')),
        CONSTRAINT chk_comandas_marchas_cantidad CHECK (cantidad > 0),
        CONSTRAINT fk_comandas_marchas_detalle_marcha FOREIGN KEY (id_marcha)
            REFERENCES comandas_marchas(id) ON DELETE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

    INSERT INTO comandas_marchas (id_pedido, secuencia, tipo, actor)
    SELECT p.id, 1, 'BASE_LEGADA', COALESCE(NULLIF(TRIM(p.usuario), ''), 'Sistema')
    FROM pedidos p
    WHERE p.estado='PENDIENTE' AND p.cerrada_en IS NULL
      AND NOT EXISTS (SELECT 1 FROM comandas_marchas m WHERE m.id_pedido=p.id);

    INSERT INTO comandas_marchas_detalle
        (id_marcha,accion,nombre,cantidad,comentario,id_plato,moneda_base,precio,aplica_iva,precio_incluye_iva,iva_porcentaje)
    SELECT m.id,'PREPARAR',d.nombre,d.cantidad,d.comentario,d.id_plato,d.moneda_base,d.precio,
           d.aplica_iva,d.precio_incluye_iva,d.iva_porcentaje
    FROM comandas_marchas m
    JOIN detalle_pedidos d ON d.id_pedido=m.id_pedido
    WHERE m.tipo='BASE_LEGADA'
      AND NOT EXISTS (SELECT 1 FROM comandas_marchas_detalle md WHERE md.id_marcha=m.id);

    -- Eventos antiguos conservan sus columnas originales; los campos nuevos NULL no reinterpretan el efectivo histórico.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido_pagos' AND column_name='monto_recibido') THEN
        ALTER TABLE pedido_pagos ADD COLUMN monto_recibido DECIMAL(14,2) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido_pagos' AND column_name='monto_aplicado') THEN
        ALTER TABLE pedido_pagos ADD COLUMN monto_aplicado DECIMAL(14,2) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido_pagos' AND column_name='cambio_devuelto') THEN
        ALTER TABLE pedido_pagos ADD COLUMN cambio_devuelto DECIMAL(14,2) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido_pagos' AND column_name='es_legado') THEN
        ALTER TABLE pedido_pagos ADD COLUMN es_legado TINYINT(1) NOT NULL DEFAULT 1;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido_pagos' AND column_name='id_liquidacion') THEN
        ALTER TABLE pedido_pagos ADD COLUMN id_liquidacion BIGINT NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido_pagos' AND column_name='idempotency_key') THEN
        ALTER TABLE pedido_pagos ADD COLUMN idempotency_key VARCHAR(100) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pedido_pagos' AND index_name='uq_pedido_pagos_idempotencia') THEN
        ALTER TABLE pedido_pagos ADD UNIQUE INDEX uq_pedido_pagos_idempotencia(idempotency_key);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='pedido_pagos' AND constraint_name='chk_pedido_pagos_montos') THEN
        ALTER TABLE pedido_pagos ADD CONSTRAINT chk_pedido_pagos_montos CHECK
          (es_legado=1 OR (monto_recibido IS NOT NULL AND monto_aplicado IS NOT NULL AND cambio_devuelto IS NOT NULL AND monto_recibido=monto_aplicado+cambio_devuelto));
    END IF;

    SELECT CONSTRAINT_NAME INTO v_fk_pago FROM information_schema.KEY_COLUMN_USAGE
      WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='pedido_pagos' AND COLUMN_NAME='id_pedido'
        AND REFERENCED_TABLE_NAME='pedidos' LIMIT 1;
    IF v_fk_pago IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE constraint_schema=DATABASE() AND constraint_name=v_fk_pago AND delete_rule='CASCADE') THEN
            SET @drop_fk_sql=CONCAT('ALTER TABLE pedido_pagos DROP FOREIGN KEY `',REPLACE(v_fk_pago,'`','``'),'`');
            PREPARE stmt_drop_fk FROM @drop_fk_sql; EXECUTE stmt_drop_fk; DEALLOCATE PREPARE stmt_drop_fk;
            SET v_fk_pago=NULL;
        END IF;
    END IF;
    IF v_fk_pago IS NULL THEN
        ALTER TABLE pedido_pagos ADD CONSTRAINT fk_pedido_pagos_pedido FOREIGN KEY(id_pedido) REFERENCES pedidos(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND table_name='pedido_pagos' AND column_name='id_liquidacion' AND referenced_table_name='pedido_liquidaciones') THEN
        ALTER TABLE pedido_pagos ADD CONSTRAINT fk_pedido_pagos_liquidacion FOREIGN KEY(id_liquidacion) REFERENCES pedido_liquidaciones(id) ON DELETE RESTRICT;
    END IF;

    -- F-26: Restricción de unicidad para Cierre Z por fecha de jornada
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='cierres_caja' AND column_name='fecha_jornada_z') THEN
        ALTER TABLE cierres_caja ADD COLUMN fecha_jornada_z DATE GENERATED ALWAYS AS (CASE WHEN `tipo` = 'TOTAL' THEN `fecha_jornada` ELSE NULL END) STORED;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='cierres_caja' AND index_name='uq_cierres_caja_z_fecha') THEN
        ALTER TABLE cierres_caja ADD UNIQUE KEY uq_cierres_caja_z_fecha (fecha_jornada_z);
    END IF;

    -- 35. Soft-delete de categorias (columna activo) y salvaguarda de categoria General
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'categorias' AND column_name = 'activo'
    ) THEN
        ALTER TABLE categorias ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1 AFTER orden;
    END IF;
    UPDATE categorias SET activo = 1 WHERE LOWER(TRIM(nombre)) = 'general';

    -- 36. Clave bootstrap_inicial_completado en configuracion_sistema
    IF NOT EXISTS (
        SELECT 1 FROM configuracion_sistema WHERE clave = 'bootstrap_inicial_completado'
    ) THEN
        -- Preservar cuentas existentes: solo pedir bootstrap si no hay un
        -- Administrador activo con credenciales distintas del fixture demo.
        -- El migrador JDBC además verifica el hash demo por PasswordHasher.
        INSERT INTO configuracion_sistema (clave, valor, descripcion)
        SELECT 'bootstrap_inicial_completado',
               IF(EXISTS (
                   SELECT 1 FROM usuarios
                   WHERE LOWER(TRIM(rol)) = 'administrador'
                     AND (activo = 1 OR activo IS NULL)
                     AND pass IS NOT NULL AND TRIM(pass) <> ''
                     AND pass NOT IN (
                         'admin',
                         '!BLOQUEADO_BOOTSTRAP_PENDIENTE!',
                         'pbkdf2-sha256$600000$4rRaMK7S7lSdpTKgN6YRiA$O4BlX32Y2+lu3IjnBmn08WaE3waaVtdvv7xiuszXxNM'
                     )
               ), '1', '0'),
               'Indica si se ha completado el asistente de bootstrap inicial'
        WHERE NOT EXISTS (
            SELECT 1 FROM configuracion_sistema WHERE clave = 'bootstrap_inicial_completado'
        );
    END IF;
END$$
DELIMITER ;

CALL upgrade_restaurante_schema();
DROP PROCEDURE IF EXISTS upgrade_restaurante_schema;

-- ============================================================================
-- CATALOGO AMPLIADO: +70 platos / +14 categorias (idempotente)
-- Espectro: aplica_iva(0/1) x precio_incluye_iva(0/1/NULL) x moneda_base(USD/VES)
-- ============================================================================
INSERT INTO categorias (id,nombre,color,orden) VALUES
(8,'Pizzas','#C0392B',1),
(9,'Pastas','#E67E22',2),
(10,'Hamburguesas','#8E44AD',3),
(11,'Pescados y Mariscos','#1ABC9C',4),
(12,'Sopas y Cremas','#F1C40F',5),
(13,'Ensaladas','#27AE60',6),
(14,'Desayunos','#D35400',7),
(15,'Panaderia y Reposteria','#B9770E',8),
(16,'Cafeteria','#6E2C00',9),
(17,'Cocteles y Mocktails','#7D3C98',10),
(18,'Vinos y Licores','#922B21',11),
(19,'Comida Vegana','#229954',12),
(20,'Menu Infantil','#2E86C1',13),
(21,'Sushi y Rolls','#884EA0',14)
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre),color=VALUES(color),orden=VALUES(orden);
INSERT INTO platos (id,nombre,precio,moneda_base,precio_usd,precio_ves,precio_clasificado,precio_incluye_iva,fecha,activo,desactivado_en,aplica_iva) VALUES
(21,'Pizza Margarita',3.50,'USD',3.50,3549.00,1,0,CURRENT_DATE(),1,NULL,1),
(22,'Pizza Pepperoni',4.99,'USD',4.99,5059.86,1,1,CURRENT_DATE(),1,NULL,1),
(23,'Pizza Cuatro Quesos',6844.50,'VES',6.75,6844.50,1,0,CURRENT_DATE(),1,NULL,1),
(24,'Pizza Vegetariana',5.25,'USD',5.25,5323.50,0,NULL,CURRENT_DATE(),1,NULL,1),
(25,'Pizza Prosciutto e Funghi',7605.00,'VES',7.50,7605.00,1,1,CURRENT_DATE(),1,NULL,1),
(26,'Espagueti a la Bolonesa',6.00,'USD',6.00,6084.00,1,0,CURRENT_DATE(),1,NULL,0),
(27,'Fettuccine Alfredo',6591.00,'VES',6.50,6591.00,1,0,CURRENT_DATE(),1,NULL,0),
(28,'Lasagna de Carne',8.25,'USD',8.25,8365.50,1,0,CURRENT_DATE(),1,NULL,1),
(29,'Penne Arrabbiata',5.75,'USD',5.75,5830.50,1,1,CURRENT_DATE(),1,NULL,1),
(30,'Ravioles de Ricotta',7098.00,'VES',7.00,7098.00,1,0,CURRENT_DATE(),1,NULL,1),
(31,'Hamburguesa Clasica',5577.00,'VES',5.50,5577.00,1,1,CURRENT_DATE(),1,NULL,1),
(32,'Hamburguesa Doble Queso',7.25,'USD',7.25,7351.50,1,0,CURRENT_DATE(),1,NULL,0),
(33,'Hamburguesa BBQ Bacon',8112.00,'VES',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,0),
(34,'Hamburguesa de Pollo Crispy',6.25,'USD',6.25,6337.50,1,0,CURRENT_DATE(),1,NULL,1),
(35,'Hamburguesa Vegana de Lentejas',6.75,'USD',6.75,6844.50,1,1,CURRENT_DATE(),1,NULL,1),
(36,'Filete de Salmon a la Plancha',12675.00,'VES',12.50,12675.00,0,NULL,CURRENT_DATE(),1,NULL,1),
(37,'Camarones al Ajillo',11154.00,'VES',11.00,11154.00,1,0,CURRENT_DATE(),1,NULL,1),
(38,'Pargo Frito Entero',14196.00,'VES',14.00,14196.00,1,1,CURRENT_DATE(),1,NULL,1),
(39,'Ceviche de Camaron',9.50,'USD',9.50,9633.00,1,0,CURRENT_DATE(),1,NULL,0),
(40,'Pulpo a la Gallega',13942.50,'VES',13.75,13942.50,1,0,CURRENT_DATE(),1,NULL,0),
(41,'Crema de Auyama',4.25,'USD',4.25,4309.50,1,0,CURRENT_DATE(),1,NULL,1),
(42,'Sopa de Pollo con Fideos',4.00,'USD',4.00,4056.00,1,1,CURRENT_DATE(),1,NULL,1),
(43,'Crema de Champinones',4816.50,'VES',4.75,4816.50,1,0,CURRENT_DATE(),1,NULL,1),
(44,'Sopa de Lentejas',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(45,'Caldo de Res',5.00,'USD',5.00,5070.00,1,0,CURRENT_DATE(),1,NULL,0),
(46,'Ensalada Cesar con Pollo',6591.00,'VES',6.50,6591.00,1,0,CURRENT_DATE(),1,NULL,0),
(47,'Ensalada Caprese',5.75,'USD',5.75,5830.50,0,NULL,CURRENT_DATE(),1,NULL,0),
(48,'Ensalada de Quinoa',6.25,'USD',6.25,6337.50,1,0,CURRENT_DATE(),1,NULL,1),
(49,'Ensalada Griega',6.00,'USD',6.00,6084.00,1,1,CURRENT_DATE(),1,NULL,1),
(50,'Ensalada de Atun',5577.00,'VES',5.50,5577.00,1,0,CURRENT_DATE(),1,NULL,1),
(51,'Desayuno Criollo',5323.50,'VES',5.25,5323.50,1,1,CURRENT_DATE(),1,NULL,1),
(52,'Huevos Rancheros',4.75,'USD',4.75,4816.50,1,0,CURRENT_DATE(),1,NULL,0),
(53,'Pancakes con Miel',4563.00,'VES',4.50,4563.00,1,0,CURRENT_DATE(),1,NULL,0),
(54,'Arepa con Perico',3.50,'USD',3.50,3549.00,1,0,CURRENT_DATE(),1,NULL,1),
(55,'Tostadas Francesas',4.25,'USD',4.25,4309.50,1,1,CURRENT_DATE(),1,NULL,1),
(56,'Croissant de Mantequilla',2788.50,'VES',2.75,2788.50,1,0,CURRENT_DATE(),1,NULL,1),
(57,'Torta Tres Leches',4563.00,'VES',4.50,4563.00,1,1,CURRENT_DATE(),1,NULL,1),
(58,'Cheesecake de Frutos Rojos',4.75,'USD',4.75,4816.50,1,0,CURRENT_DATE(),1,NULL,0),
(59,'Brownie con Helado',4056.00,'VES',4.00,4056.00,1,0,CURRENT_DATE(),1,NULL,0),
(60,'Milhojas de Crema',3.75,'USD',3.75,3802.50,1,0,CURRENT_DATE(),1,NULL,1),
(61,'Capuchino',2535.00,'VES',2.50,2535.00,0,NULL,CURRENT_DATE(),1,NULL,0),
(62,'Latte Vainilla',3.00,'USD',3.00,3042.00,1,1,CURRENT_DATE(),1,NULL,1),
(63,'Espresso Doble',2281.50,'VES',2.25,2281.50,1,0,CURRENT_DATE(),1,NULL,1),
(64,'Mocha Frappe',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(65,'Te Chai Latte',3.25,'USD',3.25,3295.50,1,0,CURRENT_DATE(),1,NULL,0),
(66,'Mojito Sin Alcohol',3549.00,'VES',3.50,3549.00,1,0,CURRENT_DATE(),1,NULL,0),
(67,'Pina Colada',4.25,'USD',4.25,4309.50,1,0,CURRENT_DATE(),1,NULL,1),
(68,'Daiquiri de Fresa',4.00,'USD',4.00,4056.00,1,1,CURRENT_DATE(),1,NULL,1),
(69,'Limonada Frozen',3042.00,'VES',3.00,3042.00,1,0,CURRENT_DATE(),1,NULL,1),
(70,'Sangria Sin Alcohol',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(71,'Copa de Vino Tinto',5.00,'USD',5.00,5070.00,1,0,CURRENT_DATE(),1,NULL,0),
(72,'Copa de Vino Blanco',5070.00,'VES',5.00,5070.00,1,0,CURRENT_DATE(),1,NULL,0),
(73,'Cerveza Artesanal IPA',4.50,'USD',4.50,4563.00,1,0,CURRENT_DATE(),1,NULL,1),
(74,'Whisky Etiqueta Negra (shot)',6.50,'USD',6.50,6591.00,1,1,CURRENT_DATE(),1,NULL,1),
(75,'Ron Aniejo (shot)',5577.00,'VES',5.50,5577.00,1,0,CURRENT_DATE(),1,NULL,1),
(76,'Bowl Vegano de Quinoa',7.50,'USD',7.50,7605.00,0,NULL,CURRENT_DATE(),1,NULL,1),
(77,'Curry de Garbanzos',6844.50,'VES',6.75,6844.50,1,1,CURRENT_DATE(),1,NULL,1),
(78,'Tacos de Jackfruit',6.25,'USD',6.25,6337.50,1,0,CURRENT_DATE(),1,NULL,0),
(79,'Lasagna Vegana',8112.00,'VES',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,0),
(80,'Salteado de Tofu y Verduras',7.25,'USD',7.25,7351.50,1,0,CURRENT_DATE(),1,NULL,1),
(81,'Mini Hamburguesa con Papas',4.50,'USD',4.50,4563.00,1,1,CURRENT_DATE(),1,NULL,1),
(82,'Nuggets de Pollo',4056.00,'VES',4.00,4056.00,1,0,CURRENT_DATE(),1,NULL,1),
(83,'Dedos de Queso',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(84,'Helado de Chocolate (Infantil)',2.50,'USD',2.50,2535.00,1,0,CURRENT_DATE(),1,NULL,0),
(85,'Roll California',6591.00,'VES',6.50,6591.00,1,0,CURRENT_DATE(),1,NULL,0),
(86,'Roll Filadelfia',7.00,'USD',7.00,7098.00,1,0,CURRENT_DATE(),1,NULL,1),
(87,'Nigiri de Salmon',5.50,'USD',5.50,5577.00,1,1,CURRENT_DATE(),1,NULL,1),
(88,'Sashimi de Atun',8619.00,'VES',8.50,8619.00,1,0,CURRENT_DATE(),1,NULL,1),
(89,'Roll Vegetariano',5830.50,'VES',5.75,5830.50,0,NULL,CURRENT_DATE(),1,NULL,0),
(90,'Gyoza de Cerdo',5323.50,'VES',5.25,5323.50,1,1,CURRENT_DATE(),1,NULL,1)
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre),precio=VALUES(precio),moneda_base=VALUES(moneda_base),precio_usd=VALUES(precio_usd),precio_ves=VALUES(precio_ves),precio_clasificado=VALUES(precio_clasificado),precio_incluye_iva=VALUES(precio_incluye_iva),aplica_iva=VALUES(aplica_iva);
INSERT INTO plato_categoria (nombre_clave,id_categoria) VALUES
('pizza margarita',8),
('pizza pepperoni',8),
('pizza cuatro quesos',8),
('pizza vegetariana',8),
('pizza prosciutto e funghi',8),
('espagueti a la bolonesa',9),
('fettuccine alfredo',9),
('lasagna de carne',9),
('penne arrabbiata',9),
('ravioles de ricotta',9),
('hamburguesa clasica',10),
('hamburguesa doble queso',10),
('hamburguesa bbq bacon',10),
('hamburguesa de pollo crispy',10),
('hamburguesa vegana de lentejas',10),
('filete de salmon a la plancha',11),
('camarones al ajillo',11),
('pargo frito entero',11),
('ceviche de camaron',11),
('pulpo a la gallega',11),
('crema de auyama',12),
('sopa de pollo con fideos',12),
('crema de champinones',12),
('sopa de lentejas',12),
('caldo de res',12),
('ensalada cesar con pollo',13),
('ensalada caprese',13),
('ensalada de quinoa',13),
('ensalada griega',13),
('ensalada de atun',13),
('desayuno criollo',14),
('huevos rancheros',14),
('pancakes con miel',14),
('arepa con perico',14),
('tostadas francesas',14),
('croissant de mantequilla',15),
('torta tres leches',15),
('cheesecake de frutos rojos',15),
('brownie con helado',15),
('milhojas de crema',15),
('capuchino',16),
('latte vainilla',16),
('espresso doble',16),
('mocha frappe',16),
('te chai latte',16),
('mojito sin alcohol',17),
('pina colada',17),
('daiquiri de fresa',17),
('limonada frozen',17),
('sangria sin alcohol',17),
('copa de vino tinto',18),
('copa de vino blanco',18),
('cerveza artesanal ipa',18),
('whisky etiqueta negra (shot)',18),
('ron aniejo (shot)',18),
('bowl vegano de quinoa',19),
('curry de garbanzos',19),
('tacos de jackfruit',19),
('lasagna vegana',19),
('salteado de tofu y verduras',19),
('mini hamburguesa con papas',20),
('nuggets de pollo',20),
('dedos de queso',20),
('helado de chocolate (infantil)',20),
('roll california',21),
('roll filadelfia',21),
('nigiri de salmon',21),
('sashimi de atun',21),
('roll vegetariano',21),
('gyoza de cerdo',21)
ON DUPLICATE KEY UPDATE id_categoria=VALUES(id_categoria);

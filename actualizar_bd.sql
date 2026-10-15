-- =====================================================================
-- SCRIPT DE ACTUALIZACIÓN SEGURA DE ESQUEMA (MIGRACIÓN SIN PÉRDIDA DE DATOS)
-- =====================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS upgrade_restaurante_schema$$
CREATE PROCEDURE upgrade_restaurante_schema()
BEGIN
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
        ALTER TABLE detalle_pedidos ADD COLUMN aplica_iva TINYINT(1) NULL DEFAULT NULL;
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

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'detalle_pedidos' AND constraint_name = 'chk_detalle_aplica_iva'
    ) THEN
        ALTER TABLE detalle_pedidos ADD CONSTRAINT chk_detalle_aplica_iva
          CHECK ((aplica_iva IS NULL) OR (aplica_iva IN (0,1)));
    END IF;

    UPDATE detalle_pedidos d LEFT JOIN platos p ON p.nombre_clave = d.nombre_clave
      SET d.aplica_iva = COALESCE(p.aplica_iva, 0) WHERE d.aplica_iva IS NULL;

    UPDATE detalle_pedidos SET aplica_iva = 0 WHERE aplica_iva IS NULL;

    ALTER TABLE detalle_pedidos MODIFY COLUMN aplica_iva TINYINT(1) NOT NULL DEFAULT 1;

    UPDATE pedidos p LEFT JOIN (
        SELECT id_pedido, SUM(precio * cantidad) AS subtotal,
               SUM(CASE WHEN aplica_iva = 1 THEN precio * cantidad ELSE 0 END) AS base_imponible
        FROM detalle_pedidos GROUP BY id_pedido
    ) d ON d.id_pedido = p.id
    SET p.subtotal = COALESCE(d.subtotal, p.subtotal),
        p.iva_monto = ROUND(COALESCE(d.base_imponible, 0) * COALESCE(p.iva_porcentaje, 0) / 100, 2),
        p.total = COALESCE(d.subtotal, p.subtotal)
            + ROUND(COALESCE(d.base_imponible, 0) * COALESCE(p.iva_porcentaje, 0) / 100, 2),
        p.subtotal_bs = ROUND(COALESCE(d.subtotal, p.subtotal) * p.tasa_cambio, 2),
        p.iva_bs = ROUND(ROUND(COALESCE(d.base_imponible, 0) * COALESCE(p.iva_porcentaje, 0) / 100, 2)
            * p.tasa_cambio, 2),
        p.total_bs = ROUND((COALESCE(d.subtotal, p.subtotal)
            + ROUND(COALESCE(d.base_imponible, 0) * COALESCE(p.iva_porcentaje, 0) / 100, 2))
            * p.tasa_cambio, 2)
    WHERE d.id_pedido IS NOT NULL AND p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 AND COALESCE(p.tasa_es_estimada, 0) = 0;

    UPDATE pedidos p LEFT JOIN (
        SELECT id_pedido, SUM(precio * cantidad) AS subtotal,
               SUM(CASE WHEN aplica_iva = 1 THEN precio * cantidad ELSE 0 END) AS base_imponible
        FROM detalle_pedidos GROUP BY id_pedido
    ) d ON d.id_pedido = p.id
    SET p.tasa_cambio = 36.5000,
        p.tasa_es_estimada = 1,
        p.subtotal = COALESCE(d.subtotal, p.subtotal),
        p.iva_monto = ROUND(COALESCE(d.base_imponible, 0) * COALESCE(p.iva_porcentaje, 0) / 100, 2),
        p.total = COALESCE(d.subtotal, p.subtotal)
            + ROUND(COALESCE(d.base_imponible, 0) * COALESCE(p.iva_porcentaje, 0) / 100, 2)
    WHERE d.id_pedido IS NOT NULL AND (p.tasa_cambio IS NULL OR p.tasa_cambio = 0);

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
END$$
DELIMITER ;

CALL upgrade_restaurante_schema();
DROP PROCEDURE IF EXISTS upgrade_restaurante_schema;

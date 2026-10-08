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
END$$
DELIMITER ;

CALL upgrade_restaurante_schema();
DROP PROCEDURE IF EXISTS upgrade_restaurante_schema;


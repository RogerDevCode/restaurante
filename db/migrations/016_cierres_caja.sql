-- Migración 016: Tabla de persistencia para Cierres de Caja (Corte X y Corte Z) y Arqueo de Efectivo
CREATE TABLE IF NOT EXISTS `cierres_caja` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `tipo` VARCHAR(20) NOT NULL,
    `fecha_jornada` DATE NOT NULL,
    `fecha_hora_emision` DATETIME NOT NULL,
    `usuario_emisor` VARCHAR(100) NOT NULL,
    `total_ventas_usd` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `total_ventas_bs` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `efectivo_esperado_bs` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `efectivo_declarado_bs` DECIMAL(12,2) NULL DEFAULT NULL,
    `diferencia_bs` DECIMAL(12,2) NULL DEFAULT NULL,
    `estado_conciliacion_bs` VARCHAR(20) NULL DEFAULT NULL,
    `efectivo_esperado_usd` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `efectivo_declarado_usd` DECIMAL(12,2) NULL DEFAULT NULL,
    `diferencia_usd` DECIMAL(12,2) NULL DEFAULT NULL,
    `estado_conciliacion_usd` VARCHAR(20) NULL DEFAULT NULL,
    `tasa_cambio` DECIMAL(12,4) NOT NULL DEFAULT 36.5000,
    `ruta_pdf` VARCHAR(255) NULL,
    `creado_en` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_cierres_fecha` (`fecha_jornada`),
    INDEX `idx_cierres_tipo` (`tipo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

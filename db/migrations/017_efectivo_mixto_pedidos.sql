-- Migración 017: Desglose de efectivo cobrado en Bolívares y Dólares para pagos mixtos y conciliación
ALTER TABLE `pedidos` ADD COLUMN `efectivo_bs` DECIMAL(14,2) NULL DEFAULT NULL;
ALTER TABLE `pedidos` ADD COLUMN `efectivo_usd` DECIMAL(14,2) NULL DEFAULT NULL;

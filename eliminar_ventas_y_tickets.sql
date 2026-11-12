-- ==============================================================================
-- RESTAURANTE 2026 - ELIMINAR VENTAS, PEDIDOS, TICKETS Y CIERRES
--
-- Conserva 100% intactos:
--   - Platos con precios, tasas, impuestos y categorias
--   - Salas y mesas (quedan 100% libres)
--   - Categorias del catalogo
--   - Mesoneros activos
--   - Clientes registrados (incluyendo Consumidor Final)
--   - Usuarios y accesos
--   - Configuracion fiscal, membrete, logo y parametros clave-valor
--   - Preferencias de columnas de tablas (interfaz visual)
--
-- Vacia y reinicia contadores AUTO_INCREMENT a 1:
--   - Pedidos (tickets / comandas / facturas)
--   - Detalle de pedidos (consumo de platos)
--   - Formas de pago (pedido_pagos)
--   - Liquidaciones de pedidos
--   - Saldos y movimientos de pedidos
--   - Comandas y marchas de cocina
--   - Cierres de caja (Corte X y Z)
--   - Auditoria de pedidos anulados
--   - Auditoria de cambios de precio
--   - Ledger fiscal (cadenas de bloques fiscales)
--   - Intentos fallidos de login
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Vaciar transacciones principales de ventas, consumo y cobranzas
TRUNCATE TABLE `detalle_pedidos`;
TRUNCATE TABLE `pedido_pagos`;
TRUNCATE TABLE `pedido_liquidaciones`;
TRUNCATE TABLE `pedido_saldos_movimientos`;
TRUNCATE TABLE `pedidos`;

-- 2. Vaciar comandas de cocina si existen
SET @existe_cmd = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'comandas_marchas_detalle');
SET @sql_cmd = IF(@existe_cmd > 0, 'TRUNCATE TABLE `comandas_marchas_detalle`', 'SELECT 1');
PREPARE stmt_cmd FROM @sql_cmd;
EXECUTE stmt_cmd;
DEALLOCATE PREPARE stmt_cmd;

SET @existe_cm = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'comandas_marchas');
SET @sql_cm = IF(@existe_cm > 0, 'TRUNCATE TABLE `comandas_marchas`', 'SELECT 1');
PREPARE stmt_cm FROM @sql_cm;
EXECUTE stmt_cm;
DEALLOCATE PREPARE stmt_cm;

-- 3. Vaciar cierres de caja y auditorias asociadas a ventas pasadas
TRUNCATE TABLE `cierres_caja`;
TRUNCATE TABLE `auditoria_pedidos`;
TRUNCATE TABLE `auditoria_cambios_precio`;
TRUNCATE TABLE `login_intentos`;

-- 4. Vaciar ledger fiscal si existe
SET @existe_ledger = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ledger_fiscal');
SET @sql_ledger = IF(@existe_ledger > 0, 'TRUNCATE TABLE `ledger_fiscal`', 'SELECT 1');
PREPARE stmt_ledger FROM @sql_ledger;
EXECUTE stmt_ledger;
DEALLOCATE PREPARE stmt_ledger;

SET @existe_ancla = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ledger_fiscal_ancla');
SET @sql_ancla = IF(@existe_ancla > 0, 'DELETE FROM `ledger_fiscal_ancla`', 'SELECT 1');
PREPARE stmt_ancla FROM @sql_ancla;
EXECUTE stmt_ancla;
DEALLOCATE PREPARE stmt_ancla;

-- 5. Reiniciar contadores AUTO_INCREMENT a 1
ALTER TABLE `pedidos` AUTO_INCREMENT = 1;
ALTER TABLE `detalle_pedidos` AUTO_INCREMENT = 1;
ALTER TABLE `pedido_liquidaciones` AUTO_INCREMENT = 1;
ALTER TABLE `pedido_saldos_movimientos` AUTO_INCREMENT = 1;
ALTER TABLE `pedido_pagos` AUTO_INCREMENT = 1;
ALTER TABLE `cierres_caja` AUTO_INCREMENT = 1;
ALTER TABLE `auditoria_pedidos` AUTO_INCREMENT = 1;
ALTER TABLE `auditoria_cambios_precio` AUTO_INCREMENT = 1;

SET @sql_ai_cm = IF(@existe_cm > 0, 'ALTER TABLE `comandas_marchas` AUTO_INCREMENT = 1', 'SELECT 1');
PREPARE stmt_ai_cm FROM @sql_ai_cm;
EXECUTE stmt_ai_cm;
DEALLOCATE PREPARE stmt_ai_cm;

SET @sql_ai_cmd = IF(@existe_cmd > 0, 'ALTER TABLE `comandas_marchas_detalle` AUTO_INCREMENT = 1', 'SELECT 1');
PREPARE stmt_ai_cmd FROM @sql_ai_cmd;
EXECUTE stmt_ai_cmd;
DEALLOCATE PREPARE stmt_ai_cmd;

-- 6. Asegurar estado operativo del sistema
UPDATE `restaurante_control` SET `estado` = 'OPERATIVO' WHERE `id` = 1;

SET FOREIGN_KEY_CHECKS = 1;

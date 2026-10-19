-- ==============================================================================
-- LIMPIEZA DE TRANSACCIONES E HISTORIAL PARA COMENZAR HOY (RESTAURANTE 2026)
-- Conserva intactos:
--   - 87 Platos reales con precios, impuestos y categorias
--   - Salas y mesas (quedan 100% libres)
--   - Categorias del catalogo
--   - Mesoneros activos
--   - Clientes registrados (incluyendo Consumidor Final)
--   - Usuarios y accesos
--   - Configuracion fiscal, tasa de cambio y datos del comercio
--
-- Vacia y reinicia a 1:
--   - Pedidos y detalles de pedido
--   - Pagos, liquidaciones y saldos de pedidos
--   - Cierres de caja diarios (Corte X y Z)
--   - Historial de auditoria de pedidos y precios
--   - Intentos fallidos de login
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Vaciar pedidos y consumo
TRUNCATE TABLE `comandas_marchas_detalle`;
TRUNCATE TABLE `comandas_marchas`;
TRUNCATE TABLE `detalle_pedidos`;
TRUNCATE TABLE `pedido_pagos`;
TRUNCATE TABLE `pedido_liquidaciones`;
TRUNCATE TABLE `pedido_saldos_movimientos`;
TRUNCATE TABLE `pedidos`;

-- 2. Vaciar historial contable, auditorias y preferencias
TRUNCATE TABLE `cierres_caja`;
TRUNCATE TABLE `auditoria_pedidos`;
TRUNCATE TABLE `auditoria_cambios_precio`;
TRUNCATE TABLE `login_intentos`;
TRUNCATE TABLE `preferencias_tabla`;

-- 3. Reiniciar contadores AUTO_INCREMENT a 1
ALTER TABLE `pedidos` AUTO_INCREMENT = 1;
ALTER TABLE `detalle_pedidos` AUTO_INCREMENT = 1;
ALTER TABLE `comandas_marchas` AUTO_INCREMENT = 1;
ALTER TABLE `comandas_marchas_detalle` AUTO_INCREMENT = 1;
ALTER TABLE `pedido_liquidaciones` AUTO_INCREMENT = 1;
ALTER TABLE `pedido_saldos_movimientos` AUTO_INCREMENT = 1;
ALTER TABLE `pedido_pagos` AUTO_INCREMENT = 1;
ALTER TABLE `cierres_caja` AUTO_INCREMENT = 1;

-- 4. Actualizar fecha de catalogo de platos al dia de hoy
UPDATE `platos` SET `fecha` = CURRENT_DATE() WHERE `activo` = 1;

-- 5. Asegurar estado operativo del sistema
UPDATE `restaurante_control` SET `estado` = 'OPERATIVO' WHERE `id` = 1;

SET FOREIGN_KEY_CHECKS = 1;

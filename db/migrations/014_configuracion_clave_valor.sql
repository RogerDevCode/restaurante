-- Migración 014: Creación de la tabla clave-valor 'configuracion_sistema' y migración inicial de parámetros
CREATE TABLE IF NOT EXISTS configuracion_sistema (
    clave VARCHAR(80) NOT NULL PRIMARY KEY,
    valor TEXT NOT NULL,
    descripcion VARCHAR(255) NULL,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

-- Migración de parámetros técnicos existentes de la tabla 'config' a 'configuracion_sistema'
INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'tasa_dolar', CAST(tasa_dolar AS CHAR), 'Tasa de cambio USD / Bs' FROM config LIMIT 1;

INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'iva_porcentaje', CAST(iva_porcentaje AS CHAR), 'Porcentaje de IVA' FROM config LIMIT 1;

INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'impresora_tickets', impresora_tickets, 'Impresora predeterminada para tickets' FROM config LIMIT 1;

INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'modo_salida_tickets', modo_salida_tickets, 'Modo de salida: TERMICA_DIRECTA, VISOR_PDF, PDF24_CREATOR' FROM config LIMIT 1;

INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'imprimir_logo_ticket', IF(imprimir_logo_ticket = 1, 'true', 'false'), 'Imprimir logo en ticket térmico 80mm' FROM config LIMIT 1;

INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'cliente_predeterminado_nombre', cliente_predeterminado_nombre, 'Nombre de cliente por defecto en facturación' FROM config LIMIT 1;

INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'cliente_predeterminado_documento', cliente_predeterminado_documento, 'Cédula/RIF de cliente por defecto en facturación' FROM config LIMIT 1;

INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion)
SELECT 'meses_retencion_pedidos', CAST(meses_retencion_pedidos AS CHAR), 'Meses de retención antes de depuración de historial' FROM config LIMIT 1;

-- Valores por defecto en caso de tabla config vacía
INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion) VALUES
('tasa_dolar', '36.5000', 'Tasa de cambio USD / Bs'),
('iva_porcentaje', '16.00', 'Porcentaje de IVA'),
('impresora_tickets', 'DEFAULT', 'Impresora predeterminada para tickets'),
('modo_salida_tickets', 'TERMICA_DIRECTA', 'Modo de salida de tickets'),
('imprimir_logo_ticket', 'true', 'Imprimir logo en ticket térmico 80mm'),
('cliente_predeterminado_nombre', 'Consumidor Final', 'Nombre de cliente por defecto en facturación'),
('cliente_predeterminado_documento', 'V-00000000', 'Cédula/RIF de cliente por defecto en facturación'),
('meses_retencion_pedidos', '24', 'Meses de retención de pedidos');

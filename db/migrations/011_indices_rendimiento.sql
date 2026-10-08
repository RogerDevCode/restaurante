-- Migración 011: Índices de rendimiento para reportes, filtros, historial y purga histórica

-- 1. Índice compuesto en (estado, fecha) para optimizar consultas de dashboard, filtros y purga
ALTER TABLE pedidos
  ADD INDEX idx_pedidos_estado_fecha (estado, fecha);

-- 2. Índice en cliente_documento para consultas rápidas por cliente
ALTER TABLE pedidos
  ADD INDEX idx_pedidos_cliente_doc (cliente_documento);

-- 3. Índice en nombre de plato para cálculo eficiente de Top Platos más vendidos
ALTER TABLE detalle_pedidos
  ADD INDEX idx_detalle_pedidos_nombre (nombre);

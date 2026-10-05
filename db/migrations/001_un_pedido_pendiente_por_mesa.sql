-- Aplicar una sola vez a bases existentes tras confirmar que no hay mesas duplicadas.
-- Las columnas calculadas quedan en NULL para pedidos finalizados, que sí pueden repetirse.
ALTER TABLE pedidos
  ADD COLUMN id_sala_pendiente INT
    GENERATED ALWAYS AS (CASE WHEN estado = 'PENDIENTE' THEN id_sala ELSE NULL END) STORED,
  ADD COLUMN num_mesa_pendiente INT
    GENERATED ALWAYS AS (CASE WHEN estado = 'PENDIENTE' THEN num_mesa ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_pedidos_mesa_pendiente (id_sala_pendiente, num_mesa_pendiente);

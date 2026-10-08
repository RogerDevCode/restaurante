-- Migración 008: Método de pago en pedidos
ALTER TABLE pedidos
  ADD COLUMN metodo_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO'
  AFTER cliente_documento;

-- Migración 010: Política de retención de registros
ALTER TABLE config
  ADD COLUMN meses_retencion_pedidos INT NOT NULL DEFAULT 24
  COMMENT 'Pedidos finalizados más antiguos que este valor en meses podrán ser purgados';

-- Migración 009: Soft delete en platos
ALTER TABLE platos
  ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN desactivado_en DATETIME NULL DEFAULT NULL;

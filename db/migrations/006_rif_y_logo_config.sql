-- Migración para soporte de RIF venezolano y logo/icono configurable en la empresa
ALTER TABLE config
  MODIFY COLUMN ruc VARCHAR(30) COLLATE utf8_spanish_ci NOT NULL,
  MODIFY COLUMN telefono VARCHAR(30) COLLATE utf8_spanish_ci NOT NULL,
  ADD COLUMN logo_path VARCHAR(255) NULL DEFAULT NULL;

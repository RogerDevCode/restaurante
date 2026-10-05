-- Migración para soportar tasa de cambio en dólares y moneda oficial en Bolívares (Bs.)
ALTER TABLE config
  ADD COLUMN tasa_dolar DECIMAL(12,4) NOT NULL DEFAULT 36.5000;

ALTER TABLE pedidos
  ADD COLUMN tasa_cambio DECIMAL(12,4) NULL DEFAULT NULL,
  ADD COLUMN total_bs DECIMAL(14,2) NULL DEFAULT NULL;

-- ============================================================================
-- MIGRACIÓN 019: IVA POR PLATO Y SALAS TIPO BARRA
-- Compatible con MySQL 8.0/8.4+. No destructivo.
-- ============================================================================

-- 1. Casilla para indicar si el plato aplica IVA (por defecto 1 = Sí aplica IVA)
ALTER TABLE platos ADD COLUMN IF NOT EXISTS aplica_iva TINYINT(1) NOT NULL DEFAULT 1;

-- 2. Tipo de sala para diferenciar entre 'SALON' y 'BARRA' (por defecto 'SALON')
ALTER TABLE salas ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'SALON';

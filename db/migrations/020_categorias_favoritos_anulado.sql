-- ============================================================================
-- MIGRACIÓN 020: CATEGORÍAS VISUALES, FAVORITOS, COLUMNA NORMALIZADA Y ANULACIÓN
-- Compatible con MySQL 8.0/8.4+. No destructivo.
-- NOTA: la aplicación sincroniza este esquema automáticamente al iniciar
-- (MigradorEsquemaJdbc, versión v4). Este script es para instalaciones nuevas
-- o uso manual a través de actualizar_bd / --migrate-db.
-- ============================================================================

-- 1. Clave normalizada de identidad de plato (LOWER(TRIM(nombre))) con índice.
--    Hereda el collation utf8_spanish_ci de platos.nombre.
ALTER TABLE platos
  ADD COLUMN nombre_clave VARCHAR(200)
    GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED;
ALTER TABLE platos ADD INDEX idx_platos_nombre_clave (nombre_clave);

-- 2. Misma clave normalizada en detalle_pedidos para el ranking de más vendidos.
ALTER TABLE detalle_pedidos
  ADD COLUMN nombre_clave VARCHAR(200)
    GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED;
ALTER TABLE detalle_pedidos ADD INDEX idx_detalle_pedidos_nombre_clave (nombre_clave);

-- 3. Categorías visuales con color (semilla: General).
CREATE TABLE IF NOT EXISTS categorias (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(60) NOT NULL UNIQUE,
  color CHAR(7) NOT NULL DEFAULT '#6B7280',
  orden INT NOT NULL DEFAULT 0,
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

INSERT IGNORE INTO categorias (nombre, color, orden) VALUES ('General', '#6B7280', 0);

-- 4. Asignación plato -> categoría. Identidad por nombre_clave (sin FK a platos).
CREATE TABLE IF NOT EXISTS plato_categoria (
  nombre_clave VARCHAR(200) NOT NULL,
  id_categoria INT NOT NULL,
  actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (nombre_clave),
  INDEX idx_plato_categoria_categoria (id_categoria)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

-- 5. Platos favoritos. Identidad por nombre_clave.
CREATE TABLE IF NOT EXISTS plato_favorito (
  nombre_clave VARCHAR(200) NOT NULL,
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (nombre_clave)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

-- 6. Habilitar la anulación de pedidos (antes solo PENDIENTE/FINALIZADO).
ALTER TABLE pedidos MODIFY COLUMN estado
  ENUM('PENDIENTE','FINALIZADO','ANULADO') COLLATE utf8_spanish_ci NOT NULL DEFAULT 'PENDIENTE';
-- Migración 018: Tabla de mesoneros y columnas de mesonero en pedidos
-- Soporte para asignación de mesoneros en mesas, soft-delete y modo vacaciones (hide)

CREATE TABLE IF NOT EXISTS mesoneros (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(150) NOT NULL,
    cedula VARCHAR(30) NOT NULL,
    telefono VARCHAR(30) NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    eliminado TINYINT(1) NOT NULL DEFAULT 0,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mesoneros_activo (activo),
    INDEX idx_mesoneros_eliminado (eliminado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

ALTER TABLE pedidos ADD COLUMN id_mesonero INT NULL DEFAULT NULL;
ALTER TABLE pedidos ADD COLUMN mesonero_nombre VARCHAR(150) NULL DEFAULT NULL;
ALTER TABLE pedidos ADD INDEX idx_pedidos_mesonero (id_mesonero);

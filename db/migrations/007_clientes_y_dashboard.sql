-- Migración 007: Soporte de clientes al facturar y estadísticas del dashboard
ALTER TABLE config
  ADD COLUMN cliente_predeterminado_nombre VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final',
  ADD COLUMN cliente_predeterminado_documento VARCHAR(30) NOT NULL DEFAULT 'V-00000000';

ALTER TABLE pedidos
  ADD COLUMN cliente_nombre VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final',
  ADD COLUMN cliente_documento VARCHAR(30) NOT NULL DEFAULT 'V-00000000';

CREATE TABLE IF NOT EXISTS clientes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  documento VARCHAR(30) NOT NULL UNIQUE,
  nombre VARCHAR(150) NOT NULL,
  telefono VARCHAR(30) NULL,
  direccion TEXT NULL,
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

INSERT IGNORE INTO clientes (documento, nombre) VALUES ('V-00000000', 'Consumidor Final');

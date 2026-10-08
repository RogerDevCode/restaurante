-- Migración 015: Tabla de auditoría de pedidos (anulaciones y reimpresiones)
-- Permite registrar cada acción crítica con usuario, motivo y fecha/hora exacta.

CREATE TABLE IF NOT EXISTS auditoria_pedidos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    accion VARCHAR(50) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    usuario VARCHAR(100) NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_auditoria_pedidos_pedido (id_pedido),
    INDEX idx_auditoria_pedidos_fecha (fecha_hora)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

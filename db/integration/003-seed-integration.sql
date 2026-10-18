-- Semilla requerida para la suite de integración MySQL (MySqlIntegrationIT)
-- Garantiza usuario administrador con correo de integración, tabla schema_migrations y pedido con detalle para pruebas de PDF.

CREATE TABLE IF NOT EXISTS `schema_migrations` (
  `version` int(11) NOT NULL,
  `nombre` varchar(160) NOT NULL,
  `checksum` varchar(80) NOT NULL,
  `estado` varchar(20) NOT NULL,
  `paso_actual` varchar(80) NOT NULL,
  `iniciada_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `aplicada_en` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `usuarios` (`id`, `nombre`, `correo`, `pass`, `rol`, `activo`)
VALUES (2, 'ANGEL SIFUENTES', 'info@angelsifuentes.com', 'pbkdf2-sha256$600000$Iso3MeZmbWWDrSK4Mxp3CA$glpT9zaEvFlHjnfm12SycJKNgXPDNE3yDh6DDQvJOn4', 'Administrador', 1)
ON DUPLICATE KEY UPDATE `rol` = 'Administrador', `pass` = VALUES(`pass`);

INSERT INTO `pedidos` (`id`, `id_sala`, `num_mesa`, `tipo_pedido`, `subtotal`, `iva_porcentaje`, `iva_monto`, `total`, `subtotal_bs`, `iva_bs`, `total_bs`, `tasa_cambio`, `usuario`, `estado`, `fecha`, `cerrada_en`, `cliente_nombre`, `cliente_documento`)
VALUES (100, 1, 1, 'MESA', 10.00, 16.00, 1.60, 11.60, 448.00, 71.68, 519.68, 44.8000, 'admin', 'FINALIZADO', '2022-05-18 00:31:52', '2022-05-18 00:31:52', 'Consumidor Final', 'V-00000000')
ON DUPLICATE KEY UPDATE `estado` = VALUES(`estado`), `fecha` = VALUES(`fecha`), `cerrada_en` = VALUES(`cerrada_en`);

INSERT INTO `detalle_pedidos` (`id`, `id_pedido`, `nombre`, `precio`, `cantidad`, `comentario`, `aplica_iva`, `id_plato`, `moneda_base`, `precio_incluye_iva`, `iva_porcentaje`)
VALUES (100, 100, 'Plato Semilla Integracion', 10.00, 1, '', 1, 1, 'USD', 0, 16.00)
ON DUPLICATE KEY UPDATE `id_pedido` = VALUES(`id_pedido`);

INSERT INTO `schema_migrations` (`version`, `nombre`, `checksum`, `estado`, `paso_actual`, `iniciada_en`, `aplicada_en`)
VALUES (13, 'sincronizacion_esquema_v13', 'ef81e8e90c5e928c1039d62553fe486c14d07c5e0f5b7f2a52a6ba720a998bc7', 'APLICADA', 'validada', NOW(), NOW())
ON DUPLICATE KEY UPDATE `estado` = 'APLICADA', `paso_actual` = 'validada', `checksum` = VALUES(`checksum`);

INSERT INTO `restaurante_control` (`id`, `estado`) VALUES (1, 'OPERATIVO')
ON DUPLICATE KEY UPDATE `estado` = 'OPERATIVO', `operacion_id` = NULL;

INSERT INTO `configuracion_sistema` (`clave`, `valor`, `descripcion`)
VALUES ('modo_atencion', 'OPCION_B', 'Modo predeterminado de atención de mesas')
ON DUPLICATE KEY UPDATE `valor` = 'OPCION_B';


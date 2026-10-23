-- MariaDB dump 10.19  Distrib 10.4.24-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: restaurante
-- ------------------------------------------------------
-- Server version	10.4.24-MariaDB

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `config`
--

DROP TABLE IF EXISTS `config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ruc` varchar(30) COLLATE utf8_spanish_ci NOT NULL,
  `nombre` varchar(255) COLLATE utf8_spanish_ci NOT NULL,
  `telefono` varchar(30) COLLATE utf8_spanish_ci NOT NULL,
  `direccion` text COLLATE utf8_spanish_ci NOT NULL,
  `mensaje` varchar(255) COLLATE utf8_spanish_ci NOT NULL,
  `tasa_dolar` decimal(12,4) NOT NULL DEFAULT 36.5000,
  `iva_porcentaje` decimal(5,2) NOT NULL DEFAULT 16.00,
  `logo_path` varchar(255) COLLATE utf8_spanish_ci DEFAULT NULL,
  `cliente_predeterminado_nombre` varchar(150) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'Consumidor Final',
  `cliente_predeterminado_documento` varchar(30) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'V-00000000',
  `meses_retencion_pedidos` int(11) NOT NULL DEFAULT 24,
  `imprimir_logo_ticket` tinyint(1) NOT NULL DEFAULT 1,
  `impresora_tickets` varchar(150) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'DEFAULT',
  `modo_salida_tickets` varchar(50) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'TERMICA_DIRECTA',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `pedidos`
--

DROP TABLE IF EXISTS `comandas_marchas_detalle`;
DROP TABLE IF EXISTS `comandas_marchas`;
DROP TABLE IF EXISTS `pedidos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pedidos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `id_sala` int(11) DEFAULT NULL,
  `num_mesa` int(11) DEFAULT NULL,
  `tipo_pedido` varchar(16) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'MESA',
  `fecha` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `total` decimal(14,2) NOT NULL,
  `estado` enum('PENDIENTE','FINALIZADO','ANULADO') COLLATE utf8_spanish_ci NOT NULL DEFAULT 'PENDIENTE',
  `usuario` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `cerrada_en` datetime DEFAULT NULL,
  `version_precio` int(11) NOT NULL DEFAULT 1,
  `id_sala_pendiente` int(11) GENERATED ALWAYS AS (CASE WHEN `estado` = 'PENDIENTE' AND `cerrada_en` IS NULL THEN `id_sala` ELSE NULL END) STORED,
  `num_mesa_pendiente` int(11) GENERATED ALWAYS AS (CASE WHEN `estado` = 'PENDIENTE' AND `cerrada_en` IS NULL THEN `num_mesa` ELSE NULL END) STORED,
  `subtotal` decimal(14,2) NOT NULL DEFAULT 0.00,
  `iva_porcentaje` decimal(5,2) NOT NULL DEFAULT 0.00,
  `iva_monto` decimal(14,2) NOT NULL DEFAULT 0.00,
  `tasa_cambio` decimal(12,4) DEFAULT NULL,
  `tasa_es_estimada` tinyint(1) NOT NULL DEFAULT 0,
  `subtotal_bs` decimal(14,2) NOT NULL DEFAULT 0.00,
  `iva_bs` decimal(14,2) NOT NULL DEFAULT 0.00,
  `total_bs` decimal(14,2) DEFAULT NULL,
  `cliente_nombre` varchar(150) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'Consumidor Final',
  `cliente_documento` varchar(30) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'V-00000000',
  `metodo_pago` varchar(30) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'EFECTIVO',
  `efectivo_bs` decimal(14,2) DEFAULT NULL,
  `efectivo_usd` decimal(14,2) DEFAULT NULL,
  `id_mesonero` int(11) DEFAULT NULL,
  `mesonero_nombre` varchar(150) COLLATE utf8_spanish_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `id_sala` (`id_sala`),
  KEY `idx_pedidos_estado_fecha` (`estado`,`fecha`),
  KEY `idx_pedidos_cliente_doc` (`cliente_documento`),
  KEY `idx_pedidos_mesonero` (`id_mesonero`),
  UNIQUE KEY `uq_pedidos_mesa_pendiente` (`id_sala_pendiente`,`num_mesa_pendiente`),
  CONSTRAINT `pedidos_ibfk_1` FOREIGN KEY (`id_sala`) REFERENCES `salas` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE TABLE `pedido_liquidaciones` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `id_pedido` int(11) NOT NULL,
  `version_precio` int(11) NOT NULL,
  `saldo_restante_ves` decimal(14,2) NOT NULL,
  `aplicado_ves` decimal(14,2) NOT NULL DEFAULT 0.00,
  `aplicado_usd` decimal(14,2) NOT NULL DEFAULT 0.00,
  `tasa_cambio` decimal(12,4) NOT NULL,
  `ajuste_cambio_bs` decimal(14,2) NOT NULL DEFAULT 0.00,
  `diferencia_usd` decimal(14,2) NOT NULL DEFAULT 0.00,
  `version_regla` varchar(40) COLLATE utf8_spanish_ci NOT NULL,
  `actor` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `idempotency_key` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_pedido_liquidacion_version` (`id_pedido`,`version_precio`),
  UNIQUE KEY `uq_pedido_liquidacion_idempotencia` (`idempotency_key`),
  CONSTRAINT `fk_pedido_liquidaciones_pedido` FOREIGN KEY (`id_pedido`) REFERENCES `pedidos` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

CREATE TABLE `pedido_saldos_movimientos` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `id_pedido` int(11) NOT NULL,
  `id_liquidacion` bigint(20) DEFAULT NULL,
  `tipo` varchar(20) COLLATE utf8_spanish_ci NOT NULL,
  `moneda` char(3) COLLATE utf8_spanish_ci NOT NULL,
  `monto_origen` decimal(14,2) NOT NULL,
  `equivalente_ves` decimal(14,2) NOT NULL,
  `tasa_cambio` decimal(12,4) NOT NULL,
  `actor` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `motivo` varchar(500) COLLATE utf8_spanish_ci NOT NULL,
  `idempotency_key` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_pedido_saldos_idempotencia` (`idempotency_key`),
  KEY `idx_pedido_saldos_pedido_fecha` (`id_pedido`,`creado_en`),
  CONSTRAINT `chk_pedido_saldos_tipo` CHECK (`tipo` IN ('CREDITO','DEVOLUCION','ANULACION')),
  CONSTRAINT `fk_pedido_saldos_pedido` FOREIGN KEY (`id_pedido`) REFERENCES `pedidos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pedido_saldos_liquidacion` FOREIGN KEY (`id_liquidacion`) REFERENCES `pedido_liquidaciones` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

CREATE TABLE `auditoria_cambios_precio` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accion` varchar(32) COLLATE utf8_spanish_ci NOT NULL,
  `producto_id` int(11) DEFAULT NULL,
  `pedido_id` int(11) DEFAULT NULL,
  `detalle_pedido_id` int(11) DEFAULT NULL,
  `moneda_base` char(3) COLLATE utf8_spanish_ci DEFAULT NULL,
  `precio_usd_anterior` decimal(14,2) DEFAULT NULL,
  `precio_usd_nuevo` decimal(14,2) DEFAULT NULL,
  `precio_ves_anterior` decimal(14,2) DEFAULT NULL,
  `precio_ves_nuevo` decimal(14,2) DEFAULT NULL,
  `tasa_anterior` decimal(12,4) DEFAULT NULL,
  `tasa_nueva` decimal(12,4) DEFAULT NULL,
  `total_anterior_ves` decimal(14,2) DEFAULT NULL,
  `total_nuevo_ves` decimal(14,2) DEFAULT NULL,
  `actor` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `motivo` varchar(500) COLLATE utf8_spanish_ci DEFAULT NULL,
  `idempotency_key` varchar(100) COLLATE utf8_spanish_ci DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_auditoria_precios_idempotencia` (`idempotency_key`),
  KEY `idx_auditoria_precios_producto_fecha` (`producto_id`,`creado_en`),
  KEY `idx_auditoria_precios_pedido_fecha` (`pedido_id`,`creado_en`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

--
-- Table structure for table `pedido_pagos`
--

DROP TABLE IF EXISTS `pedido_pagos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pedido_pagos` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `id_pedido` int(11) NOT NULL,
  `metodo_pago` varchar(30) COLLATE utf8_spanish_ci NOT NULL,
  `moneda` char(3) COLLATE utf8_spanish_ci NOT NULL,
  `monto` decimal(14,2) NOT NULL,
  `tasa_cambio` decimal(12,4) NOT NULL,
  `monto_bs` decimal(14,2) NOT NULL,
  `monto_usd` decimal(14,2) NOT NULL,
  `monto_recibido` decimal(14,2) DEFAULT NULL,
  `monto_aplicado` decimal(14,2) DEFAULT NULL,
  `cambio_devuelto` decimal(14,2) DEFAULT NULL,
  `es_legado` tinyint(1) NOT NULL DEFAULT 1,
  `id_liquidacion` bigint(20) DEFAULT NULL,
  `idempotency_key` varchar(100) COLLATE utf8_spanish_ci DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_pedido_pagos_pedido` (`id_pedido`),
  KEY `idx_pedido_pagos_metodo_fecha` (`metodo_pago`,`creado_en`),
  UNIQUE KEY `uq_pedido_pagos_idempotencia` (`idempotency_key`),
  CONSTRAINT chk_pedido_pagos_montos CHECK (es_legado = 1 OR (monto_recibido IS NOT NULL AND monto_aplicado IS NOT NULL AND cambio_devuelto IS NOT NULL AND monto_recibido = monto_aplicado + cambio_devuelto)),
  CONSTRAINT fk_pedido_pagos_pedido FOREIGN KEY (id_pedido) REFERENCES pedidos (id) ON DELETE RESTRICT,
  CONSTRAINT fk_pedido_pagos_liquidacion FOREIGN KEY (id_liquidacion) REFERENCES pedido_liquidaciones (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pedidos`
--


--
-- Table structure for table `clientes`
--

DROP TABLE IF EXISTS `clientes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `clientes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `documento` varchar(30) COLLATE utf8_spanish_ci NOT NULL,
  `nombre` varchar(150) COLLATE utf8_spanish_ci NOT NULL,
  `telefono` varchar(30) COLLATE utf8_spanish_ci DEFAULT NULL,
  `direccion` text COLLATE utf8_spanish_ci DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_clientes_documento` (`documento`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `clientes`
--


--
-- Table structure for table `platos`
--

DROP TABLE IF EXISTS `platos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `platos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `precio` decimal(10,2) DEFAULT NULL,
  `moneda_base` char(3) COLLATE utf8_spanish_ci DEFAULT NULL,
  `precio_usd` decimal(14,2) DEFAULT NULL,
  `precio_ves` decimal(14,2) DEFAULT NULL,
  `precio_clasificado` tinyint(1) NOT NULL DEFAULT 0,
  `precio_incluye_iva` tinyint(1) DEFAULT NULL,
  `fecha` date DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `desactivado_en` datetime DEFAULT NULL,
  `aplica_iva` tinyint(1) NOT NULL DEFAULT 1,
  `nombre_clave` varchar(200) GENERATED ALWAYS AS (lower(trim(`nombre`))) STORED,
  PRIMARY KEY (`id`),
  KEY `idx_platos_nombre_clave` (`nombre_clave`),
  CONSTRAINT chk_platos_precio_clasificado CHECK (precio_clasificado = 0 OR (moneda_base IS NOT NULL AND moneda_base IN ('USD','VES') AND ((moneda_base='USD' AND precio_usd>0) OR (moneda_base='VES' AND precio_ves>0)) AND precio_incluye_iva IS NOT NULL AND precio_incluye_iva IN (0,1))),
  CONSTRAINT chk_platos_precio_iva_coherente CHECK (aplica_iva = 1 OR precio_incluye_iva IS NULL OR precio_incluye_iva = 0)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `platos`
--


--
-- Table structure for table `salas`
--

DROP TABLE IF EXISTS `salas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `salas` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `mesas` int(11) NOT NULL,
  `tipo` varchar(20) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'SALON',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `salas`
--


--
-- Table structure for table `usuarios`
--

DROP TABLE IF EXISTS `usuarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `usuarios` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `correo` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `pass` varchar(255) COLLATE utf8_spanish_ci NOT NULL,
  `rol` varchar(20) COLLATE utf8_spanish_ci NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `nombre_unico` varchar(200) GENERATED ALWAYS AS (lower(trim(`nombre`))) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_usuarios_correo` (`correo`),
  UNIQUE KEY `uq_usuarios_nombre` (`nombre_unico`),
  CONSTRAINT `chk_usuarios_nombre_formato` CHECK ((char_length(`nombre`) between 3 and 60) and (lower(trim(`nombre`)) <> 'admin') and regexp_like(`nombre`, '^[[:alnum:]][[:alnum:] ._-]{1,58}[[:alnum:]]$'))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- Estado de intentos de autenticación; está limitado a cuentas existentes y se limpia al eliminarlas.
DROP TABLE IF EXISTS `login_intentos`;
CREATE TABLE `login_intentos` (
  `usuario_id` int(11) NOT NULL,
  `intentos` int(11) NOT NULL DEFAULT 0,
  `bloqueado_hasta_ms` bigint(20) NOT NULL DEFAULT 0,
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`usuario_id`),
  CONSTRAINT `fk_login_intentos_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_login_intentos_no_negativos` CHECK ((`intentos` >= 0) and (`bloqueado_hasta_ms` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

--
-- Dumping data for table `config`
--


--
-- Table structure for table `detalle_pedidos`
--

DROP TABLE IF EXISTS `detalle_pedidos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `detalle_pedidos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `precio` decimal(14,2) NOT NULL,
  `id_plato` int(11) DEFAULT NULL,
  `moneda_base` char(3) COLLATE utf8_spanish_ci DEFAULT NULL,
  `precio_incluye_iva` tinyint(1) DEFAULT NULL,
  `iva_porcentaje` decimal(5,2) DEFAULT NULL,
  `precio_congelado` tinyint(1) NOT NULL DEFAULT 0,
  `congelado_motivo` varchar(500) COLLATE utf8_spanish_ci DEFAULT NULL,
  `congelado_por` varchar(100) COLLATE utf8_spanish_ci DEFAULT NULL,
  `congelado_en` datetime DEFAULT NULL,
  `cantidad` int(11) NOT NULL,
  `comentario` text COLLATE utf8_spanish_ci DEFAULT NULL,
  `id_pedido` int(11) NOT NULL,
  `aplica_iva` tinyint(1) NOT NULL DEFAULT 1,
  `nombre_clave` varchar(200) GENERATED ALWAYS AS (lower(trim(`nombre`))) STORED,
  PRIMARY KEY (`id`),
  KEY `id_pedido` (`id_pedido`),
  KEY `idx_detalle_pedidos_nombre` (`nombre`),
  KEY `idx_detalle_pedidos_nombre_clave` (`nombre_clave`),
  KEY `idx_detalle_pedidos_plato` (`id_plato`),
  CONSTRAINT `chk_detalle_aplica_iva` CHECK (`aplica_iva` in (0,1)),
  CONSTRAINT chk_detalle_precio_iva_coherente CHECK (aplica_iva = 1 OR precio_incluye_iva IS NULL OR precio_incluye_iva = 0),
  CONSTRAINT `detalle_pedidos_ibfk_1` FOREIGN KEY (`id_pedido`) REFERENCES `pedidos` (`id`),
  CONSTRAINT `fk_detalle_pedidos_plato` FOREIGN KEY (`id_plato`) REFERENCES `platos` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- Marchas de cocina inmutables y snapshots de líneas.
CREATE TABLE `comandas_marchas` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `id_pedido` int(11) NOT NULL,
  `secuencia` int(11) NOT NULL,
  `tipo` varchar(20) COLLATE utf8_spanish_ci NOT NULL,
  `actor` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_comandas_marchas_pedido_secuencia` (`id_pedido`,`secuencia`),
  KEY `idx_comandas_marchas_pedido_fecha` (`id_pedido`,`creado_en`),
  CONSTRAINT `chk_comandas_marchas_tipo` CHECK (`tipo` in ('INICIAL','INCREMENTO','AJUSTE','BASE_LEGADA')),
  CONSTRAINT `fk_comandas_marchas_pedido` FOREIGN KEY (`id_pedido`) REFERENCES `pedidos` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
CREATE TABLE `comandas_marchas_detalle` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `id_marcha` bigint(20) NOT NULL,
  `accion` varchar(12) COLLATE utf8_spanish_ci NOT NULL,
  `nombre` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `cantidad` int(11) NOT NULL,
  `comentario` text COLLATE utf8_spanish_ci DEFAULT NULL,
  `id_plato` int(11) DEFAULT NULL,
  `moneda_base` char(3) COLLATE utf8_spanish_ci DEFAULT NULL,
  `precio` decimal(14,2) NOT NULL DEFAULT 0.00,
  `aplica_iva` tinyint(1) DEFAULT NULL,
  `precio_incluye_iva` tinyint(1) DEFAULT NULL,
  `iva_porcentaje` decimal(5,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_comandas_marchas_detalle_marcha` (`id_marcha`),
  CONSTRAINT `chk_comandas_marchas_accion` CHECK (`accion` in ('PREPARAR','RETIRAR')),
  CONSTRAINT `chk_comandas_marchas_cantidad` CHECK (`cantidad` > 0),
  CONSTRAINT `fk_comandas_marchas_detalle_marcha` FOREIGN KEY (`id_marcha`) REFERENCES `comandas_marchas` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;

-- Dumping data for table `detalle_pedidos`



--
-- Dumping data for table `usuarios`
--


--
-- Table structure for table `configuracion_sistema`
--

DROP TABLE IF EXISTS `configuracion_sistema`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `configuracion_sistema` (
  `clave` varchar(80) COLLATE utf8_spanish_ci NOT NULL,
  `valor` text COLLATE utf8_spanish_ci NOT NULL,
  `descripcion` varchar(255) COLLATE utf8_spanish_ci DEFAULT NULL,
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `configuracion_sistema`
--


--
-- Table structure for table `auditoria_pedidos`
--

DROP TABLE IF EXISTS `auditoria_pedidos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `auditoria_pedidos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `id_pedido` int(11) NOT NULL,
  `accion` varchar(50) COLLATE utf8_spanish_ci NOT NULL,
  `motivo` varchar(255) COLLATE utf8_spanish_ci NOT NULL,
  `usuario` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `fecha_hora` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_auditoria_pedidos_pedido` (`id_pedido`),
  KEY `idx_auditoria_pedidos_fecha` (`fecha_hora`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cierres_caja`
--

DROP TABLE IF EXISTS `cierres_caja`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cierres_caja` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tipo` varchar(20) COLLATE utf8_spanish_ci NOT NULL,
  `fecha_jornada` date NOT NULL,
  `fecha_hora_emision` datetime NOT NULL,
  `usuario_emisor` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `total_ventas_usd` decimal(12,2) NOT NULL DEFAULT 0.00,
  `total_ventas_bs` decimal(12,2) NOT NULL DEFAULT 0.00,
  `efectivo_esperado_bs` decimal(12,2) NOT NULL DEFAULT 0.00,
  `efectivo_declarado_bs` decimal(12,2) DEFAULT NULL,
  `diferencia_bs` decimal(12,2) DEFAULT NULL,
  `estado_conciliacion_bs` varchar(20) COLLATE utf8_spanish_ci DEFAULT NULL,
  `efectivo_esperado_usd` decimal(12,2) NOT NULL DEFAULT 0.00,
  `efectivo_declarado_usd` decimal(12,2) DEFAULT NULL,
  `diferencia_usd` decimal(12,2) DEFAULT NULL,
  `estado_conciliacion_usd` varchar(20) COLLATE utf8_spanish_ci DEFAULT NULL,
  `tasa_cambio` decimal(12,4) NOT NULL DEFAULT 36.5000,
  `ruta_pdf` varchar(255) COLLATE utf8_spanish_ci DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `fecha_jornada_z` date GENERATED ALWAYS AS (CASE WHEN `tipo` = 'TOTAL' THEN `fecha_jornada` ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  KEY `idx_cierres_fecha` (`fecha_jornada`),
  KEY `idx_cierres_tipo` (`tipo`),
  UNIQUE KEY `uq_cierres_caja_z_fecha` (`fecha_jornada_z`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `preferencias_tabla`
--

DROP TABLE IF EXISTS `preferencias_tabla`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `preferencias_tabla` (
  `usuario_id` int(11) NOT NULL,
  `tabla_clave` varchar(64) COLLATE utf8_spanish_ci NOT NULL,
  `layout_json` varchar(2000) COLLATE utf8_spanish_ci NOT NULL,
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`usuario_id`,`tabla_clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mesoneros`
--

DROP TABLE IF EXISTS `mesoneros`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mesoneros` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre_completo` varchar(150) COLLATE utf8_spanish_ci NOT NULL,
  `cedula` varchar(30) COLLATE utf8_spanish_ci NOT NULL,
  `telefono` varchar(30) COLLATE utf8_spanish_ci DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `eliminado` tinyint(1) NOT NULL DEFAULT 0,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_mesoneros_activo` (`activo`),
  KEY `idx_mesoneros_eliminado` (`eliminado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mesoneros`
--


--
-- Table structure for table `categorias`
--

DROP TABLE IF EXISTS `categorias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `categorias` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(60) COLLATE utf8_spanish_ci NOT NULL,
  `color` char(7) COLLATE utf8_spanish_ci NOT NULL DEFAULT '#6B7280',
  `orden` int(11) NOT NULL DEFAULT 0,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_categorias_nombre` (`nombre`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categorias`
--


--
-- Table structure for table `plato_categoria`
--

DROP TABLE IF EXISTS `plato_categoria`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plato_categoria` (
  `nombre_clave` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `id_categoria` int(11) NOT NULL,
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`nombre_clave`),
  KEY `idx_plato_categoria_categoria` (`id_categoria`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plato_categoria`
--


--
-- Table structure for table `plato_favorito`
--

DROP TABLE IF EXISTS `plato_favorito`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plato_favorito` (
  `nombre_clave` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`nombre_clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plato_favorito`
--


--
-- Table structure for table `restaurante_control`
--

DROP TABLE IF EXISTS `restaurante_control`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `restaurante_control` (
  `id` tinyint(4) NOT NULL,
  `estado` varchar(20) COLLATE utf8_spanish_ci NOT NULL,
  `operacion_id` varchar(100) COLLATE utf8_spanish_ci DEFAULT NULL,
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_restaurante_control_estado` CHECK (`estado` in ('OPERATIVO','MANTENIMIENTO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `restaurante_control`
--


--
-- Table structure for table `schema_migrations`
--

DROP TABLE IF EXISTS `schema_migrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `schema_migrations` (
  `version` int(11) NOT NULL,
  `nombre` varchar(160) NOT NULL,
  `checksum` varchar(80) NOT NULL,
  `estado` varchar(20) NOT NULL,
  `paso_actual` varchar(80) NOT NULL,
  `iniciada_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `aplicada_en` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-13 16:30:00

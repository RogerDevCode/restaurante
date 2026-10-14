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
-- Dumping data for table `config`
--

LOCK TABLES `config` WRITE;
/*!40000 ALTER TABLE `config` DISABLE KEYS */;
INSERT INTO `config` (`id`, `ruc`, `nombre`, `telefono`, `direccion`, `mensaje`, `tasa_dolar`, `iva_porcentaje`, `logo_path`, `cliente_predeterminado_nombre`, `cliente_predeterminado_documento`, `meses_retencion_pedidos`, `imprimir_logo_ticket`, `impresora_tickets`, `modo_salida_tickets`) VALUES (1,'J-12345678-0','Restaurante la Delicia','0414-1234567','Caracas - Venezuela','Gracias por su compra',36.5000,16.00,NULL,'Consumidor Final','V-00000000',24,1,'DEFAULT','TERMICA_DIRECTA');
/*!40000 ALTER TABLE `config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `detalle_pedidos`
--

DROP TABLE IF EXISTS `detalle_pedidos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `detalle_pedidos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `precio` decimal(10,2) NOT NULL,
  `cantidad` int(11) NOT NULL,
  `comentario` text COLLATE utf8_spanish_ci DEFAULT NULL,
  `id_pedido` int(11) NOT NULL,
  `aplica_iva` tinyint(1) NOT NULL DEFAULT 1,
  `nombre_clave` varchar(200) GENERATED ALWAYS AS (lower(trim(`nombre`))) STORED,
  PRIMARY KEY (`id`),
  KEY `id_pedido` (`id_pedido`),
  KEY `idx_detalle_pedidos_nombre` (`nombre`),
  KEY `idx_detalle_pedidos_nombre_clave` (`nombre_clave`),
  CONSTRAINT `chk_detalle_aplica_iva` CHECK (`aplica_iva` in (0,1)),
  CONSTRAINT `detalle_pedidos_ibfk_1` FOREIGN KEY (`id_pedido`) REFERENCES `pedidos` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `detalle_pedidos`
--

LOCK TABLES `detalle_pedidos` WRITE;
/*!40000 ALTER TABLE `detalle_pedidos` DISABLE KEYS */;
INSERT INTO `detalle_pedidos` (`id`, `nombre`, `precio`, `cantidad`, `comentario`, `id_pedido`, `aplica_iva`) VALUES (1,'CHAUFA',20.00,1,'',1,1),(2,'ARROZ CON POLLO',10.00,5,'ARTO MAYONESA',1,1),(3,'GASEOSA COCA COLA 1.5 LITROS',8.00,1,'',1,1),(4,'ARROZ CON POLLO',10.00,1,'',2,1),(5,'CHAUFA',20.00,1,'',2,1),(6,'GASEOSA COCA COLA 1.5 LITROS',8.00,1,'',3,1),(7,'CHAUFA',20.00,1,'',3,1),(8,'ARROZ CON POLLO',10.00,2,'',4,1);
/*!40000 ALTER TABLE `detalle_pedidos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pedidos`
--

DROP TABLE IF EXISTS `pedidos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pedidos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `id_sala` int(11) DEFAULT NULL,
  `num_mesa` int(11) DEFAULT NULL,
  `tipo_pedido` varchar(16) COLLATE utf8_spanish_ci NOT NULL DEFAULT 'MESA',
  `fecha` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `total` decimal(10,2) NOT NULL,
  `estado` enum('PENDIENTE','FINALIZADO','ANULADO') COLLATE utf8_spanish_ci NOT NULL DEFAULT 'PENDIENTE',
  `usuario` varchar(100) COLLATE utf8_spanish_ci NOT NULL,
  `id_sala_pendiente` int(11) GENERATED ALWAYS AS (CASE WHEN `estado` = 'PENDIENTE' THEN `id_sala` ELSE NULL END) STORED,
  `num_mesa_pendiente` int(11) GENERATED ALWAYS AS (CASE WHEN `estado` = 'PENDIENTE' THEN `num_mesa` ELSE NULL END) STORED,
  `subtotal` decimal(10,2) NOT NULL DEFAULT 0.00,
  `iva_porcentaje` decimal(5,2) NOT NULL DEFAULT 0.00,
  `iva_monto` decimal(10,2) NOT NULL DEFAULT 0.00,
  `tasa_cambio` decimal(12,4) DEFAULT NULL,
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
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_pedido_pagos_pedido` (`id_pedido`),
  KEY `idx_pedido_pagos_metodo_fecha` (`metodo_pago`,`creado_en`),
  CONSTRAINT `fk_pedido_pagos_pedido` FOREIGN KEY (`id_pedido`) REFERENCES `pedidos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pedidos`
--

LOCK TABLES `pedidos` WRITE;
/*!40000 ALTER TABLE `pedidos` DISABLE KEYS */;
INSERT INTO `pedidos` (`id`,`id_sala`,`num_mesa`,`fecha`,`subtotal`,`iva_porcentaje`,`iva_monto`,`total`,`estado`,`usuario`,`tasa_cambio`,`subtotal_bs`,`iva_bs`,`total_bs`,`cliente_nombre`,`cliente_documento`) VALUES (1,1,2,'2022-05-18 00:31:52',78.00,0.00,0.00,78.00,'FINALIZADO','ANGEL SIFUENTES',36.5000,2847.00,0.00,2847.00,'Consumidor Final','V-00000000'),(2,2,8,'2022-05-18 00:32:20',30.00,0.00,0.00,30.00,'PENDIENTE','ANGEL SIFUENTES',36.5000,1095.00,0.00,1095.00,'Consumidor Final','V-00000000'),(3,1,9,'2022-05-18 00:32:29',28.00,0.00,0.00,28.00,'PENDIENTE','ANGEL SIFUENTES',36.5000,1022.00,0.00,1022.00,'Consumidor Final','V-00000000'),(4,1,11,'2022-05-18 01:04:47',20.00,0.00,0.00,20.00,'PENDIENTE','ANGEL SIFUENTES',36.5000,730.00,0.00,730.00,'Consumidor Final','V-00000000');
/*!40000 ALTER TABLE `pedidos` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `clientes` WRITE;
/*!40000 ALTER TABLE `clientes` DISABLE KEYS */;
INSERT INTO `clientes` (`id`, `documento`, `nombre`, `telefono`, `direccion`, `creado_en`) VALUES (1,'V-00000000','Consumidor Final',NULL,NULL,'2026-10-06 00:00:00');
/*!40000 ALTER TABLE `clientes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `platos`
--

DROP TABLE IF EXISTS `platos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `platos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(200) COLLATE utf8_spanish_ci NOT NULL,
  `precio` decimal(10,2) NOT NULL,
  `fecha` date DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `desactivado_en` datetime DEFAULT NULL,
  `aplica_iva` tinyint(1) NOT NULL DEFAULT 1,
  `nombre_clave` varchar(200) GENERATED ALWAYS AS (lower(trim(`nombre`))) STORED,
  PRIMARY KEY (`id`),
  KEY `idx_platos_nombre_clave` (`nombre_clave`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `platos`
--

LOCK TABLES `platos` WRITE;
/*!40000 ALTER TABLE `platos` DISABLE KEYS */;
INSERT INTO `platos` (`id`, `nombre`, `precio`, `fecha`, `activo`, `desactivado_en`, `aplica_iva`) VALUES (1,'ARROZ CON POLLO',10.00,CURRENT_DATE(),1,NULL,1),(2,'CHAUFA',20.00,CURRENT_DATE(),1,NULL,1),(3,'GASEOSA COCA COLA 1.5 LITROS',8.00,CURRENT_DATE(),1,NULL,1);
/*!40000 ALTER TABLE `platos` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `salas`
--

LOCK TABLES `salas` WRITE;
/*!40000 ALTER TABLE `salas` DISABLE KEYS */;
INSERT INTO `salas` (`id`, `nombre`, `mesas`, `tipo`) VALUES (1,'SALA PRINCIPAL',15,'SALON'),(2,'SEGUNDO PISO',10,'SALON');
/*!40000 ALTER TABLE `salas` ENABLE KEYS */;
UNLOCK TABLES;

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
  `nombre_unico` varchar(200) GENERATED ALWAYS AS (lower(trim(`nombre`))) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_usuarios_correo` (`correo`),
  UNIQUE KEY `uq_usuarios_nombre` (`nombre_unico`),
  CONSTRAINT `chk_usuarios_nombre_formato` CHECK ((char_length(`nombre`) between 3 and 60) and regexp_like(`nombre`, '^[[:alnum:]][[:alnum:] ._-]{1,58}[[:alnum:]]$'))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `usuarios`
--

LOCK TABLES `usuarios` WRITE;
/*!40000 ALTER TABLE `usuarios` DISABLE KEYS */;
INSERT INTO `usuarios` (`id`, `nombre`, `correo`, `pass`, `rol`) VALUES (1,'Administrador','admin','pbkdf2-sha256$600000$4rRaMK7S7lSdpTKgN6YRiA$O4BlX32Y2+lu3IjnBmn08WaE3waaVtdvv7xiuszXxNM','Administrador'),(2,'ANGEL SIFUENTES','info@angelsifuentes.com','pbkdf2-sha256$600000$4rRaMK7S7lSdpTKgN6YRiA$O4BlX32Y2+lu3IjnBmn08WaE3waaVtdvv7xiuszXxNM','Administrador');
/*!40000 ALTER TABLE `usuarios` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `configuracion_sistema` WRITE;
/*!40000 ALTER TABLE `configuracion_sistema` DISABLE KEYS */;
INSERT INTO `configuracion_sistema` (`clave`, `valor`, `descripcion`) VALUES
('tasa_dolar', '36.5000', 'Tasa de cambio USD / Bs'),
('iva_porcentaje', '16.00', 'Porcentaje de IVA'),
('impresora_tickets', 'DEFAULT', 'Impresora predeterminada para tickets'),
('modo_salida_tickets', 'TERMICA_DIRECTA', 'Modo de salida de tickets'),
('imprimir_logo_ticket', 'true', 'Imprimir logo en ticket térmico 80mm'),
('cliente_predeterminado_nombre', 'Consumidor Final', 'Nombre de cliente por defecto en facturación'),
('cliente_predeterminado_documento', 'V-00000000', 'Cédula/RIF de cliente por defecto en facturación'),
('meses_retencion_pedidos', '24', 'Meses de retención de pedidos');
/*!40000 ALTER TABLE `configuracion_sistema` ENABLE KEYS */;
UNLOCK TABLES;

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
  PRIMARY KEY (`id`),
  KEY `idx_cierres_fecha` (`fecha_jornada`),
  KEY `idx_cierres_tipo` (`tipo`)
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
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_categorias_nombre` (`nombre`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categorias`
--

LOCK TABLES `categorias` WRITE;
/*!40000 ALTER TABLE `categorias` DISABLE KEYS */;
INSERT INTO `categorias` (`id`, `nombre`, `color`, `orden`) VALUES (1,'General','#6B7280',0);
/*!40000 ALTER TABLE `categorias` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `restaurante_control` WRITE;
/*!40000 ALTER TABLE `restaurante_control` DISABLE KEYS */;
INSERT INTO `restaurante_control` (`id`, `estado`) VALUES (1,'OPERATIVO');
/*!40000 ALTER TABLE `restaurante_control` ENABLE KEYS */;
UNLOCK TABLES;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-13 16:30:00

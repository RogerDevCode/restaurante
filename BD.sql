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

LOCK TABLES `pedidos` WRITE;
/*!40000 ALTER TABLE `pedidos` DISABLE KEYS */;
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

LOCK TABLES `platos` WRITE;
/*!40000 ALTER TABLE `platos` DISABLE KEYS */;
INSERT INTO `platos` (`id`, `nombre`, `precio`, `moneda_base`, `precio_usd`, `precio_ves`, `precio_clasificado`, `precio_incluye_iva`, `fecha`, `activo`, `desactivado_en`, `aplica_iva`) VALUES 
(1,'POLLO ASADO',45.00,'USD',45.00,45630.00,1,0,CURRENT_DATE(),1,NULL,1),
(2,'LOMO SALTADO',32.50,'USD',32.50,32955.00,1,0,CURRENT_DATE(),1,NULL,1),
(3,'PARRILLA MIXTA',55.00,'USD',55.00,55770.00,1,0,CURRENT_DATE(),1,NULL,1),
(4,'ARROZ CON POLLO',10.00,'USD',10.00,10140.00,1,0,CURRENT_DATE(),1,NULL,1),
(5,'CHAUFA',20.00,'USD',20.00,20280.00,1,0,CURRENT_DATE(),1,NULL,1),
(6,'PABELLON CRIOLLO',16.00,'USD',16.00,16224.00,1,0,CURRENT_DATE(),1,NULL,1),
(7,'AREPA REINA PEPIADA',12.00,'USD',12.00,12168.00,1,0,CURRENT_DATE(),1,NULL,1),
(8,'CACHAPA CON QUESO',15.00,'USD',15.00,15210.00,1,0,CURRENT_DATE(),1,NULL,1),
(9,'EMPANADAS (3 UND)',9.00,'USD',9.00,9126.00,1,0,CURRENT_DATE(),1,NULL,1),
(10,'ENSALADA CESAR',14.00,'USD',14.00,14196.00,1,0,CURRENT_DATE(),1,NULL,0),
(11,'SOPA DEL DIA',8.00,'USD',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,0),
(12,'GASEOSA COCA COLA 1.5 LITROS',8.00,'USD',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,1),
(13,'CERVEZA',8.00,'USD',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,1),
(14,'CAFE NEGRO',3.00,'USD',3.00,3042.00,1,0,CURRENT_DATE(),1,NULL,0),
(15,'CAFE CON LECHE',5.00,'USD',5.00,5070.00,1,0,CURRENT_DATE(),1,NULL,0),
(16,'JUGO DE NARANJA',6.00,'USD',6.00,6084.00,1,0,CURRENT_DATE(),1,NULL,0),
(17,'JUGO DE MARACUYA',8.00,'USD',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,0),
(18,'PAPELON CON LIMON',7.00,'USD',7.00,7098.00,1,0,CURRENT_DATE(),1,NULL,0),
(19,'TORTA DE CHOCOLATE',10.00,'USD',10.00,10140.00,1,0,CURRENT_DATE(),1,NULL,1),
(20,'QUESILLO',8.00,'USD',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,1),
(21,'Pizza Margarita',3.50,'USD',3.50,3549.00,1,0,CURRENT_DATE(),1,NULL,1),
(22,'Pizza Pepperoni',4.99,'USD',4.99,5059.86,1,1,CURRENT_DATE(),1,NULL,1),
(23,'Pizza Cuatro Quesos',6844.50,'VES',6.75,6844.50,1,0,CURRENT_DATE(),1,NULL,1),
(24,'Pizza Vegetariana',5.25,'USD',5.25,5323.50,0,NULL,CURRENT_DATE(),1,NULL,1),
(25,'Pizza Prosciutto e Funghi',7605.00,'VES',7.50,7605.00,1,1,CURRENT_DATE(),1,NULL,1),
(26,'Espagueti a la Bolonesa',6.00,'USD',6.00,6084.00,1,0,CURRENT_DATE(),1,NULL,0),
(27,'Fettuccine Alfredo',6591.00,'VES',6.50,6591.00,1,0,CURRENT_DATE(),1,NULL,0),
(28,'Lasagna de Carne',8.25,'USD',8.25,8365.50,1,0,CURRENT_DATE(),1,NULL,1),
(29,'Penne Arrabbiata',5.75,'USD',5.75,5830.50,1,1,CURRENT_DATE(),1,NULL,1),
(30,'Ravioles de Ricotta',7098.00,'VES',7.00,7098.00,1,0,CURRENT_DATE(),1,NULL,1),
(31,'Hamburguesa Clasica',5577.00,'VES',5.50,5577.00,1,1,CURRENT_DATE(),1,NULL,1),
(32,'Hamburguesa Doble Queso',7.25,'USD',7.25,7351.50,1,0,CURRENT_DATE(),1,NULL,0),
(33,'Hamburguesa BBQ Bacon',8112.00,'VES',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,0),
(34,'Hamburguesa de Pollo Crispy',6.25,'USD',6.25,6337.50,1,0,CURRENT_DATE(),1,NULL,1),
(35,'Hamburguesa Vegana de Lentejas',6.75,'USD',6.75,6844.50,1,1,CURRENT_DATE(),1,NULL,1),
(36,'Filete de Salmon a la Plancha',12675.00,'VES',12.50,12675.00,0,NULL,CURRENT_DATE(),1,NULL,1),
(37,'Camarones al Ajillo',11154.00,'VES',11.00,11154.00,1,0,CURRENT_DATE(),1,NULL,1),
(38,'Pargo Frito Entero',14196.00,'VES',14.00,14196.00,1,1,CURRENT_DATE(),1,NULL,1),
(39,'Ceviche de Camaron',9.50,'USD',9.50,9633.00,1,0,CURRENT_DATE(),1,NULL,0),
(40,'Pulpo a la Gallega',13942.50,'VES',13.75,13942.50,1,0,CURRENT_DATE(),1,NULL,0),
(41,'Crema de Auyama',4.25,'USD',4.25,4309.50,1,0,CURRENT_DATE(),1,NULL,1),
(42,'Sopa de Pollo con Fideos',4.00,'USD',4.00,4056.00,1,1,CURRENT_DATE(),1,NULL,1),
(43,'Crema de Champinones',4816.50,'VES',4.75,4816.50,1,0,CURRENT_DATE(),1,NULL,1),
(44,'Sopa de Lentejas',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(45,'Caldo de Res',5.00,'USD',5.00,5070.00,1,0,CURRENT_DATE(),1,NULL,0),
(46,'Ensalada Cesar con Pollo',6591.00,'VES',6.50,6591.00,1,0,CURRENT_DATE(),1,NULL,0),
(47,'Ensalada Caprese',5.75,'USD',5.75,5830.50,0,NULL,CURRENT_DATE(),1,NULL,0),
(48,'Ensalada de Quinoa',6.25,'USD',6.25,6337.50,1,0,CURRENT_DATE(),1,NULL,1),
(49,'Ensalada Griega',6.00,'USD',6.00,6084.00,1,1,CURRENT_DATE(),1,NULL,1),
(50,'Ensalada de Atun',5577.00,'VES',5.50,5577.00,1,0,CURRENT_DATE(),1,NULL,1),
(51,'Desayuno Criollo',5323.50,'VES',5.25,5323.50,1,1,CURRENT_DATE(),1,NULL,1),
(52,'Huevos Rancheros',4.75,'USD',4.75,4816.50,1,0,CURRENT_DATE(),1,NULL,0),
(53,'Pancakes con Miel',4563.00,'VES',4.50,4563.00,1,0,CURRENT_DATE(),1,NULL,0),
(54,'Arepa con Perico',3.50,'USD',3.50,3549.00,1,0,CURRENT_DATE(),1,NULL,1),
(55,'Tostadas Francesas',4.25,'USD',4.25,4309.50,1,1,CURRENT_DATE(),1,NULL,1),
(56,'Croissant de Mantequilla',2788.50,'VES',2.75,2788.50,1,0,CURRENT_DATE(),1,NULL,1),
(57,'Torta Tres Leches',4563.00,'VES',4.50,4563.00,1,1,CURRENT_DATE(),1,NULL,1),
(58,'Cheesecake de Frutos Rojos',4.75,'USD',4.75,4816.50,1,0,CURRENT_DATE(),1,NULL,0),
(59,'Brownie con Helado',4056.00,'VES',4.00,4056.00,1,0,CURRENT_DATE(),1,NULL,0),
(60,'Milhojas de Crema',3.75,'USD',3.75,3802.50,1,0,CURRENT_DATE(),1,NULL,1),
(61,'Capuchino',2535.00,'VES',2.50,2535.00,0,NULL,CURRENT_DATE(),1,NULL,0),
(62,'Latte Vainilla',3.00,'USD',3.00,3042.00,1,1,CURRENT_DATE(),1,NULL,1),
(63,'Espresso Doble',2281.50,'VES',2.25,2281.50,1,0,CURRENT_DATE(),1,NULL,1),
(64,'Mocha Frappe',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(65,'Te Chai Latte',3.25,'USD',3.25,3295.50,1,0,CURRENT_DATE(),1,NULL,0),
(66,'Mojito Sin Alcohol',3549.00,'VES',3.50,3549.00,1,0,CURRENT_DATE(),1,NULL,0),
(67,'Pina Colada',4.25,'USD',4.25,4309.50,1,0,CURRENT_DATE(),1,NULL,1),
(68,'Daiquiri de Fresa',4.00,'USD',4.00,4056.00,1,1,CURRENT_DATE(),1,NULL,1),
(69,'Limonada Frozen',3042.00,'VES',3.00,3042.00,1,0,CURRENT_DATE(),1,NULL,1),
(70,'Sangria Sin Alcohol',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(71,'Copa de Vino Tinto',5.00,'USD',5.00,5070.00,1,0,CURRENT_DATE(),1,NULL,0),
(72,'Copa de Vino Blanco',5070.00,'VES',5.00,5070.00,1,0,CURRENT_DATE(),1,NULL,0),
(73,'Cerveza Artesanal IPA',4.50,'USD',4.50,4563.00,1,0,CURRENT_DATE(),1,NULL,1),
(74,'Whisky Etiqueta Negra (shot)',6.50,'USD',6.50,6591.00,1,1,CURRENT_DATE(),1,NULL,1),
(75,'Ron Aniejo (shot)',5577.00,'VES',5.50,5577.00,1,0,CURRENT_DATE(),1,NULL,1),
(76,'Bowl Vegano de Quinoa',7.50,'USD',7.50,7605.00,0,NULL,CURRENT_DATE(),1,NULL,1),
(77,'Curry de Garbanzos',6844.50,'VES',6.75,6844.50,1,1,CURRENT_DATE(),1,NULL,1),
(78,'Tacos de Jackfruit',6.25,'USD',6.25,6337.50,1,0,CURRENT_DATE(),1,NULL,0),
(79,'Lasagna Vegana',8112.00,'VES',8.00,8112.00,1,0,CURRENT_DATE(),1,NULL,0),
(80,'Salteado de Tofu y Verduras',7.25,'USD',7.25,7351.50,1,0,CURRENT_DATE(),1,NULL,1),
(81,'Mini Hamburguesa con Papas',4.50,'USD',4.50,4563.00,1,1,CURRENT_DATE(),1,NULL,1),
(82,'Nuggets de Pollo',4056.00,'VES',4.00,4056.00,1,0,CURRENT_DATE(),1,NULL,1),
(83,'Dedos de Queso',3802.50,'VES',3.75,3802.50,1,1,CURRENT_DATE(),1,NULL,1),
(84,'Helado de Chocolate (Infantil)',2.50,'USD',2.50,2535.00,1,0,CURRENT_DATE(),1,NULL,0),
(85,'Roll California',6591.00,'VES',6.50,6591.00,1,0,CURRENT_DATE(),1,NULL,0),
(86,'Roll Filadelfia',7.00,'USD',7.00,7098.00,1,0,CURRENT_DATE(),1,NULL,1),
(87,'Nigiri de Salmon',5.50,'USD',5.50,5577.00,1,1,CURRENT_DATE(),1,NULL,1),
(88,'Sashimi de Atun',8619.00,'VES',8.50,8619.00,1,0,CURRENT_DATE(),1,NULL,1),
(89,'Roll Vegetariano',5830.50,'VES',5.75,5830.50,0,NULL,CURRENT_DATE(),1,NULL,0),
(90,'Gyoza de Cerdo',5323.50,'VES',5.25,5323.50,1,1,CURRENT_DATE(),1,NULL,1);
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `salas`
--

LOCK TABLES `salas` WRITE;
/*!40000 ALTER TABLE `salas` DISABLE KEYS */;
INSERT INTO `salas` (`id`, `nombre`, `mesas`, `tipo`) VALUES 
(1,'SALON PRINCIPAL',15,'SALON'),
(2,'TERRAZA',10,'SALON'),
(3,'SALON VIP',6,'SALON'),
(4,'BARRA',8,'BARRA');
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

LOCK TABLES `config` WRITE;
/*!40000 ALTER TABLE `config` DISABLE KEYS */;
INSERT INTO `config` (`id`, `ruc`, `nombre`, `telefono`, `direccion`, `mensaje`, `tasa_dolar`, `iva_porcentaje`, `logo_path`, `cliente_predeterminado_nombre`, `cliente_predeterminado_documento`, `meses_retencion_pedidos`, `imprimir_logo_ticket`, `impresora_tickets`, `modo_salida_tickets`) VALUES (1,'J-12345678-0','Restaurante la Delicia','0414-1234567','Caracas - Venezuela','Gracias por su compra',1014.0000,16.00,NULL,'Consumidor Final','V-00000000',24,1,'DEFAULT','TERMICA_DIRECTA');
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

-- Dumping data for table `detalle_pedidos`
LOCK TABLES `detalle_pedidos` WRITE;
/*!40000 ALTER TABLE `detalle_pedidos` DISABLE KEYS */;
/*!40000 ALTER TABLE `detalle_pedidos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `usuarios`
--

LOCK TABLES `usuarios` WRITE;
/*!40000 ALTER TABLE `usuarios` DISABLE KEYS */;
INSERT INTO `usuarios` (`id`, `nombre`, `correo`, `pass`, `rol`, `activo`) VALUES (1,'Administrador','admin','pbkdf2-sha256$600000$4rRaMK7S7lSdpTKgN6YRiA$O4BlX32Y2+lu3IjnBmn08WaE3waaVtdvv7xiuszXxNM','Administrador',1);
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
('tasa_dolar', '1014.00', 'Tasa de cambio USD / Bs'),
('iva_porcentaje', '16.00', 'Porcentaje de IVA'),
('impresora_tickets', 'DEFAULT', 'Impresora predeterminada para tickets'),
('modo_salida_tickets', 'TERMICA_DIRECTA', 'Modo de salida de tickets'),
('imprimir_logo_ticket', 'true', 'Imprimir logo en ticket térmico 80mm'),
('cliente_predeterminado_nombre', 'Consumidor Final', 'Nombre de cliente por defecto en facturación'),
('cliente_predeterminado_documento', 'V-00000000', 'Cédula/RIF de cliente por defecto en facturación'),
('meses_retencion_pedidos', '24', 'Meses de retención de pedidos'),
('modo_atencion', 'OPCION_C', 'Modo predeterminado de atención de mesas');
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

LOCK TABLES `mesoneros` WRITE;
/*!40000 ALTER TABLE `mesoneros` DISABLE KEYS */;
INSERT INTO `mesoneros` (`id`, `nombre_completo`, `cedula`, `telefono`, `activo`, `eliminado`) VALUES 
(1,'Juan Pérez','V-20111222','0414-1112233',1,0),
(2,'Luis García','V-21555666','0412-7778899',1,0),
(3,'Pedro Sánchez','V-18777888','0424-5556677',1,0),
(4,'Ana Martínez','V-18987654','0424-4445566',1,0),
(5,'Carmen Ruiz','V-17666777','0416-0001122',1,0),
(6,'Lucía Fernández','V-19888777','0414-3334455',1,0);
/*!40000 ALTER TABLE `mesoneros` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categorias`
--

LOCK TABLES `categorias` WRITE;
/*!40000 ALTER TABLE `categorias` DISABLE KEYS */;
INSERT INTO `categorias` (`id`, `nombre`, `color`, `orden`) VALUES 
(1,'Entradas','#E9B10C',1),
(2,'Platos Fuertes','#E56F1A',2),
(3,'Parrilla','#E45A4E',3),
(4,'Bebidas','#558EDD',4),
(5,'Jugos Naturales','#2E9E4F',5),
(6,'Postres','#A270DB',6),
(7,'General','#6B7280',7),
(8,'Pizzas','#C0392B',1),(9,'Pastas','#E67E22',2),(10,'Hamburguesas','#8E44AD',3),(11,'Pescados y Mariscos','#1ABC9C',4),(12,'Sopas y Cremas','#F1C40F',5),(13,'Ensaladas','#27AE60',6),(14,'Desayunos','#D35400',7),(15,'Panaderia y Reposteria','#B9770E',8),(16,'Cafeteria','#6E2C00',9),(17,'Cocteles y Mocktails','#7D3C98',10),(18,'Vinos y Licores','#922B21',11),(19,'Comida Vegana','#229954',12),(20,'Menu Infantil','#2E86C1',13),(21,'Sushi y Rolls','#884EA0',14);
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
-- Dumping data for table `plato_categoria`
--

LOCK TABLES `plato_categoria` WRITE;
/*!40000 ALTER TABLE `plato_categoria` DISABLE KEYS */;
INSERT INTO `plato_categoria` (`nombre_clave`, `id_categoria`) VALUES 
('pollo asado',3),('lomo saltado',3),('parrilla mixta',3),
('arroz con pollo',2),('chaufa',2),('pabellon criollo',2),
('arepa reina pepiada',1),('cachapa con queso',1),('empanadas (3 und)',1),('ensalada cesar',1),('sopa del dia',1),
('gaseosa coca cola 1.5 litros',4),('cerveza',4),('cafe negro',4),('cafe con leche',4),
('jugo de naranja',5),('jugo de maracuya',5),('papelon con limon',5),
('torta de chocolate',6),('quesillo',6),
('pizza margarita',8),
('pizza pepperoni',8),
('pizza cuatro quesos',8),
('pizza vegetariana',8),
('pizza prosciutto e funghi',8),
('espagueti a la bolonesa',9),
('fettuccine alfredo',9),
('lasagna de carne',9),
('penne arrabbiata',9),
('ravioles de ricotta',9),
('hamburguesa clasica',10),
('hamburguesa doble queso',10),
('hamburguesa bbq bacon',10),
('hamburguesa de pollo crispy',10),
('hamburguesa vegana de lentejas',10),
('filete de salmon a la plancha',11),
('camarones al ajillo',11),
('pargo frito entero',11),
('ceviche de camaron',11),
('pulpo a la gallega',11),
('crema de auyama',12),
('sopa de pollo con fideos',12),
('crema de champinones',12),
('sopa de lentejas',12),
('caldo de res',12),
('ensalada cesar con pollo',13),
('ensalada caprese',13),
('ensalada de quinoa',13),
('ensalada griega',13),
('ensalada de atun',13),
('desayuno criollo',14),
('huevos rancheros',14),
('pancakes con miel',14),
('arepa con perico',14),
('tostadas francesas',14),
('croissant de mantequilla',15),
('torta tres leches',15),
('cheesecake de frutos rojos',15),
('brownie con helado',15),
('milhojas de crema',15),
('capuchino',16),
('latte vainilla',16),
('espresso doble',16),
('mocha frappe',16),
('te chai latte',16),
('mojito sin alcohol',17),
('pina colada',17),
('daiquiri de fresa',17),
('limonada frozen',17),
('sangria sin alcohol',17),
('copa de vino tinto',18),
('copa de vino blanco',18),
('cerveza artesanal ipa',18),
('whisky etiqueta negra (shot)',18),
('ron aniejo (shot)',18),
('bowl vegano de quinoa',19),
('curry de garbanzos',19),
('tacos de jackfruit',19),
('lasagna vegana',19),
('salteado de tofu y verduras',19),
('mini hamburguesa con papas',20),
('nuggets de pollo',20),
('dedos de queso',20),
('helado de chocolate (infantil)',20),
('roll california',21),
('roll filadelfia',21),
('nigiri de salmon',21),
('sashimi de atun',21),
('roll vegetariano',21),
('gyoza de cerdo',21);
/*!40000 ALTER TABLE `plato_categoria` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `plato_favorito` WRITE;
/*!40000 ALTER TABLE `plato_favorito` DISABLE KEYS */;
INSERT INTO `plato_favorito` (`nombre_clave`) VALUES 
('pollo asado'),('parrilla mixta'),('arepa reina pepiada'),('cafe con leche'),('torta de chocolate');
/*!40000 ALTER TABLE `plato_favorito` ENABLE KEYS */;
UNLOCK TABLES;

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

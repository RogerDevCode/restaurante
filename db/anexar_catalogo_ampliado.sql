-- ============================================================================
-- ANEXAR CATALOGO AMPLIADO DE FORMA SEGURA (OPCION B: SIN DESTRUIR DATOS)
-- ============================================================================
START TRANSACTION;

-- 1. Desactivar plato residual de test si existiera
UPDATE platos SET activo = 0, desactivado_en = NOW() WHERE id = 106 AND activo = 1;

-- 2. Insertar Categorias nuevas sin pisar IDs existentes
INSERT INTO categorias (nombre, color, orden) VALUES
('Pizzas', '#C0392B', 14),
('Pastas', '#E67E22', 15),
('Hamburguesas', '#8E44AD', 16),
('Pescados y Mariscos', '#1ABC9C', 17),
('Sopas y Cremas', '#F1C40F', 18),
('Ensaladas', '#27AE60', 19),
('Desayunos', '#D35400', 20),
('Panaderia y Reposteria', '#B9770E', 21),
('Cafeteria', '#6E2C00', 22),
('Cocteles y Mocktails', '#7D3C98', 23),
('Vinos y Licores', '#922B21', 24),
('Comida Vegana', '#229954', 25),
('Menu Infantil', '#2E86C1', 26),
('Sushi y Rolls', '#884EA0', 27)
ON DUPLICATE KEY UPDATE color = VALUES(color);

-- 3. Insertar Platos usando AUTO_INCREMENT (omitiendo id fijo) para evitar colisiones
-- Solo inserta los platos que no existan activos previamente en la tabla
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pizza Margarita', 3.50, 'USD', 3.50, 3549.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pizza Margarita')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pizza Pepperoni', 4.99, 'USD', 4.99, 5059.86, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pizza Pepperoni')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pizza Cuatro Quesos', 6844.50, 'VES', 6.75, 6844.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pizza Cuatro Quesos')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pizza Vegetariana', 5.25, 'USD', 5.25, 5323.50, 0, NULL, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pizza Vegetariana')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pizza Prosciutto e Funghi', 7605.00, 'VES', 7.50, 7605.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pizza Prosciutto e Funghi')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Espagueti a la Bolonesa', 6.00, 'USD', 6.00, 6084.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Espagueti a la Bolonesa')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Fettuccine Alfredo', 6591.00, 'VES', 6.50, 6591.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Fettuccine Alfredo')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Lasagna de Carne', 8.25, 'USD', 8.25, 8365.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Lasagna de Carne')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Penne Arrabbiata', 5.75, 'USD', 5.75, 5830.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Penne Arrabbiata')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ravioles de Ricotta', 7098.00, 'VES', 7.00, 7098.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ravioles de Ricotta')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Hamburguesa Clasica', 5577.00, 'VES', 5.50, 5577.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Hamburguesa Clasica')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Hamburguesa Doble Queso', 7.25, 'USD', 7.25, 7351.50, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Hamburguesa Doble Queso')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Hamburguesa BBQ Bacon', 8112.00, 'VES', 8.00, 8112.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Hamburguesa BBQ Bacon')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Hamburguesa de Pollo Crispy', 6.25, 'USD', 6.25, 6337.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Hamburguesa de Pollo Crispy')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Hamburguesa Vegana de Lentejas', 6.75, 'USD', 6.75, 6844.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Hamburguesa Vegana de Lentejas')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Filete de Salmon a la Plancha', 12675.00, 'VES', 12.50, 12675.00, 0, NULL, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Filete de Salmon a la Plancha')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Camarones al Ajillo', 11154.00, 'VES', 11.00, 11154.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Camarones al Ajillo')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pargo Frito Entero', 14196.00, 'VES', 14.00, 14196.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pargo Frito Entero')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ceviche de Camaron', 9.50, 'USD', 9.50, 9633.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ceviche de Camaron')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pulpo a la Gallega', 13942.50, 'VES', 13.75, 13942.50, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pulpo a la Gallega')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Crema de Auyama', 4.25, 'USD', 4.25, 4309.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Crema de Auyama')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Sopa de Pollo con Fideos', 4.00, 'USD', 4.00, 4056.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Sopa de Pollo con Fideos')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Crema de Champinones', 4816.50, 'VES', 4.75, 4816.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Crema de Champinones')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Sopa de Lentejas', 3802.50, 'VES', 3.75, 3802.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Sopa de Lentejas')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Caldo de Res', 5.00, 'USD', 5.00, 5070.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Caldo de Res')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ensalada Cesar con Pollo', 6591.00, 'VES', 6.50, 6591.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ensalada Cesar con Pollo')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ensalada Caprese', 5.75, 'USD', 5.75, 5830.50, 0, NULL, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ensalada Caprese')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ensalada de Quinoa', 6.25, 'USD', 6.25, 6337.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ensalada de Quinoa')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ensalada Griega', 6.00, 'USD', 6.00, 6084.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ensalada Griega')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ensalada de Atun', 5577.00, 'VES', 5.50, 5577.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ensalada de Atun')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Desayuno Criollo', 5323.50, 'VES', 5.25, 5323.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Desayuno Criollo')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Huevos Rancheros', 4.75, 'USD', 4.75, 4816.50, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Huevos Rancheros')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pancakes con Miel', 4563.00, 'VES', 4.50, 4563.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pancakes con Miel')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Arepa con Perico', 3.50, 'USD', 3.50, 3549.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Arepa con Perico')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Tostadas Francesas', 4.25, 'USD', 4.25, 4309.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Tostadas Francesas')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Croissant de Mantequilla', 2788.50, 'VES', 2.75, 2788.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Croissant de Mantequilla')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Torta Tres Leches', 4563.00, 'VES', 4.50, 4563.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Torta Tres Leches')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Cheesecake de Frutos Rojos', 4.75, 'USD', 4.75, 4816.50, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Cheesecake de Frutos Rojos')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Brownie con Helado', 4056.00, 'VES', 4.00, 4056.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Brownie con Helado')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Milhojas de Crema', 3.75, 'USD', 3.75, 3802.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Milhojas de Crema')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Capuchino', 2535.00, 'VES', 2.50, 2535.00, 0, NULL, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Capuchino')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Latte Vainilla', 3.00, 'USD', 3.00, 3042.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Latte Vainilla')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Espresso Doble', 2281.50, 'VES', 2.25, 2281.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Espresso Doble')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Mocha Frappe', 3802.50, 'VES', 3.75, 3802.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Mocha Frappe')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Te Chai Latte', 3.25, 'USD', 3.25, 3295.50, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Te Chai Latte')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Mojito Sin Alcohol', 3549.00, 'VES', 3.50, 3549.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Mojito Sin Alcohol')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Pina Colada', 4.25, 'USD', 4.25, 4309.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Pina Colada')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Daiquiri de Fresa', 4.00, 'USD', 4.00, 4056.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Daiquiri de Fresa')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Limonada Frozen', 3042.00, 'VES', 3.00, 3042.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Limonada Frozen')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Sangria Sin Alcohol', 3802.50, 'VES', 3.75, 3802.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Sangria Sin Alcohol')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Copa de Vino Tinto', 5.00, 'USD', 5.00, 5070.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Copa de Vino Tinto')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Copa de Vino Blanco', 5070.00, 'VES', 5.00, 5070.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Copa de Vino Blanco')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Cerveza Artesanal IPA', 4.50, 'USD', 4.50, 4563.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Cerveza Artesanal IPA')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Whisky Etiqueta Negra (shot)', 6.50, 'USD', 6.50, 6591.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Whisky Etiqueta Negra (shot)')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Ron Aniejo (shot)', 5577.00, 'VES', 5.50, 5577.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Ron Aniejo (shot)')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Bowl Vegano de Quinoa', 7.50, 'USD', 7.50, 7605.00, 0, NULL, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Bowl Vegano de Quinoa')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Curry de Garbanzos', 6844.50, 'VES', 6.75, 6844.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Curry de Garbanzos')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Tacos de Jackfruit', 6.25, 'USD', 6.25, 6337.50, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Tacos de Jackfruit')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Lasagna Vegana', 8112.00, 'VES', 8.00, 8112.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Lasagna Vegana')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Salteado de Tofu y Verduras', 7.25, 'USD', 7.25, 7351.50, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Salteado de Tofu y Verduras')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Mini Hamburguesa con Papas', 4.50, 'USD', 4.50, 4563.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Mini Hamburguesa con Papas')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Nuggets de Pollo', 4056.00, 'VES', 4.00, 4056.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Nuggets de Pollo')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Dedos de Queso', 3802.50, 'VES', 3.75, 3802.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Dedos de Queso')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Helado de Chocolate (Infantil)', 2.50, 'USD', 2.50, 2535.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Helado de Chocolate (Infantil)')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Roll California', 6591.00, 'VES', 6.50, 6591.00, 1, 0, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Roll California')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Roll Filadelfia', 7.00, 'USD', 7.00, 7098.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Roll Filadelfia')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Nigiri de Salmon', 5.50, 'USD', 5.50, 5577.00, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Nigiri de Salmon')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Sashimi de Atun', 8619.00, 'VES', 8.50, 8619.00, 1, 0, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Sashimi de Atun')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Roll Vegetariano', 5830.50, 'VES', 5.75, 5830.50, 0, NULL, CURRENT_DATE(), 1, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Roll Vegetariano')) AND activo = 1
);
INSERT INTO platos (nombre, precio, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva, fecha, activo, desactivado_en, aplica_iva)
SELECT 'Gyoza de Cerdo', 5323.50, 'VES', 5.25, 5323.50, 1, 1, CURRENT_DATE(), 1, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM platos WHERE nombre_clave = LOWER(TRIM('Gyoza de Cerdo')) AND activo = 1
);

-- 4. Vincular platos a sus categorias por nombre de categoria (no por id estatico)
INSERT INTO plato_categoria (nombre_clave, id_categoria) VALUES
('pizza margarita', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pizzas')) LIMIT 1)),
('pizza pepperoni', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pizzas')) LIMIT 1)),
('pizza cuatro quesos', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pizzas')) LIMIT 1)),
('pizza vegetariana', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pizzas')) LIMIT 1)),
('pizza prosciutto e funghi', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pizzas')) LIMIT 1)),
('espagueti a la bolonesa', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pastas')) LIMIT 1)),
('fettuccine alfredo', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pastas')) LIMIT 1)),
('lasagna de carne', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pastas')) LIMIT 1)),
('penne arrabbiata', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pastas')) LIMIT 1)),
('ravioles de ricotta', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pastas')) LIMIT 1)),
('hamburguesa clasica', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Hamburguesas')) LIMIT 1)),
('hamburguesa doble queso', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Hamburguesas')) LIMIT 1)),
('hamburguesa bbq bacon', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Hamburguesas')) LIMIT 1)),
('hamburguesa de pollo crispy', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Hamburguesas')) LIMIT 1)),
('hamburguesa vegana de lentejas', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Hamburguesas')) LIMIT 1)),
('filete de salmon a la plancha', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pescados y Mariscos')) LIMIT 1)),
('camarones al ajillo', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pescados y Mariscos')) LIMIT 1)),
('pargo frito entero', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pescados y Mariscos')) LIMIT 1)),
('ceviche de camaron', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pescados y Mariscos')) LIMIT 1)),
('pulpo a la gallega', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Pescados y Mariscos')) LIMIT 1)),
('crema de auyama', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sopas y Cremas')) LIMIT 1)),
('sopa de pollo con fideos', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sopas y Cremas')) LIMIT 1)),
('crema de champinones', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sopas y Cremas')) LIMIT 1)),
('sopa de lentejas', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sopas y Cremas')) LIMIT 1)),
('caldo de res', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sopas y Cremas')) LIMIT 1)),
('ensalada cesar con pollo', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Ensaladas')) LIMIT 1)),
('ensalada caprese', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Ensaladas')) LIMIT 1)),
('ensalada de quinoa', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Ensaladas')) LIMIT 1)),
('ensalada griega', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Ensaladas')) LIMIT 1)),
('ensalada de atun', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Ensaladas')) LIMIT 1)),
('desayuno criollo', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Desayunos')) LIMIT 1)),
('huevos rancheros', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Desayunos')) LIMIT 1)),
('pancakes con miel', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Desayunos')) LIMIT 1)),
('arepa con perico', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Desayunos')) LIMIT 1)),
('tostadas francesas', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Desayunos')) LIMIT 1)),
('croissant de mantequilla', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Panaderia y Reposteria')) LIMIT 1)),
('torta tres leches', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Panaderia y Reposteria')) LIMIT 1)),
('cheesecake de frutos rojos', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Panaderia y Reposteria')) LIMIT 1)),
('brownie con helado', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Panaderia y Reposteria')) LIMIT 1)),
('milhojas de crema', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Panaderia y Reposteria')) LIMIT 1)),
('capuchino', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cafeteria')) LIMIT 1)),
('latte vainilla', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cafeteria')) LIMIT 1)),
('espresso doble', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cafeteria')) LIMIT 1)),
('mocha frappe', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cafeteria')) LIMIT 1)),
('te chai latte', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cafeteria')) LIMIT 1)),
('mojito sin alcohol', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cocteles y Mocktails')) LIMIT 1)),
('pina colada', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cocteles y Mocktails')) LIMIT 1)),
('daiquiri de fresa', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cocteles y Mocktails')) LIMIT 1)),
('limonada frozen', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cocteles y Mocktails')) LIMIT 1)),
('sangria sin alcohol', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Cocteles y Mocktails')) LIMIT 1)),
('copa de vino tinto', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Vinos y Licores')) LIMIT 1)),
('copa de vino blanco', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Vinos y Licores')) LIMIT 1)),
('cerveza artesanal ipa', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Vinos y Licores')) LIMIT 1)),
('whisky etiqueta negra (shot)', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Vinos y Licores')) LIMIT 1)),
('ron aniejo (shot)', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Vinos y Licores')) LIMIT 1)),
('bowl vegano de quinoa', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Comida Vegana')) LIMIT 1)),
('curry de garbanzos', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Comida Vegana')) LIMIT 1)),
('tacos de jackfruit', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Comida Vegana')) LIMIT 1)),
('lasagna vegana', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Comida Vegana')) LIMIT 1)),
('salteado de tofu y verduras', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Comida Vegana')) LIMIT 1)),
('mini hamburguesa con papas', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Menu Infantil')) LIMIT 1)),
('nuggets de pollo', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Menu Infantil')) LIMIT 1)),
('dedos de queso', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Menu Infantil')) LIMIT 1)),
('helado de chocolate (infantil)', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Menu Infantil')) LIMIT 1)),
('roll california', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sushi y Rolls')) LIMIT 1)),
('roll filadelfia', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sushi y Rolls')) LIMIT 1)),
('nigiri de salmon', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sushi y Rolls')) LIMIT 1)),
('sashimi de atun', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sushi y Rolls')) LIMIT 1)),
('roll vegetariano', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sushi y Rolls')) LIMIT 1)),
('gyoza de cerdo', (SELECT id FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM('Sushi y Rolls')) LIMIT 1))
ON DUPLICATE KEY UPDATE id_categoria = VALUES(id_categoria);

COMMIT;
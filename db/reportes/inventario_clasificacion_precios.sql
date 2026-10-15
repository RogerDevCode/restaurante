-- Informe previo al corte dual USD/VES. Solo lectura; no asigna moneda ni modifica ventas.
-- Ejecútese en la copia de la BD que se usará para preparar la clasificación manual.

SELECT id AS producto_id,
       nombre,
       precio AS precio_heredado_sin_moneda_confirmada,
       aplica_iva,
       activo,
       precio_clasificado,
       moneda_base,
       precio_usd,
       precio_ves,
       precio_incluye_iva
FROM platos
WHERE precio_clasificado = 0
ORDER BY activo DESC, nombre, id;

SELECT d.id AS detalle_id,
       d.id_pedido,
       p.estado AS estado_pedido,
       p.cerrada_en,
       d.nombre AS nombre_historico,
       d.precio AS precio_historico,
       d.cantidad,
       d.aplica_iva,
       d.id_plato,
       d.moneda_base,
       d.precio_incluye_iva,
       d.iva_porcentaje
FROM detalle_pedidos d
JOIN pedidos p ON p.id = d.id_pedido
WHERE p.estado = 'PENDIENTE'
  AND p.cerrada_en IS NULL
  AND (d.id_plato IS NULL OR d.moneda_base IS NULL OR d.precio_incluye_iva IS NULL OR d.iva_porcentaje IS NULL)
ORDER BY p.id, d.id;

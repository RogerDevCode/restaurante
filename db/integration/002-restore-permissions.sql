-- Privilegios acotados al contenedor efímero de integración.
-- Permiten verificar backups en esquemas aleatorios y borrarlos después.
GRANT ALL PRIVILEGES ON *.* TO 'restaurante_test_app'@'%';

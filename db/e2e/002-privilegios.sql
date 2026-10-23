-- Privilegios minimos para el usuario de pruebas E2E (MySQL 8.4).
-- Se ejecuta en el entrypoint del contenedor E2E (como root) tras cargar
-- 001-esquema.sql. Permisos equivalentes al arnes de bootstrap:
-- SELECT/INSERT/UPDATE/DELETE para operar, CREATE/ALTER/INDEX/REFERENCES
-- para que MigradorEsquemaJdbc pueda ajustar columnas al arrancar.
DROP USER IF EXISTS 'restaurante_test_app'@'%';
CREATE USER 'restaurante_test_app'@'%' IDENTIFIED BY 'test_only_app_password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON restaurante_test.* TO 'restaurante_test_app'@'%';
FLUSH PRIVILEGES;

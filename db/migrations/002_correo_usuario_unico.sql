-- Garantiza que cada cuenta tenga un correo único (la collation ignora mayúsculas).
-- Antes de aplicar en un entorno con datos existentes, resolver cualquier duplicado.
ALTER TABLE usuarios
    ADD CONSTRAINT uq_usuarios_correo UNIQUE (correo);

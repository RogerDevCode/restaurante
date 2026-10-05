-- PBKDF2-HMAC-SHA-256 encodings are longer than legacy plaintext credentials.
ALTER TABLE usuarios
    MODIFY COLUMN pass VARCHAR(255) COLLATE utf8_spanish_ci NOT NULL;

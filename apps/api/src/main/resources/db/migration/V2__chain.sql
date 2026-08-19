-- Cada item pasa a ser un bloque: guarda su hash y el del bloque anterior.
-- Nullable a propósito: las filas que ya existían no son bloques y /chain/verify
-- las ignora, en vez de borrar datos para poder poner NOT NULL.
ALTER TABLE items
    ADD COLUMN prev_hash TEXT,
    ADD COLUMN hash      TEXT;

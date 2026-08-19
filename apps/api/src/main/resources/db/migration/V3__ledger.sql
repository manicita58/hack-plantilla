-- La cadena pasó a ser su propio módulo: la tabla deja de llamarse `items`
-- (nombre del CRUD de ejemplo) y pasa a nombrar lo que realmente guarda.
ALTER TABLE items RENAME TO ledger_entries;
ALTER TABLE ledger_entries RENAME COLUMN name TO content;

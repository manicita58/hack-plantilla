# Postgres 18 con las dos extensiones que usan los módulos:
#   - PostGIS 3.6  -> módulo geo (geometrías, índices GIST, consultas espaciales)
#   - pgvector     -> módulo ia  (embeddings y búsqueda por similitud del RAG)
# No existe imagen oficial con ambas, así que se le agrega pgvector a la de
# PostGIS desde el repo PGDG (que la imagen ya trae configurado).
FROM postgis/postgis:18-3.6

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-18-pgvector \
    && rm -rf /var/lib/apt/lists/*

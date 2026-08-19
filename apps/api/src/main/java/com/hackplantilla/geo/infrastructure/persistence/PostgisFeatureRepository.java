package com.hackplantilla.geo.infrastructure.persistence;

import com.hackplantilla.geo.GeoModule;
import com.hackplantilla.geo.domain.FeatureRepository;
import com.hackplantilla.geo.domain.GeoFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Adaptador PostGIS. Va con JdbcTemplate y no con JPA a propósito: las
 * geometrías con Hibernate piden hibernate-spatial, un dialecto propio y un tipo
 * JTS por columna, y acá con cuatro funciones SQL (ST_AsGeoJSON, ST_GeomFromGeoJSON,
 * ST_Intersects, ST_DWithin) se hace todo y se lee mejor.
 */
@Repository
@GeoModule
class PostgisFeatureRepository implements FeatureRepository {

    /** ST_AsGeoJSON devuelve la geometría ya lista para Leaflet. */
    private static final String SELECT = """
            SELECT id, name, category, ST_AsGeoJSON(geom) AS geometry,
                   properties::text AS properties, created_at
            FROM geo_features
            """;

    private static final RowMapper<GeoFeature> MAPPER = (rs, row) -> new GeoFeature(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("geometry"),
            rs.getString("properties"),
            rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
            hasColumn(rs, "distance_meters") ? rs.getDouble("distance_meters") : null);

    private final JdbcTemplate jdbc;

    PostgisFeatureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<GeoFeature> search(BoundingBox bbox, String category, int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();

        if (bbox != null) {
            // ST_MakeEnvelope + ST_Intersects usa el índice GIST; filtrar en Java
            // traería la tabla entera a la app para descartarla ahí.
            sql.append(" AND ST_Intersects(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))");
            args.add(bbox.minLon());
            args.add(bbox.minLat());
            args.add(bbox.maxLon());
            args.add(bbox.maxLat());
        }
        if (category != null) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(limit);

        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public List<GeoFeature> near(double lon, double lat, double meters, int limit) {
        // El cast a ::geography hace que la distancia sea en metros sobre el
        // elipsoide; en ::geometry serían grados, que no significan nada acá.
        return jdbc.query("""
                        SELECT id, name, category, ST_AsGeoJSON(geom) AS geometry,
                               properties::text AS properties, created_at,
                               ST_Distance(geom::geography, ST_MakePoint(?, ?)::geography) AS distance_meters
                        FROM geo_features
                        WHERE ST_DWithin(geom::geography, ST_MakePoint(?, ?)::geography, ?)
                        ORDER BY distance_meters
                        LIMIT ?
                        """,
                MAPPER, lon, lat, lon, lat, meters, limit);
    }

    @Override
    public GeoFeature save(String name, String category, String geometryGeoJson, String propertiesJson) {
        Long id = jdbc.queryForObject("""
                        INSERT INTO geo_features (name, category, geom, properties)
                        VALUES (?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), ?::jsonb)
                        RETURNING id
                        """,
                Long.class, name, category, geometryGeoJson, propertiesJson);
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<GeoFeature> findById(long id) {
        return jdbc.query(SELECT + " WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public boolean delete(long id) {
        return jdbc.update("DELETE FROM geo_features WHERE id = ?", id) > 0;
    }

    @Override
    public List<CategoryCount> countByCategory() {
        return jdbc.query(
                "SELECT category, COUNT(*) AS features FROM geo_features GROUP BY category ORDER BY features DESC",
                (rs, row) -> new CategoryCount(rs.getString("category"), rs.getLong("features")));
    }

    private static boolean hasColumn(java.sql.ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (java.sql.SQLException e) {
            return false;
        }
    }
}

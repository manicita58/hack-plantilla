package com.hackplantilla.geo.domain;

import java.time.Instant;

/**
 * Una entidad geográfica. `geometry` es GeoJSON crudo (lo que PostGIS produce
 * con ST_AsGeoJSON y lo que Leaflet consume tal cual) y `properties` es JSON
 * libre: el dominio no impone un esquema porque cada demo guarda otra cosa.
 *
 * `distanceMeters` solo viene con valor en las búsquedas por cercanía.
 */
public record GeoFeature(
        Long id,
        String name,
        String category,
        String geometry,
        String properties,
        Instant createdAt,
        Double distanceMeters) {
}

package com.hackplantilla.geo.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de salida del geovisor. */
public interface FeatureRepository {

    /** Todo lo que cae dentro del bbox (o todo, si el bbox es null). */
    List<GeoFeature> search(BoundingBox bbox, String category, int limit);

    /** Lo que está a menos de `meters` del punto, del más cerca al más lejos. */
    List<GeoFeature> near(double lon, double lat, double meters, int limit);

    GeoFeature save(String name, String category, String geometryGeoJson, String propertiesJson);

    Optional<GeoFeature> findById(long id);

    boolean delete(long id);

    List<CategoryCount> countByCategory();

    /** minLon, minLat, maxLon, maxLat en WGS84 (EPSG:4326). */
    record BoundingBox(double minLon, double minLat, double maxLon, double maxLat) {

        /** Formato `minLon,minLat,maxLon,maxLat`, el mismo que usan WMS y Leaflet. */
        public static BoundingBox parse(String csv) {
            String[] partes = csv.split(",");
            if (partes.length != 4) {
                throw new IllegalArgumentException("bbox esperado: minLon,minLat,maxLon,maxLat");
            }
            try {
                return new BoundingBox(Double.parseDouble(partes[0]), Double.parseDouble(partes[1]),
                        Double.parseDouble(partes[2]), Double.parseDouble(partes[3]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("el bbox tiene que ser cuatro números");
            }
        }
    }

    record CategoryCount(String category, long features) {
    }
}

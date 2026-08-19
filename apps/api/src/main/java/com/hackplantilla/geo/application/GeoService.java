package com.hackplantilla.geo.application;

import com.hackplantilla.geo.GeoModule;
import com.hackplantilla.geo.domain.FeatureRepository;
import com.hackplantilla.geo.domain.GeoFeature;
import java.util.List;
import org.springframework.stereotype.Service;

/** Casos de uso del geovisor: consultar el mapa, agregar y borrar features. */
@Service
@GeoModule
public class GeoService {

    /** Tope duro: sin esto un bbox del planeta entero se trae la tabla completa al browser. */
    private static final int MAX_FEATURES = 2000;

    private final FeatureRepository features;

    public GeoService(FeatureRepository features) {
        this.features = features;
    }

    public List<GeoFeature> search(String bbox, String category, Integer limit) {
        FeatureRepository.BoundingBox caja = bbox == null || bbox.isBlank()
                ? null
                : FeatureRepository.BoundingBox.parse(bbox);
        return features.search(caja, blankToNull(category), tope(limit));
    }

    public List<GeoFeature> near(double lon, double lat, double meters, Integer limit) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("coordenadas fuera de rango (lon -180..180, lat -90..90)");
        }
        if (meters <= 0) {
            throw new IllegalArgumentException("el radio tiene que ser mayor que cero");
        }
        return features.near(lon, lat, meters, tope(limit));
    }

    public GeoFeature create(String name, String category, String geometryGeoJson, String propertiesJson) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("la feature necesita un nombre");
        }
        if (geometryGeoJson == null || geometryGeoJson.isBlank()) {
            throw new IllegalArgumentException("la feature necesita una geometría GeoJSON");
        }
        return features.save(name.strip(),
                category == null || category.isBlank() ? "default" : category.strip(),
                geometryGeoJson,
                propertiesJson == null || propertiesJson.isBlank() ? "{}" : propertiesJson);
    }

    public boolean delete(long id) {
        return features.delete(id);
    }

    public List<FeatureRepository.CategoryCount> stats() {
        return features.countByCategory();
    }

    private static int tope(Integer limit) {
        return limit == null ? MAX_FEATURES : Math.clamp(limit, 1, MAX_FEATURES);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

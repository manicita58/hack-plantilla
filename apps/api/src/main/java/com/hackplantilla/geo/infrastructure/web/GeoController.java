package com.hackplantilla.geo.infrastructure.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hackplantilla.geo.GeoModule;
import com.hackplantilla.geo.application.GeoService;
import com.hackplantilla.geo.domain.FeatureRepository;
import com.hackplantilla.geo.domain.GeoFeature;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adaptador de entrada del geovisor. Habla GeoJSON puro para los dos lados:
 * lo que devuelve se le pasa a `L.geoJSON(...)` de Leaflet sin transformar nada.
 */
@RestController
@RequestMapping("/geo")
@GeoModule
public class GeoController {

    private final GeoService geo;
    private final ObjectMapper json;

    public GeoController(GeoService geo, ObjectMapper json) {
        this.geo = geo;
        this.json = json;
    }

    @GetMapping("/features")
    public Map<String, Object> features(@RequestParam(required = false) String bbox,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) Integer limit) {
        return featureCollection(geo.search(bbox, category, limit));
    }

    @GetMapping("/features/near")
    public Map<String, Object> near(@RequestParam double lon,
                                    @RequestParam double lat,
                                    @RequestParam(defaultValue = "1000") double meters,
                                    @RequestParam(required = false) Integer limit) {
        return featureCollection(geo.near(lon, lat, meters, limit));
    }

    @PostMapping("/features")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody NewFeature body) {
        GeoFeature creada = geo.create(
                body.name(),
                body.category(),
                body.geometry() == null ? null : body.geometry().toString(),
                body.properties() == null ? null : body.properties().toString());
        return feature(creada);
    }

    @DeleteMapping("/features/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (!geo.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe la feature " + id);
        }
    }

    @GetMapping("/stats")
    public List<FeatureRepository.CategoryCount> stats() {
        return geo.stats();
    }

    private Map<String, Object> featureCollection(List<GeoFeature> features) {
        List<Map<String, Object>> lista = new ArrayList<>(features.size());
        for (GeoFeature f : features) {
            lista.add(feature(f));
        }
        return Map.of("type", "FeatureCollection", "features", lista);
    }

    /** Arma el Feature de GeoJSON: geometry y properties vuelven a ser JSON, no strings. */
    private Map<String, Object> feature(GeoFeature f) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", f.id());
        props.put("name", f.name());
        props.put("category", f.category());
        props.put("createdAt", f.createdAt());
        if (f.distanceMeters() != null) {
            props.put("distanceMeters", f.distanceMeters());
        }
        props.putAll(json.convertValue(parse(f.properties()), Map.class));

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", f.id());
        feature.put("geometry", parse(f.geometry()));
        feature.put("properties", props);
        return feature;
    }

    private JsonNode parse(String raw) {
        return json.readTree(raw == null || raw.isBlank() ? "{}" : raw);
    }

    /** `geometry` y `properties` llegan como JSON anidado, no como string. */
    public record NewFeature(String name, String category, JsonNode geometry, JsonNode properties) {
    }
}

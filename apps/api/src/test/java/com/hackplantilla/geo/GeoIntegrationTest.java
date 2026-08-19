package com.hackplantilla.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hackplantilla.geo.application.GeoService;
import com.hackplantilla.geo.domain.GeoFeature;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica que PostGIS esté de verdad en la base y que las consultas espaciales
 * hagan lo que dicen. Si la imagen de Postgres no trae la extensión, esto falla
 * acá y no en la demo.
 */
@SpringBootTest
@Transactional
class GeoIntegrationTest {

    private static final String PUNTO_BOGOTA = """
            {"type":"Point","coordinates":[-74.0721,4.5981]}""";

    @Autowired
    private GeoService geo;

    @Test
    void guardaYEncuentraPorBoundingBox() {
        GeoFeature creada = geo.create("prueba", "test", PUNTO_BOGOTA, """
                {"fuente":"test"}""");

        assertThat(creada.id()).isNotNull();
        assertThat(creada.geometry()).contains("Point");

        // Un bbox chico alrededor del punto: si el índice o el SRID estuvieran
        // mal, esto vendría vacío.
        List<GeoFeature> dentro = geo.search("-74.1,4.5,-74.0,4.7", "test", null);
        assertThat(dentro).extracting(GeoFeature::name).contains("prueba");

        // Y un bbox del otro lado del mundo no tiene que traerlo.
        assertThat(geo.search("100,-40,110,-30", "test", null)).isEmpty();
    }

    @Test
    void buscaPorCercaniaEnMetros() {
        geo.create("cerca", "test-cercania", PUNTO_BOGOTA, null);

        // ~300 m al norte del punto: entra en un radio de 1 km y no en uno de 100 m.
        List<GeoFeature> cerca = geo.near(-74.0721, 4.6008, 1000, null);
        assertThat(cerca).extracting(GeoFeature::name).contains("cerca");
        assertThat(cerca.getFirst().distanceMeters()).isNotNull().isLessThan(1000);

        assertThat(geo.near(-74.0721, 4.6008, 100, null))
                .extracting(GeoFeature::name)
                .doesNotContain("cerca");
    }

    @Test
    void rechazaCoordenadasImposibles() {
        assertThatThrownBy(() -> geo.near(0, 200, 100, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

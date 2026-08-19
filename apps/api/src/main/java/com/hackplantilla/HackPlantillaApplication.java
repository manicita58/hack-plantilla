package com.hackplantilla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Cada módulo (blockchain, ai, geo) vive en su propio paquete con su hexágono
 * adentro y se prende o apaga con `modules.<nombre>.enabled`. Para desprender
 * uno: borrá su carpeta y su migración Flyway; nada fuera de ella lo referencia.
 */
@SpringBootApplication
public class HackPlantillaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HackPlantillaApplication.class, args);
    }
}

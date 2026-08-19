package com.hackplantilla.geo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Beans del geovisor. Se apaga entero con `modules.geo.enabled=false`. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "modules.geo", name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface GeoModule {
}

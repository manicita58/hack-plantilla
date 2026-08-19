package com.hackplantilla.ai;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Beans del módulo de IA. Se apaga entero con `modules.ai.enabled=false`. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "modules.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface AiModule {
}

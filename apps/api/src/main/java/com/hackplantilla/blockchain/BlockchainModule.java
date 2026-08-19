package com.hackplantilla.blockchain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Marca los beans del módulo: con `modules.blockchain.enabled=false` no se
 * registra ninguno y los endpoints desaparecen. Una anotación en vez de repetir
 * el @ConditionalOnProperty en cada clase.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "modules.blockchain", name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface BlockchainModule {
}

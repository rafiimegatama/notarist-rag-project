package com.notarist.runtime.ocr.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the OCR module.
 *
 * <p>Deliberately almost empty. Providers are plain {@code @Component} beans that Spring discovers
 * and hands to {@code OcrProviderRegistry} as a {@code List<OcrProvider>}, so a new engine needs no
 * entry here, no {@code @Bean} method, and no edit to any shared configuration class. That is the
 * whole point: the dependency-injection wiring for "add an OCR engine" is a single
 * {@code @Component} annotation on the new class.
 */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrModuleConfig {
}

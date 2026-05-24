package com.notarist.document.config;

import org.springframework.context.annotation.Configuration;

/**
 * Document module Spring configuration.
 * All beans auto-discovered via @Component / @Repository / @Service annotations.
 * JPA entities in infrastructure.persistence.oracle are scanned by the root EntityManagerFactory.
 */
@Configuration
public class DocumentModuleConfig {
}

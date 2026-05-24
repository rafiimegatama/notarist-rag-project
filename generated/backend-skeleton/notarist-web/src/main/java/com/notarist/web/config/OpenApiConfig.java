package com.notarist.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 configuration.
 * TODO (STEP 8B): wire full API spec from /generated/openapi/notarist-api.yaml.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notaristOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("NOTARIST RAG Platform API")
                .version("1.0.0")
                .description("Internal API — Enterprise Legal Document Intelligence Platform"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}

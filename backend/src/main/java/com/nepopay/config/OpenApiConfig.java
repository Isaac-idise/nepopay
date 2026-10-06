package com.nepopay.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds an "Authorize" button to Swagger UI: log in, paste the accessToken, call protected endpoints. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nepOpayOpenApi() {
        return new OpenAPI()
                .info(new Info().title("NepOpay API")
                        .description("Simulated Nigerian fintech sandbox. 100% simulated, no real money.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}

package com.game.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gameOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multiplayer Game API")
                        .version("0.0.1")
                        .description("Backend API for the multiplayer web game."));
    }
}


package com.crediya.solicitudes.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI crediyaSolicitudesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CrediYa - Microservicio de Solicitudes")
                .description("Solicitudes de prestamo de la plataforma CrediYa")
                .version("v1"));
    }
}

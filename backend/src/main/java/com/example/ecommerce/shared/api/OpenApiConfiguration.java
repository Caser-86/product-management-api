package com.example.ecommerce.shared.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI productManagementOpenApi() {
        return new OpenAPI().info(
            new Info()
                .title("Product Management API")
                .description("Spring Boot ecommerce product management service")
                .version("v1")
                .contact(new Contact().name("Caser-86"))
        );
    }
}

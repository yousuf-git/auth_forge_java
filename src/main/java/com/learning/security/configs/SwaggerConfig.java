// Contains the Swagger configuration for the application

package com.learning.security.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                    .title("Auth Forge - Authentication and Authorization by JWT")
                    
                    .version("v1.0.0")
                    
                    .description("This is a template Spring Boot REST microservice using Spring Security and JWT Token")
                    
                    .contact(new Contact().name("M. Yousuf").email("yousuf.work09@gmail.com").url("https://github.com/yousuf-git"))
                    
                    )
                    .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                    .components(new Components()
                            .addSecuritySchemes("bearerAuth", 
                                    new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")
                                    )
                                )
                    
                    // .servers(List.of(
                    //     new Server().url("http://localhost:8081").description("Test1"),
                    //     new Server().url("http://locahost8082").description("Test2")))
                    
                    
                    ;

    }
}

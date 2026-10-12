package com.urgentia.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Titulo y version que muestra el Swagger para la entrada del propio gateway. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi(@Value("${servicio.version}") String version) {
        return new OpenAPI().info(new Info()
                .title("UrgentIA - API Gateway")
                .description("Puerta de entrada de UrgentIA: valida el token, controla el rol "
                        + "y reenvia cada pedido al servicio que corresponde.")
                .version(version));
    }
}

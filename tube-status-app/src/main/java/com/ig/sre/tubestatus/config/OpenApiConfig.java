package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tubeStatusOpenApi() {
        return new OpenAPI().info(new Info()
                .title(AppConstants.OpenApi.TITLE)
                .description(AppConstants.OpenApi.DESCRIPTION)
                .version(AppConstants.OpenApi.VERSION));
    }
}

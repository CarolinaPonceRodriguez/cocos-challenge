package org.cocos.shared.infrastructure.openapi;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.cocos.shared.infrastructure.dto.response.ApiErrorResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String ERROR_STATUS = "[45]\\d\\d";

    @Bean
    OpenAPI cocosChallengeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Cocos Challenge API")
                .version("v1")
                .description("API for instrument search, portfolios, and orders. "
                        + "Every error returns an ApiErrorResponse whose `code` identifies the cause."));
    }

    @Bean
    OpenApiCustomizer errorResponses() {
        return openApi -> {
            openApi.getComponents().getSchemas().putAll(ModelConverters.getInstance().readAll(ApiErrorResponse.class));
            Content errorContent = new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref(ApiErrorResponse.class.getSimpleName())));

            openApi.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .map(Operation::getResponses)
                    .forEach(responses -> {
                        responses.addApiResponse("500", new ApiResponse().description("INTERNAL_ERROR"));
                        responses.entrySet().stream()
                                .filter(response -> response.getKey().matches(ERROR_STATUS))
                                .forEach(response -> response.getValue().setContent(errorContent));
                    });
        };
    }
}

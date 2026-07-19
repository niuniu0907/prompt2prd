package com.prompt2prd.common.config;

import com.prompt2prd.model.adapter.SpringAiModelGateway;
import com.prompt2prd.model.application.ModelGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ModelGatewayConfiguration {

    @Bean
    ModelGateway modelGateway(GenerationProperties generationProperties) {
        return new SpringAiModelGateway(
                com.prompt2prd.model.adapter.EndpointAddressPolicy.fromEnvironment(),
                generationProperties);
    }
}

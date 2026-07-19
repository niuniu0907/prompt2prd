package com.prompt2prd.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Configures the WebFlux codec to use a Jackson 2 ObjectMapper with explicit
 * record support via {@link ParameterNamesModule}.
 *
 * <p>Spring Boot 4.1.0's auto-configured ObjectMapper can misconfigure record
 * handling when both Jackson 2 and Jackson 3 are on the classpath. This bean
 * guarantees the server can deserialize Java records from JSON.</p>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration implements WebFluxConfigurer {

    @Bean
    @Primary
    ObjectMapper prompt2prdObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(new ParameterNamesModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        ObjectMapper mapper = prompt2prdObjectMapper();
        configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
        configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
    }
}

package com.telkom.co.ke.almoptics.configs;

import com.telkom.co.ke.almoptics.configs.GlobalWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.http.codec.ServerCodecConfigurer;

@Configuration
@EnableWebFlux
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Increase buffer sizes for large file downloads
        configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024); // 16MB
    }

    @Bean
    public WebExceptionHandler globalExceptionHandler() {
        return new GlobalWebExceptionHandler();
    }
}
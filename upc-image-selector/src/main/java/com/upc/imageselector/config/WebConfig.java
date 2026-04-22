package com.upc.imageselector.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC resource handler — registered by {@code UpcImageSelectorAutoConfiguration}.
 * Not annotated with {@code @Configuration} so it is never picked up by component
 * scanning; the library always wires it explicitly through auto-configuration.
 */
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + appProperties.getDownloadDir() + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/selected/**")
                .addResourceLocations("file:" + appProperties.getSelectedDir() + "/")
                .setCachePeriod(3600);
    }
}

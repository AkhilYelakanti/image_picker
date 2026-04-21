package com.upc.imageselector.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AppProperties appProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve downloaded images at /images/**
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + appProperties.getDownloadDir() + "/")
                .setCachePeriod(3600);

        // Serve selected images at /selected/**
        registry.addResourceHandler("/selected/**")
                .addResourceLocations("file:" + appProperties.getSelectedDir() + "/")
                .setCachePeriod(3600);
    }
}

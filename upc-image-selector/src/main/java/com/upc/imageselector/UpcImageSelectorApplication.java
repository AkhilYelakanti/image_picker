package com.upc.imageselector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import com.upc.imageselector.config.AppProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class UpcImageSelectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpcImageSelectorApplication.class, args);
    }
}

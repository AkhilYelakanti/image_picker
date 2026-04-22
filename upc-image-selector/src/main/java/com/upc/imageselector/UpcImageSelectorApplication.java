package com.upc.imageselector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for running the UPC Image Selector as a standalone Spring Boot
 * application (the *-exec.jar artifact).
 *
 * When used as a library dependency this class is present in the JAR but is
 * never invoked — the consumer's own @SpringBootApplication class drives the
 * application context instead.
 */
@SpringBootApplication
public class UpcImageSelectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpcImageSelectorApplication.class, args);
    }
}

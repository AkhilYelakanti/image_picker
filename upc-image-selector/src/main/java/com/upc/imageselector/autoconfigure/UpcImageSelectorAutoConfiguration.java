package com.upc.imageselector.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.upc.imageselector.config.AppProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import com.upc.imageselector.config.AsyncConfig;
import com.upc.imageselector.config.WebConfig;
import com.upc.imageselector.controller.ApiController;
import com.upc.imageselector.controller.ExportController;
import com.upc.imageselector.controller.UiController;
import com.upc.imageselector.exception.GlobalExceptionHandler;
import com.upc.imageselector.service.*;
import com.upc.imageselector.util.FilenameParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot auto-configuration for the UPC Image Selector library.
 *
 * <p>Activated automatically when this JAR is on the classpath. All beans are
 * guarded with {@code @ConditionalOnMissingBean} so consumers can override any
 * individual component by declaring their own bean of the same type.
 *
 * <p>Web (REST API) and UI (Thymeleaf) components are conditional:
 * <ul>
 *   <li>REST API — requires {@code spring-boot-starter-web} on the classpath</li>
 *   <li>Review UI — additionally requires {@code spring-boot-starter-thymeleaf}</li>
 * </ul>
 */
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
@Import(AsyncConfig.class)
public class UpcImageSelectorAutoConfiguration {

    // ── Jackson customizer — write dates as ISO-8601 strings, not timestamps ─

    @Bean
    @ConditionalOnMissingBean(name = "upcImageSelectorJacksonCustomizer")
    public Jackson2ObjectMapperBuilderCustomizer upcImageSelectorJacksonCustomizer() {
        return builder -> builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Core services (always active) ────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public FilenameParser filenameParser() {
        return new FilenameParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public ScoringService scoringService(AppProperties props) {
        return new ScoringService(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersistenceService persistenceService(AppProperties props, ObjectMapper objectMapper) {
        return new PersistenceService(props, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DownloadService downloadService(AppProperties props, FilenameParser filenameParser) {
        return new DownloadService(props, filenameParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportService exportService(AppProperties props) {
        return new ExportService(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessingService processingService(AppProperties props,
                                               DownloadService downloadService,
                                               ScoringService scoringService,
                                               PersistenceService persistenceService,
                                               ExportService exportService) {
        return new ProcessingService(props, downloadService, scoringService,
                persistenceService, exportService);
    }

    // ── REST API + MVC config (requires spring-boot-starter-web) ─────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public WebConfig webConfig(AppProperties props) {
            return new WebConfig(props);
        }

        @Bean
        @ConditionalOnMissingBean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        @ConditionalOnMissingBean
        public ApiController apiController(ProcessingService processingService,
                                           PersistenceService persistenceService) {
            return new ApiController(processingService, persistenceService);
        }

        @Bean
        @ConditionalOnMissingBean
        public ExportController exportController(ExportService exportService,
                                                 PersistenceService persistenceService) {
            return new ExportController(exportService, persistenceService);
        }
    }

    // ── Review UI (requires spring-boot-starter-thymeleaf + web) ─────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.thymeleaf.spring6.SpringTemplateEngine")
    static class UiAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public UiController uiController(ProcessingService processingService,
                                         PersistenceService persistenceService) {
            return new UiController(processingService, persistenceService);
        }
    }
}

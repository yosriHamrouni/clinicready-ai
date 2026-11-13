package com.clinicready.document;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DocumentProperties.class)
class DocumentConfig {

    static final String EXTRACTION_EXECUTOR = "documentExtractionExecutor";

    /** Bounded pool and queue: a burst of uploads waits (or waits for the sweep), it never spawns threads. */
    @Bean(EXTRACTION_EXECUTOR)
    ThreadPoolTaskExecutor documentExtractionExecutor(DocumentProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.extraction().threads());
        executor.setMaxPoolSize(properties.extraction().threads());
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("doc-extract-");
        // Unfinished documents stay PENDING and are picked up again at the next startup.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean
    TextExtractor textExtractor(DocumentProperties properties) {
        return new TextExtractor(properties.extraction().maxPages(), properties.extraction().maxChars());
    }
}

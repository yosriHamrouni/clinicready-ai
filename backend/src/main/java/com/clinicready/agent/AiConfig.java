package com.clinicready.agent;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {

    static final String AGENT_EXECUTOR = "agentRunExecutor";

    /** Spring AI builds its provider client from the shared {@code RestClient.Builder}, so this sets its timeouts. */
    @Bean
    RestClientCustomizer aiTimeoutCustomizer(AiProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.timeout()).withReadTimeout(properties.timeout());
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }

    /** Bounded pool and queue: a burst of requests is refused with a clear status, it never spawns threads. */
    @Bean(AGENT_EXECUTOR)
    ThreadPoolTaskExecutor agentRunExecutor(AiProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.runThreads());
        executor.setMaxPoolSize(properties.runThreads());
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-run-");
        // Unfinished runs are marked FAILED at the next startup.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}

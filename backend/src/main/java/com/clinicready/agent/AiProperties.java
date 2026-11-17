package com.clinicready.agent;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Model access settings. The provider, model name and API key are Spring AI's own
 * ({@code spring.ai.model.chat}, {@code spring.ai.anthropic.*}); this holds what Spring AI does not.
 *
 * @param timeout      connect and read timeout for every call to the provider
 * @param maxToolCalls tool calls one agent run may make before it is stopped
 * @param runTimeout   wall time one agent run may take before it is stopped
 * @param runThreads   agent runs executed at the same time; more wait in a queue
 */
@ConfigurationProperties("clinicready.ai")
public record AiProperties(
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("15") int maxToolCalls,
        @DefaultValue("90s") Duration runTimeout,
        @DefaultValue("2") int runThreads) {
}

package com.clinicready.agent;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Model access settings. The provider, model name and API key are Spring AI's own
 * ({@code spring.ai.model.chat}, {@code spring.ai.anthropic.*}); this holds what Spring AI does not.
 *
 * @param timeout connect and read timeout for every call to the provider
 */
@ConfigurationProperties("clinicready.ai")
public record AiProperties(@DefaultValue("30s") Duration timeout) {
}

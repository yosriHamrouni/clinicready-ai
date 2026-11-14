package com.clinicready.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Active under the {@value #PROFILE} profile: a scripted model replaces the provider, no API key or network. */
@Configuration
@Profile(StubAiConfig.PROFILE)
public class StubAiConfig {

    public static final String PROFILE = "stub-ai";

    @Bean
    ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }
}

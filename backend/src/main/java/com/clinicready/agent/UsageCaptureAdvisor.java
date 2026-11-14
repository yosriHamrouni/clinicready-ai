package com.clinicready.agent;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;

/** Publishes a {@link ModelUsage} event after every model call made through {@link AgentChat}. */
class UsageCaptureAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(UsageCaptureAdvisor.class);

    private final ApplicationEventPublisher events;

    UsageCaptureAdvisor(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.nanoTime();
        ChatClientResponse response = chain.nextCall(request);
        Duration latency = Duration.ofNanos(System.nanoTime() - start);

        ChatResponseMetadata metadata = response.chatResponse() == null ? null : response.chatResponse().getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        String model = metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()
                ? "unknown" : metadata.getModel();
        ModelUsage captured = new ModelUsage(model, count(usage == null ? null : usage.getPromptTokens()),
                count(usage == null ? null : usage.getCompletionTokens()),
                count(usage == null ? null : usage.getTotalTokens()), latency);
        log.info("model call model={} promptTokens={} completionTokens={} totalTokens={} latencyMs={}", captured.model(),
                captured.promptTokens(), captured.completionTokens(), captured.totalTokens(), latency.toMillis());
        events.publishEvent(captured);
        return response;
    }

    private static int count(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }

    @Override
    public String getName() {
        return "usageCapture";
    }

    @Override
    public int getOrder() {
        // Just ahead of the built-in advisor that calls the model, which ends the chain.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}

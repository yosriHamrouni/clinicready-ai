package com.clinicready.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The one way the app talks to a model. The {@link ChatModel} bean exists only when
 * {@code spring.ai.model.chat} names a provider (or a test profile supplies a stub), so it is looked up lazily.
 * Every call made through the client reports a {@link ModelUsage} event.
 */
@Component
public class AgentChat {

    private final ObjectProvider<ChatModel> chatModel;
    private final UsageCaptureAdvisor usage;

    AgentChat(ObjectProvider<ChatModel> chatModel, ApplicationEventPublisher events) {
        this.chatModel = chatModel;
        this.usage = new UsageCaptureAdvisor(events);
    }

    public boolean available() {
        return chatModel.getIfAvailable() != null;
    }

    /** @throws org.springframework.beans.BeansException when no model is configured; check {@link #available()} first */
    public ChatClient client() {
        return ChatClient.builder(chatModel.getObject()).defaultAdvisors(usage).build();
    }
}

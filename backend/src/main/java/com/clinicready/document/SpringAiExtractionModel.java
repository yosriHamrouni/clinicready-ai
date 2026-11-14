package com.clinicready.document;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import com.clinicready.agent.AgentChat;

/** Document classification on top of {@link AgentChat}, which owns the model, timeout and usage capture. */
@Component
class SpringAiExtractionModel implements ExtractionModel {

    private final AgentChat chat;

    SpringAiExtractionModel(AgentChat chat) {
        this.chat = chat;
    }

    @Override
    public boolean available() {
        return chat.available();
    }

    @Override
    public Answer complete(String systemPrompt, String documentText) {
        // The text goes in as a template parameter: braces inside a document must not be parsed as a template.
        ChatResponse response = chat.client().prompt()
                .system(systemPrompt)
                .user(u -> u.text("{document}").param("document", documentText))
                .call()
                .chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Empty model response");
        }
        String name = response.getMetadata() != null ? response.getMetadata().getModel() : null;
        return new Answer(response.getResult().getOutput().getText(),
                name == null || name.isBlank() ? "unknown" : name);
    }
}

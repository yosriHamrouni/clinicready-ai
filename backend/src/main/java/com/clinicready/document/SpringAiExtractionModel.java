package com.clinicready.document;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Spring AI wiring (YOS-17, pulled forward). The chat model bean exists only when
 * {@code spring.ai.model.chat} names a provider, so it is looked up lazily.
 */
@Component
class SpringAiExtractionModel implements ExtractionModel {

    private final ObjectProvider<ChatModel> chatModel;

    SpringAiExtractionModel(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public boolean available() {
        return chatModel.getIfAvailable() != null;
    }

    @Override
    public Answer complete(String systemPrompt, String documentText) {
        ChatModel model = chatModel.getObject();
        // The text goes in as a template parameter: braces inside a document must not be parsed as a template.
        ChatResponse response = ChatClient.create(model).prompt()
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

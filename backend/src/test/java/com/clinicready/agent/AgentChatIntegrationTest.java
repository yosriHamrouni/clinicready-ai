package com.clinicready.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;

import com.clinicready.IntegrationTest;

@IntegrationTest
@ActiveProfiles(StubAiConfig.PROFILE)
@Import(AgentChatIntegrationTest.Usages.class)
class AgentChatIntegrationTest {

    @TestConfiguration
    static class Usages {
        final List<ModelUsage> seen = new ArrayList<>();

        @EventListener
        void on(ModelUsage usage) {
            seen.add(usage);
        }
    }

    @Autowired
    private AgentChat chat;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private Usages usages;
    @Autowired
    private AiProperties properties;

    @BeforeEach
    void reset() {
        model.reset();
        usages.seen.clear();
    }

    @Test
    void stubReplacesTheProvider() {
        assertThat(chat.available()).isTrue();
    }

    @Test
    void scriptedToolCallComesBackFixed() {
        model.script(ScriptedChatModel.toolCall("get_patient", "{\"id\":\"p1\"}"), ScriptedChatModel.text("done"));
        ChatClient client = chat.client();

        ChatResponse first = client.prompt().user("who?").call().chatResponse();
        ChatResponse second = client.prompt().user("again").call().chatResponse();

        assertThat(first.getResult().getOutput().getToolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("get_patient");
            assertThat(call.arguments()).isEqualTo("{\"id\":\"p1\"}");
        });
        assertThat(second.getResult().getOutput().getText()).isEqualTo("done");
        assertThat(model.prompts()).hasSize(2);
    }

    @Test
    void tokenUsageIsCapturedPerCall() {
        model.script(ScriptedChatModel.text("a"), ScriptedChatModel.text("b").withUsage(20, 7));
        chat.client().prompt().user("one").call().content();
        chat.client().prompt().user("two").call().content();

        assertThat(usages.seen).extracting(ModelUsage::model).containsOnly(ScriptedChatModel.MODEL_NAME);
        assertThat(usages.seen).extracting(ModelUsage::promptTokens).containsExactly(10, 20);
        assertThat(usages.seen).extracting(ModelUsage::completionTokens).containsExactly(5, 7);
        assertThat(usages.seen).extracting(ModelUsage::totalTokens).containsExactly(15, 27);
    }

    @Test
    void timeoutComesFromConfig() {
        assertThat(properties.timeout()).hasSeconds(30);
    }
}

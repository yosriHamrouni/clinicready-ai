package com.clinicready.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Deterministic stand-in for the provider: returns the scripted replies in order, whatever the prompt says. */
public class ScriptedChatModel implements ChatModel {

    public static final String MODEL_NAME = "scripted-stub";

    /** One scripted reply. */
    public record Step(String text, List<AssistantMessage.ToolCall> toolCalls, int promptTokens, int completionTokens) {

        public Step withUsage(int promptTokens, int completionTokens) {
            return new Step(text, toolCalls, promptTokens, completionTokens);
        }
    }

    private final Queue<Step> script = new ConcurrentLinkedQueue<>();
    private final List<Prompt> prompts = new ArrayList<>();

    public static Step text(String text) {
        return new Step(text, List.of(), 10, 5);
    }

    public static Step toolCall(String name, String argumentsJson) {
        return new Step("", List.of(new AssistantMessage.ToolCall("call-" + name, "function", name, argumentsJson)), 10, 5);
    }

    public void script(Step... steps) {
        script.clear();
        script.addAll(List.of(steps));
    }

    public synchronized List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public synchronized void reset() {
        script.clear();
        prompts.clear();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        synchronized (this) {
            prompts.add(prompt);
        }
        Step step = script.poll();
        if (step == null) {
            throw new IllegalStateException("ScriptedChatModel has no scripted reply left");
        }
        AssistantMessage message = new AssistantMessage(step.text(), java.util.Map.of(), step.toolCalls());
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().model(MODEL_NAME)
                .usage(new DefaultUsage(step.promptTokens(), step.completionTokens())).build();
        return new ChatResponse(List.of(new Generation(message)), metadata);
    }
}

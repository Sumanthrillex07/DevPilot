package com.devpilot.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Configuration for the Spring AI {@link ChatModel}.
 *
 * <p>Uses {@code gpt-4o-mini} by default for high speed and reasoning accuracy at low cost.
 * Provides a graceful in-memory stub fallback when running tests or offline development.
 */
@Configuration
public class ChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatModelConfig.class);

    @Value("${spring.ai.openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Bean
    @Primary
    public ChatModel chatModel() {
        if (openAiApiKey != null && !openAiApiKey.isBlank() && !openAiApiKey.startsWith("sk-placeholder")) {
            log.info("Configuring OpenAiChatModel with gpt-4o-mini");
            OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model("gpt-4o-mini")
                            .temperature(0.2)
                            .build())
                    .build();
        }

        log.warn("No valid OPENAI_API_KEY found; falling back to stub ChatModel for local/offline run.");
        return new StubChatModel();
    }

    @Bean
    public org.springframework.ai.chat.client.ChatClient chatClient(ChatModel chatModel) {
        return org.springframework.ai.chat.client.ChatClient.builder(chatModel).build();
    }

    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Local DevPilot response: No OpenAI key configured."))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(
                    new ChatResponse(List.of(new Generation(new AssistantMessage("Local DevPilot response: ")))),
                    new ChatResponse(List.of(new Generation(new AssistantMessage("No OpenAI key configured."))))
            );
        }
    }
}

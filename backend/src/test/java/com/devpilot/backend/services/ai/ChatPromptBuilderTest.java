package com.devpilot.backend.services.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPromptBuilderTest {

    private final ChatPromptBuilder promptBuilder = new ChatPromptBuilder();

    @Test
    @DisplayName("systemPrompt: contains repository name and grounding rules")
    void systemPrompt_containsRepoNameAndGroundingRules() {
        String prompt = promptBuilder.systemPrompt("octocat/hello-world");

        assertThat(prompt).contains("octocat/hello-world");
        assertThat(prompt).contains("DevPilot");
        assertThat(prompt).contains("ONLY the provided code context");
    }

    @Test
    @DisplayName("userPrompt: formats context and user question")
    void userPrompt_formatsContextAndQuestion() {
        String prompt = promptBuilder.userPrompt("class Foo {}", "What does Foo do?");

        assertThat(prompt).contains("Relevant Code Context:");
        assertThat(prompt).contains("class Foo {}");
        assertThat(prompt).contains("User Question:");
        assertThat(prompt).contains("What does Foo do?");
    }
}

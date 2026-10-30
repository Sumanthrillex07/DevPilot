package com.devpilot.backend.services.ai;

import org.springframework.stereotype.Component;

/**
 * Builds structured prompts for the LLM adhering to code-grounded RAG principles.
 */
@Component
public class ChatPromptBuilder {

    /**
     * Constructs the system prompt instructing the model on persona, boundaries, and citations.
     */
    public String systemPrompt(String repositoryFullName) {
        return """
                You are DevPilot, an expert AI software engineering assistant for the %s codebase.
                Your task is to provide accurate, concise, and technically sound answers grounded in the provided code context.

                Rules:
                1. Answer using ONLY the provided code context. Do not invent non-existent APIs, methods, or files.
                2. If the context does not contain enough information to answer completely, acknowledge what is present and state that you are unsure.
                3. Always cite relevant file paths and line numbers when referencing code (e.g. `[src/main/App.java:42-55]`).
                4. When providing code examples, write clean, idiomatic code matching the style of the codebase.
                """.formatted(repositoryFullName);
    }

    /**
     * Constructs the user prompt combining retrieved context and the user's question.
     */
    public String userPrompt(String codeContext, String question) {
        return """
                Relevant Code Context:
                %s

                User Question:
                %s
                """.formatted(codeContext, question);
    }
}

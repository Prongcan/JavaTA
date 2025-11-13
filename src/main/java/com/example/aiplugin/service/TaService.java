package com.example.aiplugin.service;

import com.intellij.openapi.project.Project;

/**
 * The core service interface for the AI Teaching Assistant.
 * Team members should implement this interface to provide the actual RAG and LLM logic.
 */
public interface TaService {

    /**
     * A simple method to get an instance of the service.
     * @param project The current project.
     * @return An instance of TaService.
     */
    static TaService getInstance(Project project) {
        return project.getService(TaService.class);
    }

    /**
     * Handles a general question from the user.
     *
     * @param question The user's question.
     * @return The AI's response.
     */
    String askQuestion(String question);

    /**
     * Handles a question related to a specific code segment.
     *
     * @param code The selected code segment.
     * @param question The user's question about the code.
     * @return The AI's response.
     */
    String askAboutCode(String code, String question);
}

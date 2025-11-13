package com.example.aiplugin.service;

/**
 * A mock implementation of the TaService for UI development and testing.
 * This class provides placeholder responses without making actual AI/LLM calls.
 */
public class MockTaService implements TaService {

    @Override
    public String askQuestion(String question) {
        // Simulate a delay to mimic network latency
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Response based on general knowledge; no specific course material is referenced.\n\n" +
               "This is a mock response for the question: \"" + question + "\"";
    }

    @Override
    public String askAboutCode(String code, String question) {
        // Simulate a delay
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Source: Document 'Lecture 3.pdf', Page 5.\n\n" +
               "This is a mock response for the question: \"" + question + "\"";
    }
}


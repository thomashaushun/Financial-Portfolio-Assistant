package com.tsh11.fypcode.integration.llm;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiExplanationClient implements LlmExplanationClient {

    private final String modelName;

    public GeminiExplanationClient(
            @Value("${app.llm.gemini.model:gemini-2.5-flash}") String modelName) {
        this.modelName = modelName;
    }

    @Override
    public String explain(String prompt) {
        try {
            Client client = Client.builder().build();

            GenerateContentResponse response = client.models.generateContent(
                    modelName,
                    prompt,
                    null
            );

            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new IllegalStateException("Gemini returned an empty explanation.");
            }

            return response.text().trim();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to retrieve explanation from Gemini.", ex);
        }
    }
}
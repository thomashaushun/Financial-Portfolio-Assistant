package com.tsh11.fypcode.service;

import com.tsh11.fypcode.integration.llm.LlmExplanationClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

//receive from ExplainabilityService the following:
//cacheKey = unique key for this explanation request
//prompt = full explanation prompt sent to the LLM
//then
//returns cached explanation if the cache already has it
//or
//calls the LLM client and stores the result in cache
@Service
public class CachedLlmExplanationService {

    private final LlmExplanationClient llmExplanationClient;

    public CachedLlmExplanationService(LlmExplanationClient llmExplanationClient) {
        this.llmExplanationClient = llmExplanationClient;
    }

    @Cacheable(value = "llmExplanation", key = "#cacheKey")
    public String explain(String cacheKey, String prompt) {
        return llmExplanationClient.explain(prompt);
    }
}
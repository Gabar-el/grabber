package it.dsms.grabber.service.ai;

import it.dsms.grabber.engine.llm.AnthropicClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Implementazione di {@link AiAgentService} che usa Anthropic Claude.
 *
 * <p>Delega ad {@link AnthropicClient} (engine, zero Spring) che gestisce
 * la chiamata HTTP all'API Anthropic.
 *
 * <p>Il modello e la api-key vengono letti da {@code application.yaml}:
 * <pre>
 * anthropic:
 *   api-key: ${ANTHROPIC_API_KEY}
 *   model: claude-sonnet-4-5
 * </pre>
 *
 * <p>L'AnthropicClient Engine legge ancora {@code ANTHROPIC_API_KEY} dall'env
 * per compatibilità con la CLI. In questo servizio Spring la api-key viene
 * passata via costruttore per essere iniettabile/testabile.
 */
@Service
public class ClaudeAgentService implements AiAgentService {

    private final AnthropicClient client;

    public ClaudeAgentService(
            @Value("${anthropic.model:claude-sonnet-4-5}") String model) {
        // AnthropicClient legge ANTHROPIC_API_KEY dall'env (invariato dalla CLI)
        this.client = new AnthropicClient(model);
    }

    @Override
    public String complete(String prompt, int maxTokens) throws IOException {
        return client.complete(prompt, maxTokens);
    }

    @Override
    public String getModel() {
        return client.getModel();
    }
}

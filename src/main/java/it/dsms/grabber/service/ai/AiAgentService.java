package it.dsms.grabber.service.ai;

import java.io.IOException;

/**
 * Interfaccia per il servizio LLM.
 * Disaccoppia il provider (Anthropic Claude, OpenAI, ecc.) dal business logic.
 *
 * <p>Implementazione attiva: {@link ClaudeAgentService}
 */
public interface AiAgentService {

    /**
     * Invia un prompt e restituisce il testo della risposta.
     *
     * @param prompt    testo del messaggio utente
     * @param maxTokens limite token risposta
     * @return          testo completo della risposta
     * @throws IOException in caso di errore di rete o API
     */
    String complete(String prompt, int maxTokens) throws IOException;

    /**
     * Restituisce il nome del modello in uso (es. "claude-sonnet-4-5").
     * Usato per loggare in {@code recipe_candidates.llm_model}.
     */
    String getModel();
}

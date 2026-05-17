package it.dsms.grabber.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AnthropicClient {

    private static final String API_URL       = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION   = "2023-06-01";
    public  static final String DEFAULT_MODEL = "claude-sonnet-4-5";
    public  static final String PROMPT_VERSION = "curate_recipe_mediano_v1";

    private final OkHttpClient http;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;

    public AnthropicClient(String model) {
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException(
                "Variabile d'ambiente ANTHROPIC_API_KEY non impostata. " +
                "Esegui: set ANTHROPIC_API_KEY=sk-ant-... (Windows) o export ANTHROPIC_API_KEY=... (Linux/Mac)");
        }
        this.model  = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
        this.mapper = new ObjectMapper();
        this.http   = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Invia un messaggio e ritorna il testo della risposta.
     */
    public String complete(String userPrompt, int maxTokens) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", userPrompt);

        RequestBody requestBody = RequestBody.create(
                mapper.writeValueAsString(body),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Anthropic API error " + response.code() + ": " + responseBody);
            }
            JsonNode root = mapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText("");
            }
            throw new IOException("Risposta Anthropic API vuota o malformata: " + responseBody);
        }
    }

    public String getModel() { return model; }
}

package example.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public final class InfraiFailureCapture implements FailureCapture {
    private final String baseUrl;
    private final String key;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    public InfraiFailureCapture(@Value("${infrai.base-url}") String baseUrl,
                                @Value("${infrai.api-key}") String key, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.key = key;
        this.json = json;
    }

    @Override
    public void capture(String assetId, String step, Exception cause) {
        if (key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        try {
            byte[] body = json.writeValueAsBytes(Map.of(
                "title", "Media agent processing failed",
                "message", cause.toString(),
                "level", "error",
                "fingerprint", new String[] {"media-agent", step},
                "exception", cause.toString(),
                "context", Map.of("asset_id", assetId, "step", step)));
            // A stable event identity keeps a repeated job failure from producing duplicate writes.
            String eventId = java.util.UUID.nameUUIDFromBytes((assetId + ":" + step).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/errors/capture"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", eventId)
                    .method("POST", HttpRequest.BodyPublishers.ofByteArray(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    long delay = retryAfter.matches("[0-9]+") ? Math.min(30, Long.parseLong(retryAfter)) * 1000L : 500L * (1L << attempt);
                    Thread.sleep(delay);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false))
                    throw new IllegalStateException("Capture rejected: " + envelope.path("error").toString());
                if (response.statusCode() >= 500) throw new IllegalStateException("Capture transport status " + response.statusCode());
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Capture interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Capture transport failed", e);
        }
    }
}

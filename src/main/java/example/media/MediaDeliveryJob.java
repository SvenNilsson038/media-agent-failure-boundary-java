package example.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class MediaDeliveryJob {
    public record Asset(String id, String source, String creatorId) {}
    public record Delivery(String assetId, String creatorId, String status) {}
    public interface Processor { void process(Asset asset) throws Exception; }

    private final FailureCapture failures;
    private final String agent;

    public MediaDeliveryJob(FailureCapture failures, @Value("${media.agent:delivery-agent}") String agent) {
        this.failures = failures;
        this.agent = agent;
    }

    public Delivery run(Asset ingested, Processor processor) {
        try {
            processor.process(ingested);
            return new Delivery(ingested.id(), ingested.creatorId(), "DELIVERED");
        } catch (Exception cause) {
            failures.capture(ingested.id(), agent + ":process", cause);
            return new Delivery(ingested.id(), ingested.creatorId(), "HELD_FOR_REVIEW");
        }
    }
}

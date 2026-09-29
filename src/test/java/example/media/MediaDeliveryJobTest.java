package example.media;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaDeliveryJobTest {
    @Test
    void failedProcessingIsCapturedAndHeldFromCreatorDelivery() {
        class Recorder implements FailureCapture {
            String assetId;
            String step;
            public void capture(String id, String phase, Exception error) {
                assetId = id;
                step = phase;
            }
        }
        Recorder recorder = new Recorder();
        MediaDeliveryJob job = new MediaDeliveryJob(recorder, "delivery-agent");
        MediaDeliveryJob.Delivery result = job.run(
            new MediaDeliveryJob.Asset("asset-1042", "ingest/asset-1042.mp4", "creator-27"),
            asset -> { throw new IllegalArgumentException("Invalid transcode"); });
        assertEquals("HELD_FOR_REVIEW", result.status());
        assertEquals("creator-27", result.creatorId());
        assertEquals("asset-1042", recorder.assetId);
        assertEquals("delivery-agent:process", recorder.step);
    }
}

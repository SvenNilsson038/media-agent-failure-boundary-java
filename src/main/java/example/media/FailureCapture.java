package example.media;

public interface FailureCapture {
    void capture(String assetId, String step, Exception cause);
}

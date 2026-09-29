package example.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MediaAgentExample {
    public static void main(String[] args) { SpringApplication.run(MediaAgentExample.class, args); }

    @Bean
    CommandLineRunner sample(MediaDeliveryJob job) {
        return args -> {
            MediaDeliveryJob.Asset asset = new MediaDeliveryJob.Asset("asset-1042", "ingest/asset-1042.mp4", "creator-27");
            MediaDeliveryJob.Delivery delivery = job.run(asset, input -> {
                if (input.source().endsWith(".mp4")) throw new IllegalArgumentException("Transcode validation rejected asset");
            });
            System.out.println(delivery);
        };
    }
}

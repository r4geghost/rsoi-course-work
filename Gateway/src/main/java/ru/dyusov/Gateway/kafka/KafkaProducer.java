package ru.dyusov.Gateway.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
public class KafkaProducer {

    @Value(value = "statistics")
    private String topic;

    private final StreamBridge streamBridge;

    public KafkaProducer(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void send(LogMessage message) {
        try {
            streamBridge.send(topic, message);
        } catch (Exception e) {
            System.out.println("Error sending message: " + e.getMessage());
        }
    }
}

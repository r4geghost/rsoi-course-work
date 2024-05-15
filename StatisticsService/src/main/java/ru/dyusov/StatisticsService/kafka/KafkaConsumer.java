package ru.dyusov.StatisticsService.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import ru.dyusov.StatisticsService.model.Message;
import ru.dyusov.StatisticsService.service.StatisticsService;

import java.util.function.Consumer;

@Slf4j
@Component
public class KafkaConsumer {

    private final StatisticsService service;

    @Autowired
    public KafkaConsumer(StatisticsService service) {
        this.service = service;
    }

    @Bean
    public Consumer<Message> consumer() {
        return data -> {
            log.info("[STATISTICS]: {} got from topic", data);
            try {
                service.process(data);
            } catch (Exception ex) {
                log.error("[STATISTICS]: {} caught error, err={}", data, ex.getMessage());
            }
        };
    }
}

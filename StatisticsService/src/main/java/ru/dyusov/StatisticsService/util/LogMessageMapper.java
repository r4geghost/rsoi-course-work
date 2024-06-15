package ru.dyusov.StatisticsService.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.dyusov.StatisticsService.model.LogMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public class LogMessageMapper {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN);

    public static LogMessage convertMessage(String logMessage) throws JsonParseException {
        try {
            Map<String, Object> map = mapper.readValue(logMessage, new TypeReference<>() {});
            return new LogMessage(
                    UUID.fromString(map.get("eventUuid").toString()),
                    LocalDateTime.parse(map.get("eventStart").toString(), formatter),
                    LocalDateTime.parse(map.get("eventEnd").toString(), formatter),
                    map.get("username").toString(),
                    map.get("action").toString(),
                    map.get("service").toString()
            );
        } catch (Exception e) {
            System.out.println("Error while parsing log message: " + logMessage);
            throw new JsonParseException("Error while parsing log message: " + e.getMessage());
        }
    }
}

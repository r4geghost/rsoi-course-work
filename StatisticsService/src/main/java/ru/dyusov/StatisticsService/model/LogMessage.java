package ru.dyusov.StatisticsService.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Accessors(chain = true)
@ToString
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(schema = "public", name = "service_statistics")
public class LogMessage implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JsonProperty("eventUuid")
    public UUID eventUuid;

    @JsonProperty("eventStart")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    public LocalDateTime eventStart;

    @JsonProperty("eventEnd")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    public LocalDateTime eventEnd;

    @JsonProperty("username")
    public String username;

    @JsonProperty("action")
    public String action;

    @JsonProperty("service")
    public String service;
}

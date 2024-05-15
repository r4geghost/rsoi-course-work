package ru.dyusov.StatisticsService.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.UUID;

@Data
@Accessors(chain = true)
@ToString
@Entity
@Table(name = "service_statistics")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JsonProperty("eventUuid")
    public UUID eventUuid;

    @JsonProperty("eventStart")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    public Date eventStart;

    @JsonProperty("eventEnd")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    public Date eventEnd;

    @JsonProperty("username")
    public String username;

    @JsonProperty("action")
    public String action;

//    @JsonProperty("params")
//    public Map<String, Object> params;
//
//    @JsonAnySetter
//    void setParams(String key, Object value) {
//        params.put(key, value);
//    }

    @JsonProperty("service")
    public String service;
}

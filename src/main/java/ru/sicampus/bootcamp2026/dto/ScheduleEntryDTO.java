package ru.sicampus.bootcamp2026.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduleEntryDTO {
    private Long id;
    private String topic;
    private LocalDateTime dateTime;
}
package ru.sicampus.bootcamp2026.dto;
import lombok.Data;
import ru.sicampus.bootcamp2026.entity.JobTitle;

@Data
public class ParticipantDTO {
    private String fullName;
    private JobTitle jobTitle;
    private String status;
}
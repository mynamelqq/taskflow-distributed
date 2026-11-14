package com.taskflow.schedulerjava.domain;

import java.time.LocalDateTime;

public record TaskRecord (
    Long id,
    String taskType,
    String payload,
    String status,
    String result,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
){

}

package com.taskflow.schedulerjava.dto;


import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record TaskResponse(
        long id,
        String taskType,
        JsonNode payload,
        String status,
        JsonNode result,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {}
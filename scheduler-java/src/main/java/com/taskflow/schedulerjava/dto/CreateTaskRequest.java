package com.taskflow.schedulerjava.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record CreateTaskRequest(
    @NotBlank
    String taskType,

    @NotNull
    JsonNode payload
){}


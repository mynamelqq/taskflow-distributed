package com.taskflow.schedulerjava.dto;

public record CreateTaskResponse(
        long taskId,
        String message
) {}
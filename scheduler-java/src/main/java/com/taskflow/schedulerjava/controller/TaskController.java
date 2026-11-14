package com.taskflow.schedulerjava.controller;

import com.taskflow.schedulerjava.dto.CreateTaskRequest;
import com.taskflow.schedulerjava.dto.CreateTaskResponse;
import com.taskflow.schedulerjava.dto.TaskResponse;
import com.taskflow.schedulerjava.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }
    @PostMapping
    public ResponseEntity<CreateTaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request) {

        long taskId = taskService.createTask(
                request.taskType(),
                request.payload()
        );

        return ResponseEntity.accepted().body(
                new CreateTaskResponse(
                        taskId,
                        "Task accepted"
                )
        );
    }
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(
            @PathVariable long id) {

        return ResponseEntity.ok(
                taskService.getTask(id)
        );
    }
}

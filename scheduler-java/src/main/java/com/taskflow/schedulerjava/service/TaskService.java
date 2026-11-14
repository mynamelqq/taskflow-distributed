package com.taskflow.schedulerjava.service;

import com.taskflow.schedulerjava.domain.TaskRecord;
import com.taskflow.schedulerjava.dto.TaskResponse;
import com.taskflow.schedulerjava.executor.TaskHandlerRegistry;
import com.taskflow.schedulerjava.infrastructure.LocalTaskDispatcher;
import com.taskflow.schedulerjava.repository.TaskRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


@Service
public class TaskService {
    private final TaskRepository repository;
    private final TaskHandlerRegistry registry;
    private final LocalTaskDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public TaskService(
            TaskRepository repository,
            TaskHandlerRegistry registry,
            LocalTaskDispatcher dispatcher,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    // 创建任务
    public long createTask(String taskType, JsonNode payload) {

        if (!registry.supports(taskType)) {
            throw new IllegalArgumentException(
                    "Unsupported task type: " + taskType
            );
        }

        try {
            String payloadJson =
                    objectMapper.writeValueAsString(payload);

            // 保存 PENDING 任务
            long taskId = repository.insert(
                    taskType, payloadJson
            );

            // 提交给本地线程池
            dispatcher.dispatch(taskId);

            return taskId;

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid task payload", e
            );
        }
    }

    // 查询任务
    public TaskResponse getTask(long taskId) {

        TaskRecord task = repository.findById(taskId);

        try {
            JsonNode payload =
                    objectMapper.readTree(task.payload());

            JsonNode result = task.result() == null
                    ? null
                    : objectMapper.readTree(task.result());

            return new TaskResponse(
                    task.id(),
                    task.taskType(),
                    payload,
                    task.status(),
                    result,
                    task.errorMessage(),
                    task.createdAt(),
                    task.startedAt(),
                    task.finishedAt()
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Invalid JSON stored in database", e
            );
        }
    }
}

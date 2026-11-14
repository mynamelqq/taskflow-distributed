package com.taskflow.schedulerjava.executor;

import com.taskflow.schedulerjava.domain.TaskRecord;
import com.taskflow.schedulerjava.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TaskRunner {
    private static final Logger log =
            LoggerFactory.getLogger(TaskRunner.class);
    private final TaskRepository repository;
    private final TaskHandlerRegistry registry;
    private final ObjectMapper objectMapper;
    public TaskRunner(
            TaskRepository repository,
            TaskHandlerRegistry registry,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }
    public void run(long taskId) {

        // 1. 尝试领取任务
        int claimed = repository.claimTask(taskId);

        // 领取失败，说明任务已经被其他线程处理
        if (claimed != 1) {
            log.info("Task {} already claimed", taskId);
            return;
        }

        try {
            // 2. 查询任务
            TaskRecord task = repository.findById(taskId);

            // 3. 找到对应的执行器
            TaskHandler handler =
                    registry.getHandler(task.taskType());

            // 4. 解析任务参数
            JsonNode payload =
                    objectMapper.readTree(task.payload());

            // 5. 执行任务
            JsonNode result = handler.execute(payload);

            // 6. 保存执行结果
            String resultJson =
                    objectMapper.writeValueAsString(result);

            int updated = repository.markSucceeded(
                    taskId, resultJson
            );

            if (updated != 1) {
                log.warn("Task {} result update failed", taskId);
            }

            log.info("Task {} completed", taskId);

        } catch (Exception e) {

            log.error("Task {} execution failed", taskId, e);

            // 7. 保存失败信息
            try {
                repository.markFailed(
                        taskId,
                        e.getMessage() == null
                                ? "Unknown error"
                                : e.getMessage()
                );
            } catch (Exception updateException) {
                log.error(
                        "Failed to update task status: {}",
                        taskId,
                        updateException
                );
            }
        }
    }

}

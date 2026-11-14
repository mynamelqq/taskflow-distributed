package com.taskflow.schedulerjava.infrastructure;

import com.taskflow.schedulerjava.executor.TaskRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class LocalTaskDispatcher {

    private final ThreadPoolTaskExecutor executor;
    private final TaskRunner taskRunner;

    public LocalTaskDispatcher(
            ThreadPoolTaskExecutor localTaskExecutor,
            TaskRunner taskRunner) {

        this.executor = localTaskExecutor;
        this.taskRunner = taskRunner;
    }

    public void dispatch(long taskId) {

        executor.execute(() -> taskRunner.run(taskId));
    }
}
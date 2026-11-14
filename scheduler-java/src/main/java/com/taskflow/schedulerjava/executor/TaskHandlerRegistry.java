package com.taskflow.schedulerjava.executor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TaskHandlerRegistry {
    private final Map<String, TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> handlerList) {

        this.handlers = handlerList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TaskHandler::taskType,
                        Function.identity()
                ));
    }
    public TaskHandler getHandler(String taskType) {

        TaskHandler handler = handlers.get(taskType);

        if (handler == null) {
            throw new IllegalArgumentException(
                    "Unsupported task type: " + taskType
            );
        }

        return handler;
    }

    public boolean supports(String taskType) {
        return handlers.containsKey(taskType);
    }
}

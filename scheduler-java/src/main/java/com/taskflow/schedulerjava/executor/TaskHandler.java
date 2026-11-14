package com.taskflow.schedulerjava.executor;

import tools.jackson.databind.JsonNode;

public interface TaskHandler {
    // 当前执行器支持的任务类型
    String taskType();
    // 执行任务
    JsonNode execute(JsonNode payload) throws Exception;

}

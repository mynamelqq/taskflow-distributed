package com.taskflow.schedulerjava;

import com.taskflow.schedulerjava.repository.TaskRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SchedulerJavaApplicationTests {
    @Resource
    TaskRepository repository;
    @Test
    void contextLoads() {
        repository.insert("inner","sadasda");
    }

}

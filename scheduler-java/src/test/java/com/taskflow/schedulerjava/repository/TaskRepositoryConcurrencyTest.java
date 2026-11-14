package com.taskflow.schedulerjava.repository;

import com.taskflow.schedulerjava.domain.TaskRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TaskRepositoryConcurrencyTest {

    @Autowired
    private TaskRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyOneThreadCanClaimTheSameTask() throws Exception {
        // Do not use TaskService here: it dispatches the task as soon as it is created.
        long taskId = repository.insert("DELAY_ECHO", "{\"message\":\"claim test\",\"delayMs\":0}");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Integer> claim = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start claim");
                }
                return repository.claimTask(taskId);
            };

            Future<Integer> first = executor.submit(claim);
            Future<Integer> second = executor.submit(claim);
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both claim threads should be ready");
            start.countDown();

            int firstResult = first.get(10, TimeUnit.SECONDS);
            int secondResult = second.get(10, TimeUnit.SECONDS);
            assertEquals(1, firstResult + secondResult);
            assertTrue((firstResult == 1 && secondResult == 0)
                    || (firstResult == 0 && secondResult == 1));

            TaskRecord task = repository.findById(taskId);
            assertEquals("RUNNING", task.status());
            assertNotNull(task.startedAt());
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM task WHERE id = ?", taskId);
        }
    }
}

package com.taskflow.schedulerjava.repository;

import com.taskflow.schedulerjava.domain.TaskRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class TaskRepository {
    private final JdbcTemplate jdbcTemplate;
    // 1. 创建任务
    public long insert(String taskType, String payload) {

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                INSERT INTO task
                (task_type, payload, status, created_at, updated_at)
                VALUES (?, ?, 'PENDING', NOW(3), NOW(3))
                """, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, taskType);
            ps.setString(2, payload);

            return ps;
        }, keyHolder);//主键持有

        return Objects.requireNonNull(
                keyHolder.getKey()
        ).longValue();
    }
    // 2. 查询任务
    public TaskRecord findById(long id) {

        List<TaskRecord> records = jdbcTemplate.query("""
            SELECT * FROM task WHERE id = ?
            """, (rs, rowNum) -> new TaskRecord(
                rs.getLong("id"),
                rs.getString("task_type"),
                rs.getString("payload"),
                rs.getString("status"),
                rs.getString("result"),
                rs.getString("error_message"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getTimestamp("started_at") == null ? null :
                        rs.getTimestamp("started_at").toLocalDateTime(),
                rs.getTimestamp("finished_at") == null ? null :
                        rs.getTimestamp("finished_at").toLocalDateTime()
        ), id);

        if (records.isEmpty()) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        return records.get(0);
    }
    // 3. 原子领取任务
    public int claimTask(long id) {

        return jdbcTemplate.update("""
            UPDATE task
            SET status = 'RUNNING',
                started_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = ?
              AND status = 'PENDING'
            """, id);
    }
    // 4. 执行成功
    public int markSucceeded(long id, String result) {
        return jdbcTemplate.update("""
            UPDATE task
            SET status = 'SUCCEEDED',
                result = ?,
                finished_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = ?
              AND status = 'RUNNING'
            """, result, id);
    }
    // 5. 执行失败
    public int markFailed(long id, String error) {

        return jdbcTemplate.update("""
            UPDATE task
            SET status = 'FAILED',
                error_message = ?,
                finished_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = ?
              AND status = 'RUNNING'
            """, error, id);
    }
}

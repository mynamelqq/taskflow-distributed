# TaskFlow 第一版任务功能设计

## 1. 范围

第一版仅设计四项能力：创建任务 API、查询任务 API、Java 本地执行器接口、任务状态机。唯一支持的任务类型是 `DELAY_ECHO`。目标行为是接收任务、持久化为 `PENDING`、由本地调度器领取并执行，约 `delayMs` 后保存回显结果。

今天只定义 API 和接口/状态规则，不实现数据库访问、后台轮询调度、执行器或前端。暂不引入分布式锁、Redis、消息队列、Go、多节点协调和自动重试。

## 2. 任务模型

表 `task` 是任务当前状态的权威记录。字段沿用现有表设计：

| 字段 | 用途 |
| --- | --- |
| `id` | 自增任务 ID |
| `task_type` | 任务类型，第一版为 `DELAY_ECHO` |
| `payload` | 类型对应的 JSON 输入 |
| `status` | `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` 之一 |
| `result` | 成功结果 JSON；未成功时为空 |
| `error_message` | 失败摘要；成功时为空 |
| `created_at` / `updated_at` | 创建和最近更新时间 |
| `started_at` / `finished_at` | 开始和结束时间；未发生时为空 |

时间使用 UTC 持久化，API 使用 ISO-8601 格式并带时区偏移。`payload` 与 `result` 的 JSON 结构由任务类型定义，不把不可信的任意对象反序列化为多态 Java 类型。

## 3. 创建任务

### `POST /api/tasks`

请求：

```json
{
  "taskType": "DELAY_ECHO",
  "payload": {
    "message": "Hello TaskFlow",
    "delayMs": 2000
  }
}
```

校验规则：

- `taskType` 必填，第一版只接受 `DELAY_ECHO`。
- `payload.message` 必须是字符串。
- `payload.delayMs` 必须是非负整数；第一版建议限制为 `0..60000` 毫秒，避免单个演示任务长时间占用执行线程。
- 缺字段、类型错误、未知任务类型或超出范围返回 `400 Bad Request`，响应包含稳定的错误码和可读说明。

创建行为：服务端写入原始 payload，设置 `status=PENDING`、`result=null`、`error_message=null`、`created_at=updated_at=now`，其余时间字段为空。新任务不得直接创建为 `RUNNING` 或终态。

成功响应：`201 Created`，返回任务资源和 `Location: /api/tasks/{id}`。

```json
{
  "id": 123,
  "taskType": "DELAY_ECHO",
  "payload": { "message": "Hello TaskFlow", "delayMs": 2000 },
  "status": "PENDING",
  "result": null,
  "errorMessage": null,
  "createdAt": "2026-11-14T07:00:00Z",
  "updatedAt": "2026-11-14T07:00:00Z",
  "startedAt": null,
  "finishedAt": null
}
```

此接口只表示任务已成功保存，不保证响应时已经开始执行。客户端可凭返回的 `id` 查询状态。

## 4. 查询任务

### `GET /api/tasks/{id}`

根据 ID 返回任务资源，字段与创建成功响应相同。存在时返回 `200 OK`；ID 格式非法返回 `400`，任务不存在返回 `404 Not Found`。查询为只读行为，不触发执行、不修改状态。

状态轮询由调用方决定频率；MVP 可以每秒查询一次，直到 `SUCCEEDED` 或 `FAILED`。终态任务返回持久化的 `result` 或 `errorMessage`。

## 5. 状态机

```text
                 成功执行
PENDING ──领取成功──> RUNNING ─────────> SUCCEEDED
                              └─执行失败─> FAILED
```

允许的转换只有 `PENDING -> RUNNING`、`RUNNING -> SUCCEEDED` 和 `RUNNING -> FAILED`。`SUCCEEDED`、`FAILED` 是终态；第一版不自动重试，也不提供任意状态修改接口。

状态变更由任务仓储/状态服务的专用操作负责，业务处理器不能直接设置状态。领取任务必须通过条件更新保证只有一个执行者成功：

```sql
UPDATE task
SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP(3),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = ? AND status = 'PENDING';
```

只有受影响行数为 `1` 的调用者获得执行权；为 `0` 表示任务已被领取或状态已变化，调用者不得执行。`RUNNING -> SUCCEEDED/FAILED` 同样带 `WHERE id = ? AND status = 'RUNNING'` 条件，并写入相应结果/错误摘要、`finished_at` 和 `updated_at`。不要用先查后改替代条件更新。

本地单进程 MVP 仍使用上述条件更新，使领取规则明确且为后续多实例演进留好边界；这不等于实现了分布式锁或完整的多实例调度保证。

## 6. Java 本地执行器接口

建议用处理器接口隔离任务类型与调度器：

```java
public interface TaskHandler<P, R> {
    String taskType();
    R execute(P payload) throws Exception;
}
```

`DELAY_ECHO` 的类型化数据：

```java
public record DelayEchoPayload(String message, long delayMs) {}
public record DelayEchoResult(String message) {}
```

处理器实现应校验/接收已校验的 payload，等待 `delayMs`，然后返回原消息作为结果。线程被中断时应恢复中断标记并按执行失败处理；不要吞掉异常。

调度器职责（本阶段只定义）：周期性查找旧到新的 `PENDING` 任务；按 `task_type` 找到对应处理器；先通过条件更新领取；领取成功后调用处理器；成功则保存结果并转到 `SUCCEEDED`，异常则保存精简错误摘要并转到 `FAILED`。调度器不得绕过状态服务直接任意更新状态。数据库 I/O 不应占住等待 `delayMs` 的执行线程；实现时应使用独立、有界的执行线程池，避免无界线程增长。

`TaskHandler` 只承载任务业务执行；任务领取、持久化、状态转换和异常落库由调度器/应用服务协调。后续新增任务类型时增加处理器，不改变状态机。

## 7. `DELAY_ECHO` 结果与失败

成功结果：

```json
{ "message": "Hello TaskFlow" }
```

`delayMs=2000` 表示处理器从开始执行起等待约两秒。实际完成时间还会包含轮询间隔、线程排队和数据库写入时间，因此不是精确的定时保证。

执行异常时状态变为 `FAILED`，`result` 保持空，`error_message` 存简短且不含敏感信息的诊断文本。失败任务不重试；运维或客户端不得通过普通 API 把终态任务改回 `PENDING`。

## 8. 第一版边界与后续实现顺序

实现时依次落地：任务状态/DTO 与 payload 校验 → 创建和查询持久化 → 条件领取及状态更新 → `DELAY_ECHO` 处理器 → 本地定时扫描与有界线程池。数据库表需先建好并配置 MySQL 连接。单机进程重启后，残留 `RUNNING` 任务如何恢复暂不定义；自动重试和尝试历史应在需要时再设计，避免第一版把失败状态语义复杂化。

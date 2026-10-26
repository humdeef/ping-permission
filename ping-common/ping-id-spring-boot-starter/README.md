# ping-id-spring-boot-starter：分布式 ID

这个 starter 内置雪花算法（Snowflake），提供 `DistributedIdGenerator` 的实现，不依赖任何外部 ID 组件（不依赖 Redis、数据库号段、ZooKeeper）。

## 1. 包含什么

| 类 | 作用 |
| --- | --- |
| `DistributedIdAutoConfiguration` | 自动装配 `DistributedIdGenerator` |
| `PingIdProperties` | `ping.id.*` 配置 |
| `SnowflakeIdGenerator` | 64 位雪花算法实现 |

依赖：`ping-core`、`spring-boot-autoconfigure`、`slf4j-api`。

## 2. 配置

```yaml
ping:
  id:
    worker-id: -1       # 0-31；-1 表示自动推导
    datacenter-id: -1   # 0-31；-1 表示默认 0
    epoch: 1704067200000  # 起始时间戳，默认 2024-01-01T00:00:00Z
```

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `worker-id` | `-1` | 工作节点 ID，范围 0~31；`-1` 自动推导 |
| `datacenter-id` | `-1` | 数据中心 ID，范围 0~31；`-1` 当作 0 |
| `epoch` | `1704067200000` | 起始时间，必须是过去的时间戳 |

### 自动推导 workerId

未配置时：

```
seed = 主机名 + "|" + spring.application.name + "|" + PID
workerId = floorMod(seed.hashCode(), 32)
```

失败时退化为 `floorMod(PID, 32)`。

这样做是为了让同一主机上的多个服务/实例尽量拿到不同的 workerId，但**不保证唯一**。

## 3. 使用方式

```java
@RequiredArgsConstructor
@Service
public class AppAuthServiceImpl implements AppAuthService {

    private final DistributedIdGenerator idGenerator;

    public AppUser register(...) {
        AppUser user = new AppUser();
        user.setId(Long.valueOf(idGenerator.nextId()));  // 注意：接口返回 String
        ...
    }
}
```

接口定义：

```java
@FunctionalInterface
public interface DistributedIdGenerator {
    String nextId();
}
```

为什么返回 `String` 而不是 `long`？

- 雪花 ID 是 19 位，超过 JavaScript 的 `Number.MAX_SAFE_INTEGER`（2^53-1）；
- 直接返回 `long` 给前端会丢精度；
- 需要数值时自己 `Long.valueOf(...)`。

`SnowflakeIdGenerator` 另外提供了 `public synchronized long nextLong()`；如果你确定只在 Java 内部使用，可以强转实现类或注入具体类型。

## 4. 64 位结构

```
0 | 41 bit 时间戳（相对 epoch） | 5 bit 数据中心 | 5 bit 工作节点 | 12 bit 序列号
```

| 部分 | 位数 | 能力 |
| --- | --- | --- |
| 时间戳 | 41 | 约 69 年（从 epoch 起） |
| 数据中心 | 5 | 0~31 |
| 工作节点 | 5 | 0~31 |
| 序列号 | 12 | 单节点每毫秒 4096 个 |

- 单节点每毫秒最多 4096 个 ID；
- 序列号用尽时自旋等待下一毫秒；
- 时钟回拨 ≤ 5ms：等待恢复；
- 时钟回拨 > 5ms：抛 `IllegalStateException`，拒绝生成，避免重复 ID。

## 5. 常见坑

### 5.1 生产环境依赖自动推导

自动推导只有 32 个桶，多实例部署时碰撞概率不低（8 个实例时约 58%）。**生产环境务必显式配置唯一的 `worker-id`**，例如通过环境变量注入：

```yaml
ping:
  id:
    worker-id: ${PING_WORKER_ID:0}
    datacenter-id: ${PING_DATACENTER_ID:0}
```

容器编排时按实例序号注入（StatefulSet ordinal、部署脚本序号等）。

### 5.2 修改 `epoch`

`epoch` 是 ID 结构的一部分。修改后：

- 新旧 ID 无法按时间排序；
- 极端情况下可能与历史 ID 重复；
- 已经存储的 ID 不会改变。

上线后不要改 `epoch`。如果必须改，应视为一次 ID 体系迁移。

### 5.3 时钟回拨

NTP 校时、虚拟机暂停、容器迁移都可能导致时钟回拨：

- 回拨 ≤ 5ms：自旋等待；
- 回拨 > 5ms：抛异常。

如果业务不能接受抛异常，需要在调用侧捕获并降级（例如返回错误、重试、使用数据库自增兜底），而不是修改算法忽略回拨。

### 5.4 单机多服务 workerId 冲突

自动推导的 seed 包含主机名、应用名、PID，同一台机器上的不同服务通常会得到不同值，但哈希只有 32 个桶，仍可能冲突。多服务同机部署时，显式配置不同的 `worker-id`。

### 5.5 把 ID 当业务主键又要求连续

雪花 ID 趋势递增，但**不连续**，也不保证严格按业务时间排序（多节点时钟差异）。不要用它实现：

- 订单号“连续递增”；
- 按 ID 分页的稳定排序（同一毫秒内多节点顺序不确定）。

需要强连续时用数据库序列/号段，雪花 ID 只适合唯一性要求。

### 5.6 前端精度丢失

```json
{"id": 1845321092837465600}
```

JavaScript 解析后可能变成 `1845321092837465601`。对外 DTO 用 `String`：

```java
private String id;
```

本仓库的 `AppLoginResponse.userId` / `AppUserResponse.userId` 已经这样处理。

### 5.7 `epoch` 在未来

构造函数会校验：

```java
if (epoch <= 0 || epoch > System.currentTimeMillis()) {
    throw new IllegalArgumentException("epoch must be a positive past timestamp");
}
```

容器时间不对（例如时区/时钟未同步）时，应用可能启动失败。先检查宿主机时间。

## 6. 覆盖默认实现

```java
@Bean
@Primary
public DistributedIdGenerator myIdGenerator() {
    return () -> "biz-" + UUID.randomUUID();
}
```

自动配置的 Bean 带 `@ConditionalOnMissingBean`，你的 Bean 会生效。但要注意：`ping-log` 的 `bizTid` 也会优先使用 `DistributedIdGenerator`，替换后链路 ID 的格式会一起改变。

## 7. 依赖方式

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-id-spring-boot-starter</artifactId>
</dependency>
```

适用：任何需要生成全局唯一 ID 的 servlet 服务。网关目前不生成业务 ID，可以不引。

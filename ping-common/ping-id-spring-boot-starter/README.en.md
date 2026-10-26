# ping-id-spring-boot-starter: Distributed ID

This starter bundles the Snowflake algorithm and provides an implementation of `DistributedIdGenerator`, without depending on any external ID component (no Redis, database number segments, or ZooKeeper).

## 1. What's included

| Class | Purpose |
| --- | --- |
| `DistributedIdAutoConfiguration` | Auto-wires `DistributedIdGenerator` |
| `PingIdProperties` | `ping.id.*` configuration |
| `SnowflakeIdGenerator` | 64-bit Snowflake implementation |

Dependencies: `ping-core`, `spring-boot-autoconfigure`, `slf4j-api`.

## 2. Configuration

```yaml
ping:
  id:
    worker-id: -1       # 0-31; -1 means auto-derive
    datacenter-id: -1   # 0-31; -1 means default 0
    epoch: 1704067200000  # Start timestamp, defaults to 2024-01-01T00:00:00Z
```

| Configuration | Default | Description |
| --- | --- | --- |
| `worker-id` | `-1` | Worker node ID, range 0~31; `-1` auto-derives |
| `datacenter-id` | `-1` | Data center ID, range 0~31; `-1` is treated as 0 |
| `epoch` | `1704067200000` | Start time, must be a past timestamp |

### Auto-deriving workerId

When not configured:

```
seed = hostname + "|" + spring.application.name + "|" + PID
workerId = floorMod(seed.hashCode(), 32)
```

On failure, it falls back to `floorMod(PID, 32)`.

This is done so that multiple services/instances on the same host get different workerIds as much as possible, but **uniqueness is not guaranteed**.

## 3. Usage

```java
@RequiredArgsConstructor
@Service
public class AppAuthServiceImpl implements AppAuthService {

    private final DistributedIdGenerator idGenerator;

    public AppUser register(...) {
        AppUser user = new AppUser();
        user.setId(Long.valueOf(idGenerator.nextId()));  // Note: the interface returns String
        ...
    }
}
```

Interface definition:

```java
@FunctionalInterface
public interface DistributedIdGenerator {
    String nextId();
}
```

Why does it return `String` instead of `long`?

- Snowflake IDs are 19 digits, exceeding JavaScript's `Number.MAX_SAFE_INTEGER` (2^53-1);
- Returning `long` directly to the frontend loses precision;
- Call `Long.valueOf(...)` yourself when you need a numeric value.

`SnowflakeIdGenerator` additionally provides `public synchronized long nextLong()`; if you are sure it is used only inside Java, you can cast the implementation class or inject the concrete type.

## 4. 64-bit layout

```
0 | 41 bit timestamp (relative to epoch) | 5 bit data center | 5 bit worker node | 12 bit sequence
```

| Part | Bits | Capacity |
| --- | --- | --- |
| Timestamp | 41 | About 69 years (from epoch) |
| Data center | 5 | 0~31 |
| Worker node | 5 | 0~31 |
| Sequence | 12 | 4096 per millisecond per node |

- At most 4096 IDs per millisecond per node;
- When the sequence is exhausted, spin-wait for the next millisecond;
- Clock rollback ≤ 5ms: wait for recovery;
- Clock rollback > 5ms: throw `IllegalStateException` and refuse to generate, avoiding duplicate IDs.

## 5. Pitfalls

### 5.1 Relying on auto-derivation in production

Auto-derivation has only 32 buckets, so the collision probability is not low when deploying multiple instances (about 58% with 8 instances). **In production, always configure a unique `worker-id` explicitly**, e.g. injected via environment variables:

```yaml
ping:
  id:
    worker-id: ${PING_WORKER_ID:0}
    datacenter-id: ${PING_DATACENTER_ID:0}
```

Inject by instance ordinal during container orchestration (StatefulSet ordinal, deployment script ordinal, etc.).

### 5.2 Changing `epoch`

`epoch` is part of the ID structure. After changing it:

- Old and new IDs cannot be sorted by time;
- In extreme cases, they may duplicate historical IDs;
- Already stored IDs do not change.

Do not change `epoch` after going live. If you must, treat it as a migration of the ID system.

### 5.3 Clock rollback

NTP time synchronization, VM suspension, and container migration can all cause clock rollback:

- Rollback ≤ 5ms: spin-wait;
- Rollback > 5ms: throw an exception.

If the business cannot accept the exception, catch it on the caller side and degrade (e.g. return an error, retry, fall back to a database auto-increment) rather than modifying the algorithm to ignore rollback.

### 5.4 workerId conflicts for multiple services on one host

The auto-derived seed includes the hostname, application name, and PID; different services on the same machine usually get different values, but the hash has only 32 buckets, so conflicts are still possible. When deploying multiple services on the same host, configure different `worker-id` values explicitly.

### 5.5 Using IDs as business primary keys while requiring continuity

Snowflake IDs are trend-increasing but **not continuous**, and they do not guarantee strict ordering by business time (due to clock differences across nodes). Do not use them to implement:

- "Continuously increasing" order numbers;
- Stable sorting by ID pagination (the order of multiple nodes within the same millisecond is indeterminate).

When strong continuity is required, use database sequences/number segments; Snowflake IDs are only suitable for uniqueness requirements.

### 5.6 Frontend precision loss

```json
{"id": 1845321092837465600}
```

After JavaScript parses it, it may become `1845321092837465601`. Use `String` in external DTOs:

```java
private String id;
```

`AppLoginResponse.userId` / `AppUserResponse.userId` in this repository already do this.

### 5.7 `epoch` in the future

The constructor validates:

```java
if (epoch <= 0 || epoch > System.currentTimeMillis()) {
    throw new IllegalArgumentException("epoch must be a positive past timestamp");
}
```

When the container time is wrong (e.g. timezone/clock not synchronized), the application may fail to start. Check the host time first.

## 6. Overriding the default implementation

```java
@Bean
@Primary
public DistributedIdGenerator myIdGenerator() {
    return () -> "biz-" + UUID.randomUUID();
}
```

The auto-configured bean carries `@ConditionalOnMissingBean`, so your bean takes effect. Note, however, that `ping-log`'s `bizTid` also prefers `DistributedIdGenerator`; after replacement, the format of trace IDs changes accordingly.

## 7. Adding the dependency

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-id-spring-boot-starter</artifactId>
</dependency>
```

Applicable to: any servlet service that needs to generate globally unique IDs. The gateway currently does not generate business IDs, so it can omit this.

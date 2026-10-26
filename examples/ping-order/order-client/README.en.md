# order-client: Order domain external Dubbo contract

`order-client` is the API package that the order service exposes to other services; it contains only interfaces and DTOs.

## 1. What it contains

```java
public interface OrderDubboClient {
    OrderDTO getOrderById(Long id);
}
```

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDTO implements Serializable {
    private Integer id;
    private String traNo;
}
```

Dependencies: Lombok only.

## 2. Provider

The implementation in `order-service`:

```java
@DubboService
public class OrderDubboServiceImpl implements OrderDubboClient {

    @Override
    public OrderDTO getOrderById(Long id) {
        return new OrderDTO(Math.toIntExact(id), "trade-" + id);
    }
}
```

The startup class of `order-service` enables `@EnableDubbo` and registers in the Nacos configuration:

```yaml
dubbo:
  application:
    name: dubbo-order-service
  protocol:
    name: tri
    port: 20881
```

## 3. Consumer

```xml
<dependency>
    <groupId>com.ping.order</groupId>
    <artifactId>order-client</artifactId>
</dependency>
```

```java
@DubboReference(check = false, timeout = 3000)
private OrderDubboClient orderDubboClient;

OrderDTO order = orderDubboClient.getOrderById(1L);
```

## 4. Pitfalls

### 4.1 DTO does not implement `Serializable`

Dubbo transport objects need to be serializable and retain a no-arg constructor. `OrderDTO` has both.

### 4.2 Contract package depends on the implementation package in reverse

Do not let `order-client` depend on `order-service`. Consumers only need interfaces and DTOs; they should not be dragged into Spring MVC, Sa-Token, and database implementations.

### 4.3 Referencing order-client in the gateway

The gateway does not currently need order capabilities. If it does in the future, note that the gateway is WebFlux; consumers only need to depend on the contract package, not `order-service`.

### 4.4 Changing interface method signatures

Adding/removing methods is a breaking change. During canary releases, upgrade providers first, then consumers; or add a new interface version.

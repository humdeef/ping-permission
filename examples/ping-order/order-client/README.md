# order-client：订单域对外 Dubbo 契约

`order-client` 是订单服务暴露给其他服务的 API 包，只包含接口和 DTO。

## 1. 包含什么

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

依赖：仅 Lombok。

## 2. 提供方

`order-service` 中的实现：

```java
@DubboService
public class OrderDubboServiceImpl implements OrderDubboClient {

    @Override
    public OrderDTO getOrderById(Long id) {
        return new OrderDTO(Math.toIntExact(id), "trade-" + id);
    }
}
```

`order-service` 的启动类开启了 `@EnableDubbo`，并在 Nacos 配置中注册：

```yaml
dubbo:
  application:
    name: dubbo-order-service
  protocol:
    name: tri
    port: 20881
```

## 3. 消费方

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

## 4. 常见坑

### 4.1 DTO 不实现 `Serializable`

Dubbo 传输对象需要可序列化，并保留无参构造器。`OrderDTO` 两者都有。

### 4.2 契约包反向依赖实现包

不要让 `order-client` 依赖 `order-service`。消费方只需要接口和 DTO，不应被拖入 Spring MVC、Sa-Token、数据库实现。

### 4.3 在网关里引用 order-client

当前网关不需要订单能力。如果未来需要，注意网关是 WebFlux，消费方只依赖契约包即可，不要引入 `order-service`。

### 4.4 修改接口方法签名

新增/删除方法属于不兼容变更。灰度发布时先升级提供方，再升级消费方；或新增接口版本。

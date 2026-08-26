# Java `HttpClient` 使用指南

> 读者：具备基础 Java 编程经验的开发者  
> 目标：使用 JDK 内置 `java.net.http.HttpClient` 完成同步、异步、JSON 请求、超时与错误处理。  
> 适用版本：Java 17（本项目当前配置）

## 范围

本文讲解 Java 11 起内置的 `HttpClient`，不需要额外引入 Apache HttpClient 依赖。重点是应用服务调用 HTTP API 的常用场景；不覆盖复杂连接池调优、mTLS 或完整的重试框架。

## 目录

- [核心模型](#核心模型)
- [企业项目中的推荐组织方式](#企业项目中的推荐组织方式)
- [发起同步请求](#发起同步请求)
- [发送 JSON POST 请求](#发送-json-post-请求)
- [异步请求](#异步请求)
- [超时、状态码与异常处理](#超时状态码与异常处理)
- [选择与实践建议](#选择与实践建议)

## 核心模型

一次调用由三个对象组成：

```text
HttpClient  →  负责发送请求，可复用
HttpRequest →  描述 URL、方法、请求头和请求体
HttpResponse<T> → 包含状态码、响应头和解析后的响应体
```

推荐将 `HttpClient` 定义为单例 Bean 或静态成员并复用；每次请求只新建 `HttpRequest`。前面的示例便于理解 API，实际企业项目通常还需要集中管理下游地址、超时、鉴权、错误转换与日志。

## 企业项目中的推荐组织方式

企业项目不应让 Controller 或业务服务直接拼接 URL、序列化 JSON 和判断状态码。更容易维护的边界如下：

```text
Controller / Service
        ↓ 调用业务方法
InventoryClient（下游接口的本地门面）
        ↓ DTO 序列化、请求构造、错误转换
HttpClient + ObjectMapper
        ↓
库存服务
```

一个下游服务对应一个专用 Client，例如 `InventoryClient`、`PaymentClient`。业务代码只处理领域 DTO 和业务异常，不依赖 URL、HTTP 请求头或 JSON 字符串。这能让下游协议变化集中在 Client 内部，并便于单独测试。

### 1. 将地址与超时外置到配置

不要把下游 URL 和超时硬编码在 Java 类中。以下配置将库存服务的地址和超时按环境区分；生产环境可通过环境变量或配置中心覆盖这些值。

```yaml
# application.yml
clients:
  inventory:
    base-url: https://inventory.example.com
    connect-timeout: 2s
    request-timeout: 5s
```

> 示例状态：说明性片段。需要项目启用 Spring Boot 的配置属性绑定。

```java
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "clients.inventory")
public record InventoryClientProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration requestTimeout) {
}
```

敏感信息（如 API Token、用户名和证书路径）也应使用环境变量、密钥管理服务或配置中心注入，不能提交到 `application.yml` 或日志中。

### 2. 以 Spring Bean 集中创建并复用 `HttpClient`

将连接相关配置放在 `HttpClient` 上；请求超时仍由每个 `HttpRequest` 指定。每个下游可拥有独立的 `HttpClient` Bean，以便使用不同的连接超时、代理或认证策略。

> 示例状态：说明性片段。

```java
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(InventoryClientProperties.class)
public class InventoryHttpClientConfiguration {

    @Bean
    public HttpClient inventoryHttpClient(InventoryClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }
}
```

如果多个下游的连接策略相同，也可以共用一个 `HttpClient` Bean；但 URL、每个请求的超时、业务请求头与错误规则仍应归各自的 Client 管理。

### 3. 在专用 Client 中封装协议细节

下面的 Client 将 URL 拼接、状态码判断、JSON 反序列化和异常转换收敛在一个地方。调用方只会得到 `InventoryResponse` 或业务可识别的 `DownstreamServiceException`。

> 示例状态：说明性片段。为说明结构省略了日志与认证请求头；响应 JSON 的字段需与 `InventoryResponse` 对应。

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class InventoryClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final InventoryClientProperties properties;

    public InventoryClient(
            HttpClient inventoryHttpClient,
            ObjectMapper objectMapper,
            InventoryClientProperties properties) {
        this.httpClient = inventoryHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public InventoryResponse getInventory(String sku) {
        URI uri = properties.baseUrl().resolve("/api/inventories/" + sku);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(properties.requestTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            ensureSuccess(response);
            return objectMapper.readValue(response.body(), InventoryResponse.class);
        } catch (IOException exception) {
            throw new DownstreamServiceException("库存服务调用失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DownstreamServiceException("库存服务调用被中断", exception);
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new DownstreamServiceException(
                    "库存服务返回 HTTP " + response.statusCode()
            );
        }
    }

    public record InventoryResponse(String sku, Integer availableQuantity) {
    }

    public static class DownstreamServiceException extends RuntimeException {
        public DownstreamServiceException(String message) {
            super(message);
        }

        public DownstreamServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

> 注意：若项目中存在多个 `HttpClient` Bean，应使用 `@Qualifier("inventoryHttpClient")` 标注构造器参数，避免 Spring 无法判断注入哪一个 Bean。

### 4. 明确失败处理、重试和可观测性边界

`HttpClient` 负责发送 HTTP 请求，但不提供通用的业务重试、熔断或指标策略。企业项目通常在 Client 外围统一这些规则：

| 场景 | Client 的处理 | 常见外围策略 |
|---|---|---|
| 2xx 响应 | 反序列化为 DTO | 正常返回 |
| 4xx 响应 | 转换为可识别的业务/下游异常 | 通常不重试；根据业务提示或降级 |
| 5xx、连接失败、超时 | 记录下游名、状态码和耗时后抛出异常 | 仅对幂等操作做有限重试；必要时熔断、降级 |
| 鉴权失败 | 不记录 Token，保留脱敏后的上下文 | 刷新凭据或告警，不盲目重试 |

建议至少记录请求方法、下游服务名、路径（不含敏感查询参数）、状态码、耗时和异常类型。对于链路追踪，可将当前 trace ID 透传到下游请求头；对于重试、限流和熔断，可采用项目已选定的治理组件，但必须为 `POST` 等非幂等操作定义明确的幂等键或禁止自动重试。

### 5. 为 Client 写独立测试

优先测试 Client 的可观察行为，而不是只测试 `HttpRequest` 是否被创建：

- 成功响应能正确反序列化为 DTO。
- 4xx 和 5xx 响应会转换为预期异常。
- 网络异常和超时会保留原因，并正确中断处理。
- 必需的请求头、路径与超时配置实际发送。

测试可使用本地 Mock HTTP Server 或 MockWebServer，避免依赖真实下游服务。这样既能验证请求内容，也能稳定模拟超时、错误码和异常响应。

## 发起同步请求

下面是一个读取文本响应的完整方法。

> 示例状态：说明性片段。可放入任意 Java 类中调用。

```java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientExample {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String getUser(Long userId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.example.com/users/" + userId))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() / 100 != 2) {
            throw new IOException("请求失败，HTTP 状态码：" + response.statusCode());
        }

        return response.body();
    }
}
```

`BodyHandlers.ofString()` 将响应体读为字符串，适用于 JSON、HTML 或普通文本。对于下载文件，可改用 `BodyHandlers.ofFile(path)`。

## 发送 JSON POST 请求

`HttpClient` 不会自动将 Java 对象转换为 JSON；`BodyPublishers.ofString(...)` 接收的只能是 `String`。在 Spring Boot 项目中，`spring-boot-starter-webmvc` 会提供 Jackson 支持，并自动配置 `ObjectMapper` Bean。通常由调用方先创建请求 DTO，再用 `ObjectMapper.writeValueAsString(...)` 得到 JSON 字符串，最后将它作为请求体发送。

> 示例状态：说明性片段。

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CreateOrderClient {

    // 默认配置，等价于 HttpClient.newBuilder().build()
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public CreateOrderClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createOrder(CreateOrderRequest createOrderRequest)
            throws IOException, InterruptedException {

        String requestJson = toJson(createOrderRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.example.com/orders"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 201) {
            throw new IOException(
                    "创建订单失败，状态码=" + response.statusCode()
                            + "，响应=" + response.body()
            );
        }

        return response.body();
    }

    private String toJson(CreateOrderRequest createOrderRequest)
            throws JsonProcessingException {
        return objectMapper.writeValueAsString(createOrderRequest);
    }

    public record CreateOrderRequest(String productId, Integer quantity) {
    }
}
```

常用请求体发布器：

| 场景           | `BodyPublisher`                   |
| -------------- | --------------------------------- |
| 无请求体       | `BodyPublishers.noBody()`         |
| JSON、表单文本 | `BodyPublishers.ofString(...)`    |
| 字节数组       | `BodyPublishers.ofByteArray(...)` |
| 文件上传       | `BodyPublishers.ofFile(path)`     |

## 异步请求

`sendAsync` 不会阻塞当前线程，而是立即返回 `CompletableFuture`。HTTP 请求会在后台继续执行，调用方可在结果完成后通过 `thenApply`、`thenAccept` 或 `exceptionally` 处理结果和异常。

> 示例状态：说明性片段。

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class AsyncHttpClientExample {

    private final HttpClient client = HttpClient.newHttpClient();

    public CompletableFuture<String> getProductAsync(Long productId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.example.com/products/" + productId))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new IllegalStateException(
                                "查询商品失败，状态码：" + response.statusCode()
                        );
                    }
                    return response.body();
                });
    }
}
```

调用方可以继续组合结果：

```java
getProductAsync(1001L)
        .thenAccept(System.out::println)
        .exceptionally(error -> {
            error.printStackTrace();
            return null;
        });
```

### `send` 与 `sendAsync`：差别是等待方式，不是“能否发送多个请求”

`send` 会阻塞**调用它的线程**，直到响应到达或请求失败。因此，在同一个线程中顺序调用两次 `send` 时，第二次请求必须等待第一次结束；但如果由多个线程分别调用 `send`，这些请求同样可以并发进行。`HttpClient` 本身可以复用，并不限制为“一次只能发一个请求”。

`sendAsync` 会立即返回 `CompletableFuture`，调用线程无需等待，因而一个线程就能快速提交多个请求，再在所有结果完成后统一处理。对于 Web Controller，这能避免业务线程把时间消耗在等待下游响应上；但它不等于取消线程、连接或网络资源的消耗。

| 调用方式 | 调用线程行为 | 同一线程提交多个请求 | 常见使用场景 |
|---|---|---|---|
| `send(...)` | 等待当前请求完成 | 顺序执行；如需并发，应由多个线程调度 | 业务流程简单、必须立即取得结果 |
| `sendAsync(...)` | 立即取得 `CompletableFuture` | 可快速提交，再组合多个 Future | 聚合多个下游接口、异步业务链路 |

以下示例并发查询商品、库存和价格。三个请求会先被提交，`CompletableFuture.allOf(...)` 再等待它们全部完成：

> 示例状态：说明性片段。假设 `getProductAsync`、`getStockAsync` 和 `getPriceAsync` 均返回 `CompletableFuture<String>`。

```java
CompletableFuture<String> productFuture = getProductAsync(1001L);
CompletableFuture<String> stockFuture = getStockAsync(1001L);
CompletableFuture<String> priceFuture = getPriceAsync(1001L);

CompletableFuture.allOf(productFuture, stockFuture, priceFuture)
        .thenRun(() -> {
            String product = productFuture.join();
            String stock = stockFuture.join();
            String price = priceFuture.join();
            System.out.println(product + stock + price);
        })
        .exceptionally(error -> {
            error.printStackTrace();
            return null;
        });
```

异步并不意味着自动成功：网络错误会使 `CompletableFuture` 以异常状态完成，非 2xx 状态码仍需像示例中一样主动判断。也应限制并发数量，避免在高流量下同时向下游发起过多请求。

## 超时、状态码与异常处理

`HttpClient` 的两个超时覆盖不同阶段。一次 HTTP 调用通常经历“建立连接 → 发送请求 → 服务端处理 → 接收响应”的过程：

```text
开始请求 ── 建立 TCP/TLS 连接 ── 发送请求 ── 等待服务端处理 ── 接收响应
              ↑ connectTimeout                 ←──── request.timeout ────→
```

| 配置                   | 含义                                            |
| ---------------------- | ----------------------------------------------- |
| `connectTimeout(...)`  | 从尝试建立网络连接到连接成功的最长等待时间，配置在 `HttpClient` 上；连接已被复用时通常不会触发它。 |
| `request.timeout(...)` | 单次请求从发出到获得响应的最长允许时间，配置在 `HttpRequest` 上；它覆盖连接、服务端处理和响应等待。 |

例如，`connectTimeout(5 秒)` 用于避免目标地址不可达时长时间卡在建连阶段；`request.timeout(10 秒)` 用于避免服务端已连接却迟迟不返回结果。两者应同时设置：只设置连接超时，无法限制慢接口；只设置请求超时，则无法针对建连问题设置更短边界。

超时发生时，`send(...)` 会抛出异常；`sendAsync(...)` 返回的 `CompletableFuture` 会以异常状态完成。实际代码应记录下游名称、耗时和异常类型，并依据操作是否幂等决定是否重试。

实践中建议：

- 所有外部调用都配置连接超时和请求超时。
- 先检查 `statusCode()`，再解析业务 JSON。
- 不要无条件重试非幂等请求，如创建订单、扣款等 `POST` 操作。
- 记录下游服务、URL 路径、耗时和状态码；避免把 `Authorization` 等敏感请求头写入日志。
- 把 HTTP 调用封装在专门的 Client 类中，避免 Controller 或业务服务到处拼接 URL。

## 选择与实践建议

对于本项目的 Java 17 环境，JDK `HttpClient` 适用于常规服务间 HTTP 调用：

| 需求                             | 建议                                    |
| -------------------------------- | --------------------------------------- |
| 少量同步或异步 REST 调用         | 使用 JDK `HttpClient`                   |
| Spring 应用中的声明式接口调用    | 评估 Spring HTTP Interface 或 OpenFeign |
| 高并发、响应式调用链             | 评估 Spring `WebClient`                 |
| 既有项目大量依赖 Apache 工具生态 | 继续使用 Apache HttpClient，并统一封装  |

核心原则是：复用 `HttpClient`、为请求设定边界（超时与状态码）、将序列化和异常转换集中在客户端封装层。

## 参考资料

- [Java 17 HttpClient API](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html)
- [Java 17 HttpRequest API](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpRequest.html)
- [Java 17 HttpResponse API](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpResponse.html)

## 验证摘要

- 结构：按用户要求，未运行结构校验器。
- 代码：示例按 Java 17 API 审核，未执行实际网络请求。
- 来源：JDK 17 官方 API 文档。
- 未验证：示例中的 `api.example.com` 为占位下游地址，未进行真实连通性、认证和业务响应测试。

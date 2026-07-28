# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run with dev profile (active by default, port 9001, context-path /api)
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=JavaDocApplicationTests

# Run a specific test method
./mvnw test -Dtest=JavaDocApplicationTests#contextLoads
```

## Architecture

Spring Boot 4.0.6 REST API for cookbook/menu management, migrated 1:1 from a Nest.js + Mongoose project. Java 17, MongoDB, Lombok.

**Package layout:**

```
com.example.javadoc
├── config/          # CORS, static resource mapping
├── core/advice/     # GlobalResponseBodyAdvice (wraps all responses), GlobalExceptionHandler
└── module/
    ├── market/      # controller, service (MarketService + FoodService), dto/, entity/
    └── order/       # controller, service, dto/, entity/
```

**Response format:** `GlobalResponseBodyAdvice` wraps all `@ResponseBody` returns as `{code: 200, message: "success", data: <body>}`. `GlobalExceptionHandler` returns `{statusCode, timestamp, path, message}` on errors.

**API prefix:** All endpoints under `/api` via `server.servlet.context-path=/api`.

## Key Conventions

- **MongoDB config uses `spring.mongodb.uri`** — Spring Boot 4.x changed the prefix from `spring.data.mongodb`. Using the old prefix silently falls back to connecting to the `test` database.
- **Collection names** in `@Document(collection = "...")` must match Mongoose auto-pluralized names: `markets`, `orders` (not `market`, `order`).
- **Embedded documents** — Market.foods is an array of `FoodItem` inner class, Order.foods is an array of `OrderFoodItem` inner class. Array manipulation (`$push`, `$pull`, `$set`, `$inc`) is done via `MongoTemplate`, not repository methods.
- **Lombok** used throughout: `@Data` on entities/DTOs, `@RequiredArgsConstructor` on services for constructor injection.
- **MapStruct** for DTO mapping (see `MarketMapper`, `OrderMapper`). Uses `mapstruct-processor` annotation processor.
- **File uploads** go to `static/images/market/` (configured via `app.upload.path`), served at `/static/**`. Image filenames use `System.currentTimeMillis() + extension`.
- **No authentication** by design (matches original Nest.js project).

## Profiles

- **dev** (default): Local MongoDB at `mongodb://127.0.0.1:27017/market`, Knife4j enabled
- **prod**: Remote MongoDB with credentials, Knife4j/OpenAPI docs disabled

## API Documentation

Knife4j (enhanced Swagger UI) available at: `http://localhost:9001/api/doc.html`

Grouped APIs:
- 市场管理 (Market): `/api/market/**`
- 订单管理 (Order): `/api/order/**`

The `SpringDocConfig.responseWrapperCustomizer` automatically wraps all 200 response schemas in the OpenAPI spec to match the runtime `{code, message, data}` format.

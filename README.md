# Vert.x User Proxy Service

Small **Spring Boot + Vert.x + Maven** service that calls the existing `user-service` **asynchronously** and adds:

- **Vert.x Web Client** for non-blocking upstream calls
- **Vert.x Circuit Breaker** for failure isolation
- **Exponential backoff with jitter** for retries
- **Header + query param pass-through**
- **Fallback 503 response** when the upstream service is unavailable

## Upstream contract used

This proxy is built against your existing repo:

- Repository: `https://github.com/prashanth609/user-service`
- Base path: `/api/v1/users`

The upstream README exposes:

- `POST /api/v1/users`
- `GET /api/v1/users`
- `GET /api/v1/users/{id}`
- `PUT /api/v1/users/{id}`
- `DELETE /api/v1/users/{id}`

## Ports

- **User Service**: `8080`
- **Vert.x Proxy Service**: `8081`
- **Spring Boot Actuator**: `8082`

## API exposed by this service

The proxy keeps the same CRUD shape but under a new prefix:

- `GET /api/v1/user-proxy`
- `GET /api/v1/user-proxy/{id}`
- `POST /api/v1/user-proxy`
- `PUT /api/v1/user-proxy/{id}`
- `DELETE /api/v1/user-proxy/{id}`
- `GET /api/v1/user-proxy/search?...`

Examples:

### Get all users
```bash
curl http://localhost:8081/api/v1/user-proxy
```

### Get one user
```bash
curl http://localhost:8081/api/v1/user-proxy/1
```

### Create user
```bash
curl -X POST http://localhost:8081/api/v1/user-proxy \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Ranjith",
    "email": "ranjith@example.com",
    "phone": "9876543210"
  }'
```

### Search
```bash
curl "http://localhost:8081/api/v1/user-proxy/search?name=ran&page=0&size=10&sort=createdAt,desc"
```

## How it works

`VertxHttpServer` receives the request and forwards it to `UserServiceProxyClient`.

`UserServiceProxyClient`:

1. Uses **Vert.x Web Client** for asynchronous HTTP requests
2. Wraps every upstream call with a **stateful Circuit Breaker**
3. Retries failed upstream calls with **exponential backoff + jitter**
4. Opens the circuit after repeated failures
5. Returns a **503 fallback** payload instead of hanging the client

## Config

`src/main/resources/application.yml`

```yaml
proxy:
  server:
    port: 8081
    prefix: /api/v1/user-proxy

user-service:
  base-url: http://localhost:8080
  request-timeout-ms: 3000
  connect-timeout-ms: 2000
  circuit-breaker:
    max-failures: 5
    timeout-ms: 3000
    reset-timeout-ms: 10000
    max-retries: 3
    backoff-initial-delay-ms: 200
    backoff-max-delay-ms: 2000
```

## Run

### 1) Start the upstream User Service
From your original repo:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 2) Start this proxy service
```bash
mvn spring-boot:run
```

### 3) Verify
```bash
curl http://localhost:8081/healthz
curl http://localhost:8082/actuator/health
curl http://localhost:8081/api/v1/user-proxy
```

## Notes

- **4xx** responses from `user-service` are passed through directly and **do not** trip the circuit breaker.
- **5xx**, transport failures, and timeouts are treated as failures.
- Important headers like `Authorization`, `Accept`, `Content-Type`, `ETag`, and cache headers are forwarded.
- If your upstream service protects endpoints with JWT, send the `Authorization` header to this proxy and it will forward it upstream.

## Suggested next step

If you want this to become production-ready, the next upgrade would be:

- structured request/response logging
- Micrometer metrics for breaker states and retry counts
- correlation ID propagation
- Dockerfile + `docker-compose.yml`
- Testcontainers integration test against MySQL-backed `user-service`

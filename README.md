# CRUD OpenTelemetry Application

A Spring Boot CRUD application (users) instrumented with OpenTelemetry. It demonstrates
manual tracing, application metrics, correlated logs, and the Java Agent's automatic
instrumentation working together.

## 1. Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker (optional, for the local observability backend in section 5)
- OpenTelemetry Java Agent jar (not committed to the repo, see below)

Download the agent once:

```bash
mkdir -p ~/otel
curl -L -o ~/otel/opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
```

## 2. Build and Run

### Build
```bash
mvn clean package
```

### Run without the agent (baseline, no telemetry is exported)
```bash
mvn spring-boot:run
```
The application runs on http://localhost:8080 and works exactly as before.

## 3. Run with the OpenTelemetry Java Agent

Start the observability backend first (Grafana, Tempo, Prometheus and Loki in one container):

```bash
docker run --rm -it -p 3000:3000 -p 4317:4317 -p 4318:4318 grafana/otel-lgtm
```

Then run the application with the agent:

```bash
java -javaagent:$HOME/otel/opentelemetry-javaagent.jar \
     -Dotel.service.name=crud-otel-application \
     -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
     -Dotel.exporter.otlp.protocol=http/protobuf \
     -Dotel.traces.exporter=otlp \
     -Dotel.metrics.exporter=otlp \
     -Dotel.logs.exporter=otlp \
     -Dotel.metric.export.interval=10000 \
     -jar target/observability-skeleton-0.0.1-SNAPSHOT.jar
```

In IntelliJ, put the same `-javaagent:...` and `-D...` options in
Run > Edit Configurations > Modify options > Add VM options.

### Console-only alternative (no backend)
Replace the three exporter options with the following to print telemetry to the console:

```
-Dotel.traces.exporter=logging -Dotel.metrics.exporter=logging -Dotel.logs.exporter=none
```

## 4. Required OpenTelemetry Configuration

| Setting | Value | Purpose |
|---|---|---|
| `-javaagent:<path>` | path to `opentelemetry-javaagent.jar` | Attaches the agent (automatic instrumentation) |
| `otel.service.name` | `crud-otel-application` | Service name on all telemetry |
| `otel.exporter.otlp.endpoint` | `http://localhost:4318` | OTLP endpoint (collector / backend) |
| `otel.exporter.otlp.protocol` | `http/protobuf` | OTLP transport |
| `otel.traces.exporter` | `otlp` (or `logging`) | Where spans go |
| `otel.metrics.exporter` | `otlp` (or `logging`) | Where metrics go |
| `otel.logs.exporter` | `otlp` (or `none`) | Where application logs go |
| `otel.metric.export.interval` | `10000` | Metric export interval in ms |

Log correlation is configured in `application.properties`:

```properties
logging.pattern.level=%5p [trace_id=%X{trace_id} span_id=%X{span_id}]
```

The agent places `trace_id` and `span_id` in the logging context while a span is active.

## 5. API Endpoints and Example Traffic

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/users` | Create a user. Body: `{"name":"...","email":"..."}`. 201, or 409 if the email exists. |
| GET | `/users/{id}` | Get a user. 200, or 404 if not found. |
| DELETE | `/users/{id}` | Delete a user. 204, or 404 if not found. |

```bash
# Create: success (201)
curl -i -X POST http://localhost:8080/users -H "Content-Type: application/json" \
  -d '{"name":"Eve","email":"eve@example.com"}'

# Get: success (200). Use the id returned by the create call.
curl -i http://localhost:8080/users/4

# Get: failure (404)
curl -i http://localhost:8080/users/999

# Create: failure (409, duplicate email)
curl -i -X POST http://localhost:8080/users -H "Content-Type: application/json" \
  -d '{"name":"Eve","email":"eve@example.com"}'

# Delete: success (204)
curl -i -X DELETE http://localhost:8080/users/4

# Delete: failure (404)
curl -i -X DELETE http://localhost:8080/users/999
```

## 6. Where to View Telemetry

Open Grafana at http://localhost:3000 (user `admin`, password `admin`) and go to **Explore**.

| Signal | Data source | Query |
|---|---|---|
| Traces | Tempo (TraceQL) | `{ resource.service.name = "crud-otel-application" && name = "delete-user" }`; failures: `{ resource.service.name = "crud-otel-application" && status = error }` |
| Metrics (count) | Prometheus | `sum by (operation, status) (users_operation_count_total)` |
| Metrics (duration) | Prometheus | `sum by (operation, status) (users_operation_duration_milliseconds_sum) / sum by (operation, status) (users_operation_duration_milliseconds_count)` |
| Logs | Loki | `{service_name="crud-otel-application"} \|= "operation="` |

Application logs in the console also show the trace context, for example:

```
WARN [trace_id=<trace id> span_id=<span id>] ... operation=delete user.id=999 status=failed error=UserNotFoundException
```

## 7. Manual vs Automatic Instrumentation

**Automatic (OpenTelemetry Java Agent)**
- Attached with `-javaagent`, with no source changes.
- Creates the HTTP server spans (`GET /users/{id}`, `POST /users`, `DELETE /users/{id}`).
- Propagates trace context and injects `trace_id` and `span_id` into the logging context.

**Manual (OpenTelemetry API in `UserService`)**
- `create-user` and `delete-user` spans with `operation.name` and `user.id` attributes.
- On failure: `recordException` and the span status is set to ERROR.
- Metrics: `users.operation.count` (counter) and `users.operation.duration` (histogram, ms), with `operation` (create, get, delete) and `status` (success, error). `user.id` is not used as a metric attribute, to avoid high cardinality.
- Logs: started, succeeded and failed messages with `operation` and `user.id`. Sensitive data (such as the email) is not logged.

**How they work together**
- `GET /users/{id}` produces only the automatic HTTP span (no manual HTTP spans are created).
- `DELETE /users/{id}` produces the automatic HTTP span with the manual `delete-user` span as its child, in the same trace:

```
HTTP DELETE /users/{id}   [automatic]
  └── delete-user         [manual]
```
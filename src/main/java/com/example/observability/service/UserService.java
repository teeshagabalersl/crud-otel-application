package com.example.observability.service;

import com.example.observability.dto.UserRequest;
import com.example.observability.dto.UserResponse;
import com.example.observability.entity.User;
import com.example.observability.exception.DuplicateEmailException;
import com.example.observability.exception.UserNotFoundException;
import com.example.observability.repository.UserRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Service layer that contains business logic for User operations.
 *
 * Manual instrumentation: create-user and delete-user spans, operation
 * count/duration metrics for create/get/delete, and structured logs.
 * Automatic instrumentation (Java Agent): HTTP server spans for all endpoints
 * and trace_id/span_id injection into the logging context.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String INSTRUMENTATION_NAME = "com.example.observability.service.UserService";

    // Span attribute keys
    private static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("operation.name");
    private static final AttributeKey<Long> USER_ID = AttributeKey.longKey("user.id");

    // Metric attribute keys (low cardinality only - never user.id)
    private static final AttributeKey<String> METRIC_OPERATION = AttributeKey.stringKey("operation");
    private static final AttributeKey<String> METRIC_STATUS = AttributeKey.stringKey("status");

    // OpenTelemetry API handles. With the Java Agent attached these are backed by
    // the agent's SDK; without it they are no-ops.
    private final Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
    private final Meter meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_NAME);

    private final LongCounter operationCount = meter
            .counterBuilder("users.operation.count")
            .setDescription("Number of user operations by operation type and status")
            .setUnit("1")
            .build();

    private final DoubleHistogram operationDuration = meter
            .histogramBuilder("users.operation.duration")
            .setDescription("Duration of user operations")
            .setUnit("ms")
            .build();

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Create a new user.
     *
     * @param request request containing name and email
     * @return created user response
     */
    @Transactional
    public UserResponse createUser(UserRequest request) {
        long start = System.nanoTime();
        String status = "error";
        Span span = tracer.spanBuilder("create-user").startSpan();
        Scope scope = span.makeCurrent();
        try {
            span.setAttribute(OPERATION_NAME, "create");
            log.info("operation=create status=started");

            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateEmailException("Email already exists: " + request.getEmail());
            }

            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());

            User saved = userRepository.save(user);
            span.setAttribute(USER_ID, saved.getId());
            status = "success";
            log.info("operation=create user.id={} status=succeeded", saved.getId());
            return toResponse(saved);
        } catch (RuntimeException e) {
            if (e instanceof DuplicateEmailException) {
                // The message contains the user's email, so record only the exception type.
                span.addEvent("exception", Attributes.of(
                        AttributeKey.stringKey("exception.type"), e.getClass().getName()));
            } else {
                span.recordException(e);
            }
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            // Log only the exception type: the message may contain the user's email.
            log.warn("operation=create status=failed error={}", e.getClass().getSimpleName());
            throw e;
        } finally {
            scope.close();
            span.end();
            recordMetrics("create", status, start);
        }
    }

    /**
     * Retrieve a user by id.
     *
     * Tracing: automatic instrumentation only (Java Agent HTTP span), no manual span.
     * Metrics and logs: recorded manually. Logs carry the HTTP span's trace/span ids.
     *
     * @param id user id
     * @return user response
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        long start = System.nanoTime();
        String status = "error";
        log.info("operation=get user.id={} status=started", id);
        try {
            User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
            status = "success";
            log.info("operation=get user.id={} status=succeeded", id);
            return toResponse(user);
        } catch (RuntimeException e) {
            log.warn("operation=get user.id={} status=failed error={}", id, e.getClass().getSimpleName());
            throw e;
        } finally {
            recordMetrics("get", status, start);
        }
    }

    /**
     * Delete a user by id.
     *
     * @param id user id
     */
    @Transactional
    public void deleteUser(Long id) {
        long start = System.nanoTime();
        String status = "error";
        Span span = tracer.spanBuilder("delete-user").startSpan();
        Scope scope = span.makeCurrent();
        try {
            span.setAttribute(OPERATION_NAME, "delete");
            span.setAttribute(USER_ID, id);
            log.info("operation=delete user.id={} status=started", id);

            User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
            userRepository.delete(user);
            status = "success";
            log.info("operation=delete user.id={} status=succeeded", id);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            log.warn("operation=delete user.id={} status=failed error={}", id, e.getClass().getSimpleName());
            throw e;
        } finally {
            scope.close();
            span.end();
            recordMetrics("delete", status, start);
        }
    }

    /**
     * Record the count and duration metrics for one operation.
     */
    private void recordMetrics(String operation, String status, long startNanos) {
        Attributes attributes = Attributes.of(METRIC_OPERATION, operation, METRIC_STATUS, status);
        double durationMs = (System.nanoTime() - startNanos) / (double) TimeUnit.MILLISECONDS.toNanos(1);
        operationCount.add(1, attributes);
        operationDuration.record(durationMs, attributes);
    }

    /**
     * Map entity to response DTO.
     */
    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}

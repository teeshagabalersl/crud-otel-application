package com.example.observability.service;

import com.example.observability.dto.UserRequest;
import com.example.observability.dto.UserResponse;
import com.example.observability.entity.User;
import com.example.observability.exception.DuplicateEmailException;
import com.example.observability.exception.UserNotFoundException;
import com.example.observability.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer that contains business logic for User operations.
 *
 * NOTE: This class includes TODO comments marking where candidates should add
 * manual observability instrumentation (spans, metrics, logs) as part of the
 * assignment. The skeleton intentionally does not contain any observability
 * libraries or runtime configuration.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Create a new user.
     *
     * This is the primary manual-instrumentation target for the assignment.
     * Candidates should add a manual span around this method when implementing
     * observability.
     *
     * @param request request containing name and email
     * @return created user response
     */
    @Transactional
    public UserResponse createUser(UserRequest request) {
        // TODO: Observability instrumentation point (candidate task)
        // - Add business-level spans/metrics/logs as appropriate for monitoring
        // - Capture meaningful attributes (e.g., user.id, operation.name)
        // - Record operation counts, durations, and failures
        // NOTE: This skeleton intentionally does not include observability libraries or agent configuration.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    /**
     * Retrieve a user by id.
     *
     * This endpoint is a primary target for automatic instrumentation verification —
     * candidates should run the application with their chosen instrumentation tooling
     * to verify that HTTP, controller and JDBC/framework-level spans are generated automatically.
     *
     * @param id user id
     * @return user response
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        // TODO: Auto-instrumentation verification target
        // - Configure OpenTelemetry Java Agent externally to verify automatic spans
        // - Do NOT create duplicate manual HTTP spans for this endpoint in the skeleton
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return toResponse(user);
    }

    /**
     * Delete a user by id.
     *
     * This operation is intended to demonstrate coexistence of automatic and
     * manual instrumentation: framework-level tooling may produce the top-level HTTP span
     * while the candidate can add a business-level span around the delete operation.
     *
     * @param id user id
     */
    @Transactional
    public void deleteUser(Long id) {
        // TODO: Observability instrumentation point (candidate task)
        // - Use a business-level span/metric around the delete operation if desired
        // - Record metrics and logs to capture operation success/failure and duration
        // NOTE: This skeleton intentionally does not include observability libraries or agent configuration.
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        userRepository.delete(user);
    }

    /**
     * Map entity to response DTO.
     */
    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}

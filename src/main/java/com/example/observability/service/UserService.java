package com.example.observability.service;

import com.example.observability.dto.UserRequest;
import com.example.observability.dto.UserResponse;
import com.example.observability.entity.User;
import com.example.observability.exception.DuplicateEmailException;
import com.example.observability.exception.UserNotFoundException;
import com.example.observability.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        // TODO: Manual instrumentation target (candidate task)
        // - Add a manual business-level span (suggested name: create-user)
        // - Add meaningful attributes (e.g., user.id, operation.name)
        // - Record metrics (operation count, duration, errors)
        // - Add logs for start/success/failure and record exceptions on spans
        // NOTE: Do NOT add OpenTelemetry SDK or agent configuration in this skeleton.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        // TODO: Auto-instrumentation verification target (candidate task)
        // - Configure OpenTelemetry Java Agent externally to verify automatic spans
        // - Do NOT create duplicate manual HTTP spans for this endpoint in the skeleton
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        // TODO: Combined manual + auto instrumentation target (candidate task)
        // - Auto instrumentation should produce an HTTP/server span
        // - Candidate should add a manual business-level span (suggested name: delete-user)
        // - Record metrics and logs; record exceptions on the manual span when needed
        // NOTE: Do NOT add OpenTelemetry SDK or agent configuration in this skeleton.
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        userRepository.delete(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}

package com.example.observability.repository;

import com.example.observability.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for User entities. Extends Spring Data JPA to provide
 * CRUD operations without boilerplate.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Check whether a user with the given email already exists.
     *
     * @param email email to check
     * @return true if a user exists with the email
     */
    boolean existsByEmail(String email);
}

package com.example.observability;

import com.example.observability.entity.User;
import com.example.observability.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Application entry point for the Observability skeleton application.
 * <p>
 * This class boots Spring Boot and seeds sample data into the in-memory H2 database
 * so the API can be exercised immediately after startup.
 */
@SpringBootApplication
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }

    /**
     * Initializes a small set of users on startup for manual testing.
     * Uses UserRepository directly for simplicity.
     *
     * @param userRepository repository to persist seed users
     * @return a CommandLineRunner that seeds data if repository is empty
     */
    @Bean
    public CommandLineRunner dataInitializer(UserRepository userRepository) {
        return args -> {
            // Seed data only when repository is empty to avoid duplicates on restart
            if (userRepository.count() == 0) {
                userRepository.save(new User(null, "John Doe", "john@example.com"));
                userRepository.save(new User(null, "Jane Doe", "jane@example.com"));
                userRepository.save(new User(null, "Bob Smith", "bob@example.com"));
            }
        };
    }
}

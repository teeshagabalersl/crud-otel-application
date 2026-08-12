package com.example.observability;

import com.example.observability.entity.User;
import com.example.observability.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }

    @Bean
    public CommandLineRunner dataInitializer(UserRepository userRepository) {
        return args -> {
            // Seed data: three users
            if (userRepository.count() == 0) {
                userRepository.save(new User(null, "John Doe", "john@example.com"));
                userRepository.save(new User(null, "Jane Doe", "jane@example.com"));
                userRepository.save(new User(null, "Bob Smith", "bob@example.com"));
            }
        };
    }
}

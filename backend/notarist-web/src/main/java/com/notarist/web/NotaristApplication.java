package com.notarist.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.notarist")
@EntityScan(basePackages = "com.notarist")
// Repository scanning does NOT follow @EntityScan: without this, Boot scans only the
// auto-configuration package (com.notarist.web), so every Spring Data interface in the
// sibling modules (com.notarist.auth, .kase, .review, .verification, …) is silently
// never created, and the first constructor that needs one fails the context.
@EnableJpaRepositories(basePackages = "com.notarist")
@EnableScheduling
public class NotaristApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotaristApplication.class, args);
    }
}

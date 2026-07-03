package com.notarist.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.notarist")
@EntityScan(basePackages = "com.notarist")
@EnableScheduling
public class NotaristApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotaristApplication.class, args);
    }
}

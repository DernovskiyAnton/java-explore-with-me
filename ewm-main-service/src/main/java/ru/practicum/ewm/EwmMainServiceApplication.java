package ru.practicum.ewm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"ru.practicum.ewm", "ru.practicum.client"})
public class EwmMainServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwmMainServiceApplication.class, args);
    }
}
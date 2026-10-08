package com.codeit.careeros;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerosBackendApplication.class, args);
    }
}
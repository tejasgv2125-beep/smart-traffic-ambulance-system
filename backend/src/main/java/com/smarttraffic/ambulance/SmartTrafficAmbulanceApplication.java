package com.smarttraffic.ambulance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartTrafficAmbulanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                SmartTrafficAmbulanceApplication.class,
                args
        );
    }
}
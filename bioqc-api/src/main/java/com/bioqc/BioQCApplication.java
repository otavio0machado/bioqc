package com.bioqc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BioQCApplication {

    public static void main(String[] args) {
        SpringApplication.run(BioQCApplication.class, args);
    }
}

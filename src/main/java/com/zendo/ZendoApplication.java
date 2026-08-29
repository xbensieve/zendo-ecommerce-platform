package com.zendo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZendoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZendoApplication.class, args);
    }

}

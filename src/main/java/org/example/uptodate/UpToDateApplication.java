package org.example.uptodate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class UpToDateApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpToDateApplication.class, args);
    }

}

package hu.diku.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DikuApplication {
    public static void main(String[] args) { SpringApplication.run(DikuApplication.class, args); }
}

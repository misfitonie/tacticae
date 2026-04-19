package gg.tacticae.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "gg.tacticae")
public class TacticaeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TacticaeApplication.class, args);
    }
}
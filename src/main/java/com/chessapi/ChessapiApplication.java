package com.chessapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class ChessapiApplication {

    public static void main(String[] args) {

        SpringApplication.run(ChessapiApplication.class, args);
        System.out.println("=== Chess API Started ===");
        System.out.println("Swagger UI: http://localhost:8081/swagger-ui.html");
        System.out.println("API Docs: http://localhost:8081/api-docs");
        System.out.println("Test endpoint: http://localhost:8081/api/games/test");
    }

}

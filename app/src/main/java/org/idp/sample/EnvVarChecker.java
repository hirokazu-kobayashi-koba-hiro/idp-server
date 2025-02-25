package org.idp.sample;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class EnvVarChecker implements CommandLineRunner {
    @Override
    public void run(String... args) {
        System.out.println("=== ENV ===");
        System.out.println(System.getenv("JDBC_DATABASE_URL"));
        System.out.println(System.getenv("DB_URL"));
    }
}

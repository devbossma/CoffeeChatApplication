package dev.saberlabs.coffeechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for "Not So Simple Chat" — the coffee shop chat application.
 *
 * <p>See {@code PRD.md} at the repository root for the full scope, the pattern-by-pattern
 * design mapping (section 7.2), and what's explicitly out of scope (section 4).
 */
@SpringBootApplication
public class CoffeeChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeChatApplication.class, args);
    }
}

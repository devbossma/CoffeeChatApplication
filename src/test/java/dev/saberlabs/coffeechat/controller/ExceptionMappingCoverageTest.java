package dev.saberlabs.coffeechat.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exception-to-status table: every domain exception class in the application must either have a
 * specific mapping in {@link RestExceptionHandler} or be listed here as INTERNAL (it can never reach a
 * controller). A new domain exception that is neither fails this test, instead of silently becoming a 500.
 */
@DisplayName("Every domain exception is mapped (or explicitly internal)")
class ExceptionMappingCoverageTest {

    private static final Path CLASSES = Path.of("target/classes/dev/saberlabs/coffeechat");
    private static final String BASE = "dev.saberlabs.coffeechat.";

    /** Caught and handled inside the application; never propagates to a controller. */
    private static final Set<String> INTERNAL = Set.of(
            "chat.ChatBaristaUnavailableException" // caught by ChatMatchmaker, which drops the barista from the queue
    );

    private static List<Class<?>> domainExceptions() throws Exception {
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CLASSES)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Exception.class")).toList()) {
                String relative = CLASSES.relativize(file).toString().replace('\\', '/').replace('/', '.');
                String name = relative.substring(0, relative.length() - ".class".length());
                found.add(Class.forName(BASE + name));
            }
        }
        return found;
    }

    private static Set<Class<?>> mappedTypes() {
        Set<Class<?>> mapped = new HashSet<>();
        for (var method : RestExceptionHandler.class.getDeclaredMethods()) {
            ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
            if (handler != null) {
                mapped.addAll(List.of(handler.value()));
            }
        }
        return mapped;
    }

    @Test
    @DisplayName("each domain exception is handled by a specific (non-catch-all) handler or is declared internal")
    void everyDomainExceptionIsMapped() throws Exception {
        List<Class<?>> exceptions = domainExceptions();
        assertTrue(exceptions.size() >= 15, "the scan must actually find the exception classes, found " + exceptions.size());

        Set<Class<?>> specific = mappedTypes();
        specific.remove(Exception.class);
        specific.remove(RuntimeException.class);

        List<String> unmapped = new ArrayList<>();
        for (Class<?> exception : exceptions) {
            String shortName = exception.getName().substring(BASE.length());
            boolean handled = specific.stream().anyMatch(type -> type.isAssignableFrom(exception));
            if (!handled && !INTERNAL.contains(shortName)) {
                unmapped.add(shortName);
            }
        }
        assertEquals(List.of(), unmapped, "these domain exceptions would fall through to the generic 500 handler");
    }

    @Test
    @DisplayName("the internal list contains only classes that exist, and none of them is also mapped")
    void internalListIsHonest() throws Exception {
        Set<Class<?>> specific = mappedTypes();
        for (String name : INTERNAL) {
            Class<?> type = Class.forName(BASE + name);
            assertFalse(specific.contains(type), name + " is both internal and mapped: remove it from the internal list");
        }
    }
}

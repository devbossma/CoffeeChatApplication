package dev.saberlabs.coffeechat.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Actor.SYSTEM} skips the role check, and it is a public constant, so "only trusted code uses
 * it" would otherwise be a mere convention. This fails the build if any class in the controller
 * package references it, so an endpoint can never accidentally act as the system. It scans both the
 * source and the compiled bytecode (a field reference to {@code Actor.SYSTEM} puts the class name
 * {@code facade/Actor} and the field name {@code SYSTEM} into the class file's constant pool).
 */
@DisplayName("Controllers never act as Actor.SYSTEM")
class ControllerActorGuardTest {

    private static final Path SOURCES = Path.of("src/main/java/dev/saberlabs/coffeechat/controller");
    private static final Path CLASSES = Path.of("target/classes/dev/saberlabs/coffeechat/controller");

    static boolean referencesSystemActorInSource(String source) {
        return source.contains("Actor.SYSTEM");
    }

    static boolean referencesSystemActorInBytecode(byte[] classFile) {
        String constants = new String(classFile, StandardCharsets.ISO_8859_1);
        return constants.contains("facade/Actor") && constants.contains("SYSTEM");
    }

    private static List<Path> filesIn(Path dir, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(suffix)).toList();
        }
    }

    @Test
    @DisplayName("no controller-package source references Actor.SYSTEM")
    void sourcesAreClean() throws IOException {
        List<Path> sources = filesIn(SOURCES, ".java");
        assertFalse(sources.isEmpty(), "the guard must actually be looking at the controller sources");
        for (Path source : sources) {
            assertFalse(referencesSystemActorInSource(Files.readString(source)), source + " references Actor.SYSTEM");
        }
    }

    @Test
    @DisplayName("no compiled controller class references Actor.SYSTEM")
    void bytecodeIsClean() throws IOException {
        List<Path> classes = filesIn(CLASSES, ".class");
        assertFalse(classes.isEmpty(), "the guard must actually be looking at the controller classes");
        for (Path classFile : classes) {
            assertFalse(referencesSystemActorInBytecode(Files.readAllBytes(classFile)), classFile + " references Actor.SYSTEM");
        }
    }

    @Test
    @DisplayName("the scanners themselves detect a reference (so the guard cannot pass vacuously)")
    void scannersDetectAReference() {
        assertTrue(referencesSystemActorInSource("var a = Actor.SYSTEM;"));
        assertFalse(referencesSystemActorInSource("var a = Actor.user(id);"));
        assertTrue(referencesSystemActorInBytecode(
                "xx dev/saberlabs/coffeechat/facade/Actor xx SYSTEM xx".getBytes(StandardCharsets.ISO_8859_1)));
        assertFalse(referencesSystemActorInBytecode(
                "xx dev/saberlabs/coffeechat/facade/Actor xx user xx".getBytes(StandardCharsets.ISO_8859_1)));
    }
}

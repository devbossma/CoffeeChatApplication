package dev.saberlabs.coffeechat.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the "the facade is the only door" rule at the controller boundary.
 *
 * <p>{@code Actor.SYSTEM} skips the role check and is a public constant, so "only trusted code uses it"
 * would otherwise be a convention. This fails the build if any class in the controller package
 * references that exact field, so an endpoint can never act as the system. Likewise no controller may
 * touch the command machinery ({@code OrderInvoker}, the commands) or the order/payment/staff services
 * directly: they call the facade. (A controller may still use {@code CustomerService}, which creates
 * customers and is not part of the order lifecycle.)
 *
 * <p>Both source and compiled bytecode are scanned. The bytecode match is exact: a reference to
 * {@code Actor.SYSTEM} puts the UTF-8 constant {@code SYSTEM} (length-prefixed, so
 * {@code SYSTEM_MESSAGE} does not match) and the class {@code facade/Actor} into the constant pool.
 */
@DisplayName("Controllers only talk to the facade")
class ControllerActorGuardTest {

    private static final Path SOURCES = Path.of("src/main/java/dev/saberlabs/coffeechat/controller");
    private static final Path CLASSES = Path.of("target/classes/dev/saberlabs/coffeechat/controller");

    private static final Pattern SYSTEM_ACTOR_IN_SOURCE = Pattern.compile("\\bActor\\s*\\.\\s*SYSTEM\\b");

    /**
     * Class references a controller must not carry: the invoker, any command, and the order/payment/staff
     * services. (Exceptions that merely live in the command package, such as UndoNotSupportedException,
     * are fine: a controller advice maps them.)
     */
    private static final Pattern FORBIDDEN_DEPENDENCY = Pattern.compile(
            "dev/saberlabs/coffeechat/command/(OrderInvoker|\\w*Command)\\b"
                    + "|dev/saberlabs/coffeechat/service/(OrderService|PaymentService|StaffAccess|StaffService)\\b");

    static boolean referencesSystemActorInSource(String source) {
        return SYSTEM_ACTOR_IN_SOURCE.matcher(source).find();
    }

    /** True when the class file has the exact UTF-8 constant "SYSTEM" and refers to facade/Actor. */
    static boolean referencesSystemActorInBytecode(byte[] classFile) {
        String constants = new String(classFile, StandardCharsets.ISO_8859_1);
        boolean exactSystemConstant = constants.contains("\u0001\u0000\u0006SYSTEM");
        return exactSystemConstant && constants.contains("facade/Actor");
    }

    static boolean referencesForbiddenDependency(byte[] classFile) {
        String constants = new String(classFile, StandardCharsets.ISO_8859_1);
        return FORBIDDEN_DEPENDENCY.matcher(constants).find();
    }

    private static byte[] utf8Constant(String value) {
        byte[] text = value.getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[3 + text.length];
        out[0] = 1;
        out[1] = (byte) (text.length >> 8);
        out[2] = (byte) text.length;
        System.arraycopy(text, 0, out, 3, text.length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
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
    @DisplayName("no compiled controller class touches the command machinery or the order/payment/staff services (the facade is the only door)")
    void controllersUseOnlyTheFacade() throws IOException {
        for (Path classFile : filesIn(CLASSES, ".class")) {
            assertFalse(referencesForbiddenDependency(Files.readAllBytes(classFile)),
                    classFile + " reaches past the facade");
        }
    }

    @Test
    @DisplayName("the scanners detect a real reference and ignore look-alikes, so the guard cannot pass vacuously or fail falsely")
    void scannersAreExact() {
        assertTrue(referencesSystemActorInSource("var a = Actor.SYSTEM;"));
        assertTrue(referencesSystemActorInSource("import static dev.saberlabs.coffeechat.facade.Actor.SYSTEM;"));
        assertFalse(referencesSystemActorInSource("var a = Actor.user(id); var m = MessageType.SYSTEM_MESSAGE;"));

        byte[] actorClass = utf8Constant("dev/saberlabs/coffeechat/facade/Actor");
        assertTrue(referencesSystemActorInBytecode(concat(actorClass, utf8Constant("SYSTEM"))));
        assertFalse(referencesSystemActorInBytecode(concat(actorClass, utf8Constant("SYSTEM_MESSAGE"))),
                "Actor.user(...) together with MessageType.SYSTEM_MESSAGE is legitimate");
        assertFalse(referencesSystemActorInBytecode(concat(actorClass, utf8Constant("user"))));

        assertTrue(referencesForbiddenDependency(utf8Constant("dev/saberlabs/coffeechat/command/OrderInvoker")));
        assertTrue(referencesForbiddenDependency(utf8Constant("dev/saberlabs/coffeechat/command/PayOrderCommand")));
        assertTrue(referencesForbiddenDependency(utf8Constant("dev/saberlabs/coffeechat/service/PaymentService")));
        assertFalse(referencesForbiddenDependency(utf8Constant("dev/saberlabs/coffeechat/service/CustomerService")));
        assertFalse(referencesForbiddenDependency(utf8Constant("dev/saberlabs/coffeechat/command/UndoNotSupportedException")));
    }
}

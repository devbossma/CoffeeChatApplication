package dev.saberlabs.coffeechat.web;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ActorArgumentResolver")
class ActorArgumentResolverTest {

    @SuppressWarnings("unused")
    static void handler(Actor actor, String other) {
    }

    private ActorArgumentResolver resolver;
    private MethodParameter actorParameter;
    private MethodParameter otherParameter;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new ActorArgumentResolver();
        var method = ActorArgumentResolverTest.class.getDeclaredMethod("handler", Actor.class, String.class);
        actorParameter = new MethodParameter(method, 0);
        otherParameter = new MethodParameter(method, 1);
    }

    private Object resolve(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(ActorArgumentResolver.HEADER, header);
        }
        return resolver.resolveArgument(actorParameter, null, new ServletWebRequest(request), null);
    }

    @Nested
    @DisplayName("supportsParameter()")
    class SupportsTests {

        @Test
        @DisplayName("supports exactly Actor parameters")
        void supports() {
            assertTrue(resolver.supportsParameter(actorParameter));
            assertFalse(resolver.supportsParameter(otherParameter));
        }
    }

    @Nested
    @DisplayName("resolveArgument()")
    class ResolveTests {

        @Test
        @DisplayName("a numeric header becomes that user's Actor, never the system actor")
        void valid() {
            Actor actor = (Actor) resolve("42");
            assertEquals(Long.valueOf(42), actor.userId());
            assertFalse(actor.isSystem());
        }

        @Test
        @DisplayName("surrounding whitespace is tolerated")
        void trimmed() {
            assertEquals(Long.valueOf(7), ((Actor) resolve("  7 ")).userId());
        }

        @Test
        @DisplayName("a missing or blank header is 401 (UnknownActorException)")
        void missingOrBlank() {
            assertThrows(UnknownActorException.class, () -> resolve(null));
            assertThrows(UnknownActorException.class, () -> resolve(""));
            assertThrows(UnknownActorException.class, () -> resolve("   "));
        }

        @Test
        @DisplayName("non-numeric, decimal, hex, zero, negative and out-of-range values are all 401")
        void invalid() {
            for (String bad : new String[]{"abc", "12x", "1.5", "0x10", "0", "-3", "99999999999999999999", "1 2"}) {
                assertThrows(UnknownActorException.class, () -> resolve(bad), bad);
            }
        }
    }
}

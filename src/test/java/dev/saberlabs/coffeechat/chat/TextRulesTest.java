package dev.saberlabs.coffeechat.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TextRules")
class TextRulesTest {

    private static final List<String> SPACES = List.of(" ", "\t", "\n", "\r", " ", " ", " ", " ", "　");

    @Nested
    @DisplayName("isBlank()")
    class IsBlankTests {

        @Test
        @DisplayName("is true for empty and for text made only of ASCII or Unicode spaces")
        void blank() {
            assertTrue(TextRules.isBlank(""));
            for (String space : SPACES) {
                assertTrue(TextRules.isBlank(space + space), "U+" + Integer.toHexString(space.charAt(0)));
            }
            assertTrue(TextRules.isBlank(String.join("", SPACES)));
        }

        @Test
        @DisplayName("is false as soon as there is a visible character")
        void notBlank() {
            assertFalse(TextRules.isBlank(" a "));
            assertFalse(TextRules.isBlank("."));
        }

        @Test
        @DisplayName("rejects null")
        void nullInput() {
            assertThrows(NullPointerException.class, () -> TextRules.isBlank(null));
        }
    }

    @Nested
    @DisplayName("strip()")
    class StripTests {

        @Test
        @DisplayName("removes leading and trailing Unicode spaces but keeps inner ones")
        void strips() {
            assertEquals("a b", TextRules.strip("   a b  "));
        }

        @Test
        @DisplayName("returns empty for all-space input and the same text when nothing to strip")
        void edges() {
            assertEquals("", TextRules.strip("   "));
            assertEquals("abc", TextRules.strip("abc"));
        }
    }

    @Nested
    @DisplayName("tokens()")
    class TokensTests {

        @Test
        @DisplayName("splits on runs of any space, mixed")
        void splits() {
            assertArrayEquals(new String[]{"a", "b", "c"}, TextRules.tokens("a  b \t c"));
        }

        @Test
        @DisplayName("no tokens for an empty string")
        void empty() {
            assertEquals(0, TextRules.tokens("").length);
        }
    }
}

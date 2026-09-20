package dev.saberlabs.coffeechat.chat;

import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * One definition of "whitespace" for everything a customer types. {@link Character#isWhitespace} (what
 * {@code String.strip()} uses) does not count the non-breaking spaces U+00A0, U+2007 and U+202F, which a
 * phone keyboard or a paste happily inserts; so those are added here. The database's own blank CHECK
 * only trims ASCII spaces, so this is the real gate and the CHECK the backstop.
 */
final class TextRules {

    private TextRules() {
    }

    static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == ' ' || c == ' ' || c == ' ' || Character.isSpaceChar(c);
    }

    /** True for the empty string and for a string made only of {@linkplain #isSpace spaces}. */
    static boolean isBlank(@NotNull String text) {
        Objects.requireNonNull(text, "text cannot be null");
        return strip(text).isEmpty();
    }

    /** Removes leading and trailing {@linkplain #isSpace spaces}. */
    static String strip(@NotNull String text) {
        Objects.requireNonNull(text, "text cannot be null");
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** Splits an already-stripped string on runs of {@linkplain #isSpace spaces}. */
    static String[] tokens(@NotNull String stripped) {
        Objects.requireNonNull(stripped, "stripped cannot be null");
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (isSpace(c)) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out.toArray(new String[0]);
    }
}

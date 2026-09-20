package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a chat message into an order request, or says why it is not one. Pure parsing: no Spring
 * state, no persistence, no session or customer knowledge.
 *
 * <p><b>The marker is explicit.</b> A message is an order command only when its first
 * whitespace-delimited token is exactly {@code /order} (case-insensitive). So the PRD's false-positive
 * sentence "order latte from this place was amazing" (no slash) is small talk, and so are
 * {@code /orders} and {@code /ordering}. Any run of whitespace (spaces, tabs, newlines, non-breaking
 * spaces, and the other Unicode spaces of {@link TextRules}) separates tokens and the input is trimmed first. Coffee and extras are case-insensitive;
 * duplicate extras are allowed and kept in the order typed (two milks cost twice one); the extras
 * aliases carried over from the reference are {@code whipped}, {@code whippedcream} and
 * {@code whipped_cream} for whipped cream.
 *
 * <p>Whether the shop is open or the coffee is on today's menu is not the parser's business: that is
 * {@code CoffeeShopFacade.placeOrder}'s, which is the only way an order is placed.
 */
@Component
public final class OrderCommandParser {

    /** The token that turns a message into an order command. */
    public static final String MARKER = "/order";


    private static final Map<String, ExtraType> EXTRAS = Map.of(
            "milk", ExtraType.MILK,
            "sugar", ExtraType.SUGAR,
            "whipped", ExtraType.WHIPPED_CREAM,
            "whippedcream", ExtraType.WHIPPED_CREAM,
            "whipped_cream", ExtraType.WHIPPED_CREAM);

    /** What parsing produced. */
    public sealed interface Result permits NotAnOrder, Parsed, MissingCoffeeType, UnknownCoffeeType, UnknownExtras {
    }

    /** Ordinary small talk: no {@code /order} marker. */
    public record NotAnOrder() implements Result {
    }

    /** A well-formed order command. {@code extras} keeps duplicates and the typed order. */
    public record Parsed(@NotNull CoffeeType coffee, @NotNull List<ExtraType> extras) implements Result {
        public Parsed {
            Objects.requireNonNull(coffee, "coffee cannot be null");
            extras = List.copyOf(Objects.requireNonNull(extras, "extras cannot be null"));
        }
    }

    /** {@code /order} with nothing after it. */
    public record MissingCoffeeType() implements Result {
    }

    /** The word after {@code /order} is not a coffee. {@code typed} is lower-cased. */
    public record UnknownCoffeeType(@NotNull String typed) implements Result {
    }

    /** One or more extras are not recognised: ALL of them are reported, lower-cased, in typed order. */
    public record UnknownExtras(@NotNull List<String> typed) implements Result {
        public UnknownExtras {
            typed = List.copyOf(Objects.requireNonNull(typed, "typed cannot be null"));
        }
    }

    /** The coffees a customer may name, for use in a reply message. */
    public List<String> availableCoffees() {
        return Arrays.stream(CoffeeType.values()).map(c -> c.name().toLowerCase(Locale.ROOT)).toList();
    }

    /** The extras a customer may name, for use in a reply message. */
    public List<String> availableExtras() {
        return List.of("milk", "sugar", "whipped");
    }

    /**
     * @param input a raw chat message
     * @throws NullPointerException if {@code input} is null
     */
    public Result parse(@NotNull String input) {
        Objects.requireNonNull(input, "input cannot be null");
        String trimmed = TextRules.strip(input);
        if (trimmed.isEmpty()) {
            return new NotAnOrder();
        }
        String[] tokens = TextRules.tokens(trimmed);
        if (!tokens[0].equalsIgnoreCase(MARKER)) {
            return new NotAnOrder();
        }
        if (tokens.length < 2) {
            return new MissingCoffeeType();
        }
        String coffeeWord = tokens[1].toLowerCase(Locale.ROOT);
        CoffeeType coffee = Arrays.stream(CoffeeType.values())
                .filter(c -> c.name().equalsIgnoreCase(coffeeWord))
                .findFirst()
                .orElse(null);
        if (coffee == null) {
            return new UnknownCoffeeType(coffeeWord);
        }
        List<ExtraType> extras = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 2; i < tokens.length; i++) {
            String word = tokens[i].toLowerCase(Locale.ROOT);
            ExtraType extra = EXTRAS.get(word);
            if (extra == null) {
                unknown.add(word);
            } else {
                extras.add(extra);
            }
        }
        if (!unknown.isEmpty()) {
            return new UnknownExtras(unknown);
        }
        return new Parsed(coffee, extras);
    }
}

package dev.saberlabs.coffeechat.decorator;

import dev.saberlabs.coffeechat.model.Coffee;
import dev.saberlabs.coffeechat.model.ExtraType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Applies a list of {@link ExtraType}s to a base {@link Coffee} by wrapping it in the matching
 * {@link CoffeeDecorator}s, in list order.
 *
 * <p>This is the one place extras get turned into decorators. {@code CoffeeShopFacade} calls it
 * while building an order; it is a plain utility (private constructor), not a bean &mdash; the
 * mapping from extra to wrapper is an {@link EnumMap}, so it stays {@code switch}-free like the
 * rest of the pattern wiring.
 */
public final class CoffeeDecorators {

    private static final Map<ExtraType, UnaryOperator<Coffee>> WRAPPERS = new EnumMap<>(ExtraType.class);

    static {
        WRAPPERS.put(ExtraType.MILK, MilkDecorator::new);
        WRAPPERS.put(ExtraType.SUGAR, SugarDecorator::new);
        WRAPPERS.put(ExtraType.WHIPPED_CREAM, WhippedCreamDecorator::new);
    }

    private CoffeeDecorators() {
    }

    /**
     * @param base   the undecorated coffee from {@code CoffeeFactory}
     * @param extras extras to apply, in order; may be empty
     * @return {@code base} if {@code extras} is empty, otherwise {@code base} wrapped in one
     *         decorator per extra
     * @throws NullPointerException     if {@code base}, {@code extras}, or any element is null
     * @throws IllegalArgumentException if an extra has no registered decorator
     */
    public static Coffee decorate(Coffee base, List<ExtraType> extras) {
        Objects.requireNonNull(base, "base coffee cannot be null");
        Objects.requireNonNull(extras, "extras cannot be null");
        Coffee result = base;
        for (ExtraType extra : extras) {
            Objects.requireNonNull(extra, "extras cannot contain null");
            UnaryOperator<Coffee> wrapper = WRAPPERS.get(extra);
            if (wrapper == null) {
                throw new IllegalArgumentException("No decorator registered for extra: " + extra);
            }
            result = wrapper.apply(result);
        }
        return result;
    }
}

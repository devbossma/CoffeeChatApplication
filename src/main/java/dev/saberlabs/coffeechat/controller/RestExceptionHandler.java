package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.CoffeeNotOnMenuException;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.facade.ShopClosedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the domain exceptions {@code CoffeeShopFacade} throws (and bean-validation failures) onto
 * HTTP status codes, so controllers can stay free of {@code try/catch}.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler({ShopClosedException.class, CoffeeNotOnMenuException.class})
    public ProblemDetail onConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({CustomerNotFoundException.class, OrderNotFoundException.class})
    public ProblemDetail onNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationError(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .findFirst()
                .orElse("request validation failed");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}

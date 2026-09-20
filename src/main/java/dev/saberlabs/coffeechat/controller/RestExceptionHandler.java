package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.command.UndoNotSupportedException;
import dev.saberlabs.coffeechat.chat.ChatSessionAlreadyOpenException;
import dev.saberlabs.coffeechat.chat.ChatSessionNotFoundException;
import dev.saberlabs.coffeechat.chat.InvalidChatMessageException;
import dev.saberlabs.coffeechat.chat.NotChatParticipantException;
import dev.saberlabs.coffeechat.chat.SessionNotActiveException;
import dev.saberlabs.coffeechat.facade.CoffeeNotOnMenuException;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderStateConflictException;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.ShopClosedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.IllegalOrderTransitionException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps the domain exceptions {@code CoffeeShopFacade} throws (and bean-validation failures) onto
 * HTTP status codes, so controllers can stay free of {@code try/catch}.
 */
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler({ShopClosedException.class, CoffeeNotOnMenuException.class, OrderStateConflictException.class,
            IllegalOrderTransitionException.class})
    public ProblemDetail onConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** The caller could not be identified (no actor, or an unknown user id). */
    @ExceptionHandler(UnknownActorException.class)
    public ResponseEntity<ProblemDetail> onUnknownActor(UnknownActorException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "X-User-Id")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    /** A known user whose role does not permit the action. */
    @ExceptionHandler(RoleNotAllowedException.class)
    public ProblemDetail onRoleNotAllowed(RoleNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UndoNotSupportedException.class)
    public ProblemDetail onUndoNotSupported(UndoNotSupportedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({CustomerNotFoundException.class, OrderNotFoundException.class})
    public ProblemDetail onNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** A concurrent writer changed the order first; the client should re-read and decide again. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail onConcurrentUpdate(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The order was modified concurrently; re-read it and try again");
    }

    /** The customer already has an open chat; the body carries that session's id so the client can go back to it. */
    @ExceptionHandler(ChatSessionAlreadyOpenException.class)
    public ProblemDetail onChatAlreadyOpen(ChatSessionAlreadyOpenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("existingSessionId", ex.existingSessionId());
        return problem;
    }

    @ExceptionHandler(SessionNotActiveException.class)
    public ProblemDetail onSessionNotActive(SessionNotActiveException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(NotChatParticipantException.class)
    public ProblemDetail onNotParticipant(NotChatParticipantException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ProblemDetail onChatNotFound(ChatSessionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Deliberately the only "bad input" domain mapping: a blanket {@code IllegalArgumentException -> 400} would
     * turn programming errors from anywhere into a 400 carrying an internal message. Request bodies are
     * validated by bean validation ({@code MethodArgumentNotValidException} below).
     */
    @ExceptionHandler(InvalidChatMessageException.class)
    public ProblemDetail onInvalidChatMessage(InvalidChatMessageException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Request-body validation. Overrides the base class so the body names the first offending field. (All the
     * other framework failures, such as malformed JSON, a missing body, an unknown enum value or a
     * wrong-typed path variable, are answered as 400/405/415 {@code ProblemDetail}s by
     * {@link ResponseEntityExceptionHandler} itself.)
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .findFirst()
                .orElse("request validation failed");
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    /** Anything unforeseen: a ProblemDetail like every other error, with no internal message leaked. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unhandled exception in a request", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}

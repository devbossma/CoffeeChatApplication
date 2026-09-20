package dev.saberlabs.coffeechat.web;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Turns the {@code X-User-Id} request header into the {@link Actor} a controller method declares as a
 * parameter.
 *
 * <p><b>This is a demo identity claim, not authentication</b> (PRD section 4: no auth system). Anyone can
 * send any id. What it gives the rest of the application is one place where "who is calling" is decided,
 * so replacing it with real authentication (a verified token or session) later means changing this class
 * and nothing else: the facade and services already enforce roles on the {@code Actor} they are given.
 *
 * <p>It only ever builds {@link Actor#user(Long)}, never the trusted {@code SYSTEM} actor, so no HTTP request
 * can act as the system; {@code ControllerActorGuardTest} keeps that true. A missing, blank, non-numeric,
 * non-positive or out-of-range header is {@link UnknownActorException} (401), the same as an id that names
 * no user.
 */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String raw = webRequest.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            throw new UnknownActorException("The " + HEADER + " header is required");
        }
        long id;
        try {
            id = Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            throw new UnknownActorException("The " + HEADER + " header must be a numeric user id");
        }
        if (id <= 0) {
            throw new UnknownActorException("The " + HEADER + " header must be a positive user id");
        }
        return Actor.user(id);
    }
}

package dev.saberlabs.coffeechat.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Registers the {@link ActorArgumentResolver}. A {@code WebMvcConfigurer}, so {@code @WebMvcTest} slices pick it up too. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver());
    }
}

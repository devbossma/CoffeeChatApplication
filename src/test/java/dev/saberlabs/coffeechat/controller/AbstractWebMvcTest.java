package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.chat.ChatService;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One shared web slice for every controller test. Spring's test-context cache keys on the slice's controllers
 * and its mock beans, so a {@code @WebMvcTest(XController.class)} per controller would build one context per
 * controller. Loading all controllers with the union of their collaborators mocked here gives every controller
 * test the same cache key: ONE web context however many controllers there are. (Mocks are reset after each test.)
 */
@WebMvcTest
abstract class AbstractWebMvcTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CoffeeShopFacade facade;

    @MockitoBean
    ChatService chat;

    @MockitoBean
    CoffeeShop coffeeShop;

    @MockitoBean
    CustomerService customers;
}

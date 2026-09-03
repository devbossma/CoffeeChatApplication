/**
 * REST controllers — the application's only entry point; there is no UI in this project.
 * Thin by design: a controller validates the request and calls straight into
 * {@code facade}/{@code service}, it doesn't hold business logic itself.
 * See {@code PRD.md} section 9.4 for the planned endpoint surface.
 */
package dev.saberlabs.coffeechat.controller;

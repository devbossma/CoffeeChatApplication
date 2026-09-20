package dev.saberlabs.coffeechat.chat;

import org.jetbrains.annotations.Nullable;

/** A session together with the display names of its participants; the barista is null while WAITING. */
public record SessionDetail(SessionView session, String customerName, @Nullable String baristaName) {
}

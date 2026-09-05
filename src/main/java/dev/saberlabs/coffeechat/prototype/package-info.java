/**
 * Pattern: PROTOTYPE.
 *
 * <p>Uses Spring's actual prototype bean scope ({@code @Scope("prototype")}) to hand out a
 * fresh, independent order to customize when a customer reorders — the one pattern in this
 * package list that only really makes sense once Spring is involved.
 */
package dev.saberlabs.coffeechat.prototype;

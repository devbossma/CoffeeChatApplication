package dev.saberlabs.coffeechat.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Logs a match once it has committed (never for one that rolled back). */
@Component
public class ChatMatchedListener {

    private static final Logger log = LoggerFactory.getLogger(ChatMatchedListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatched(ChatMatchedEvent event) {
        log.info("Chat session {} matched: customer {} with barista {}",
                event.sessionId(), event.customerId(), event.baristaId());
    }
}

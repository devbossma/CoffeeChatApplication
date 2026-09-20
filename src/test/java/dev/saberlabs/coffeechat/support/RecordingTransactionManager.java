package dev.saberlabs.coffeechat.support;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@code PlatformTransactionManager} that touches no database: it counts begin/commit/rollback
 * and can be told to fail the commit, which is how a unit test reproduces "the transaction failed
 * at commit" (an optimistic-lock conflict surfaces exactly there) without a real one.
 */
public class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    public final AtomicInteger begun = new AtomicInteger();
    public final AtomicInteger committed = new AtomicInteger();
    public final AtomicInteger rolledBack = new AtomicInteger();
    private volatile RuntimeException commitFailure;

    /** The next (and every following) commit throws {@code failure}; pass {@code null} to stop. */
    public void failCommitsWith(RuntimeException failure) {
        this.commitFailure = failure;
    }

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        begun.incrementAndGet();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        if (commitFailure != null) {
            throw commitFailure;
        }
        committed.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        rolledBack.incrementAndGet();
    }
}
